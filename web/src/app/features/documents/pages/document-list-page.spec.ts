import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import type { IGetRowsParams, SortModelItem } from 'ag-grid-community';
import { vi } from 'vitest';
import { DocumentSummary } from '../../../core/models/document.model';
import { DocumentListPage, SEARCH_DEBOUNCE_MS } from './document-list-page';

const ROW: DocumentSummary = {
  id: 'doc-1',
  title: 'Notice',
  status: 'InReview',
  version: 3,
  updatedAt: '2026-11-17T10:00:00.000Z',
};

function rowsParams(startRow: number, endRow: number, sortModel: SortModelItem[] = []) {
  const successCallback = vi.fn();
  const failCallback = vi.fn();
  const params = {
    startRow,
    endRow,
    sortModel,
    successCallback,
    failCallback,
  } as unknown as IGetRowsParams;
  return { params, successCallback, failCallback };
}

/**
 * The AG Grid view needs a real layout, so this spec drives the component class and its
 * datasource directly: the window mapping, the debounce, the states and the query
 * invalidation are the behaviour S6 must prove, and the template only binds them.
 */
describe('DocumentListPage', () => {
  let component: DocumentListPage;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    component = TestBed.runInInjectionContext(() => new DocumentListPage());
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('loads blocks of 25 rows and paginates on the server row count', () => {
    const { params, successCallback } = rowsParams(0, 25);
    expect(component.loading()).toBe(true);
    expect(component.gridOptions.rowModelType).toBe('infinite');
    expect(component.gridOptions.pagination).toBe(true);
    expect(component.gridOptions.paginationPageSize).toBe(25);
    expect(component.gridOptions.paginationPageSizeSelector).toEqual([25, 50, 100]);
    expect(component.gridOptions.cacheBlockSize).toBe(25);

    component.gridOptions.datasource?.getRows(params);
    const request = http.expectOne((candidate) => candidate.url === '/api/documents');
    expect(request.request.params.get('startRow')).toBe('0');
    expect(request.request.params.get('endRow')).toBe('25');
    expect(request.request.params.get('sort')).toBe('updatedAt:desc');
    request.flush({ rowCount: 42, rows: [ROW] });

    expect(successCallback).toHaveBeenCalledWith([ROW], 42);
    expect(component.total()).toBe(42);
    expect(component.summary()).toBe('42 document(s)');
    expect(component.loading()).toBe(false);
    expect(component.failure()).toBeNull();
    expect(component.empty()).toBe(false);
  });

  it('sends the grid sort model and the toolbar filters to the same window', () => {
    component.onStatusChange('InReview');
    vi.useFakeTimers();
    component.onSearchInput(' contrat ');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS);

    const { params } = rowsParams(25, 50, [{ colId: 'title', sort: 'asc' }]);
    component.gridOptions.datasource?.getRows(params);
    const request = http.expectOne((candidate) => candidate.url === '/api/documents');

    expect(request.request.params.get('startRow')).toBe('25');
    expect(request.request.params.get('endRow')).toBe('50');
    expect(request.request.params.get('sort')).toBe('title:asc');
    expect(request.request.params.get('filter.status')).toBe('InReview');
    expect(request.request.params.get('q')).toBe('contrat');
    request.flush({ rowCount: 0, rows: [] });

    expect(component.total()).toBe(0);
    expect(component.summary()).toBe('0 document(s) pour ces filtres');
    expect(component.empty()).toBe(true);
  });

  it('applies only the last typed term, once, after the debounce delay', () => {
    vi.useFakeTimers();

    component.onSearchInput('c');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS - 100);
    component.onSearchInput('co');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS - 100);
    component.onSearchInput('contrat');

    // The raw value follows the keystrokes, the query does not yet.
    expect(component.searchInput()).toBe('contrat');
    expect(component.search()).toBe('');

    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS - 1);
    expect(component.search()).toBe('');

    vi.advanceTimersByTime(1);
    expect(component.search()).toBe('contrat');

    const { params } = rowsParams(0, 25);
    component.gridOptions.datasource?.getRows(params);
    http
      .expectOne((candidate) => candidate.url === '/api/documents')
      .flush({ rowCount: 1, rows: [ROW] });
  });

  it('does not query again when the trimmed term is unchanged', () => {
    const setGridOption = vi.fn();
    component.onGridReady({ api: { setGridOption } } as never);
    vi.useFakeTimers();

    component.onSearchInput('contrat');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS);
    expect(setGridOption).toHaveBeenCalledTimes(1);

    component.onSearchInput(' contrat ');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS);
    expect(setGridOption).toHaveBeenCalledTimes(1);
  });

  it('restarts the row model, on page one, when a filter changes', () => {
    const setGridOption = vi.fn();
    component.onGridReady({ api: { setGridOption } } as never);

    component.onStatusChange('Draft');

    expect(setGridOption).toHaveBeenCalledTimes(1);
    expect(setGridOption).toHaveBeenCalledWith('datasource', component.gridOptions.datasource);
    expect(component.loading()).toBe(true);
    expect(component.hasFilters()).toBe(true);
  });

  it('clears both filters with a single reload', () => {
    const setGridOption = vi.fn();
    component.onGridReady({ api: { setGridOption } } as never);
    component.onStatusChange('Draft');
    vi.useFakeTimers();
    component.onSearchInput('note');
    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS);
    setGridOption.mockClear();

    component.clearFilters();
    // The pending timer must not fire a second reload after the reset.
    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS);

    expect(setGridOption).toHaveBeenCalledTimes(1);
    expect(component.statusFilter()).toBe('');
    expect(component.searchInput()).toBe('');
    expect(component.search()).toBe('');
    expect(component.hasFilters()).toBe(false);
  });

  it('reports a failing block and asks the grid to show the error overlay', () => {
    const { params, failCallback, successCallback } = rowsParams(0, 25);
    component.gridOptions.datasource?.getRows(params);
    http
      .expectOne((candidate) => candidate.url === '/api/documents')
      .flush(
        { code: 'INTERNAL_ERROR', detail: 'The request failed' },
        {
          status: 500,
          statusText: 'Server Error',
        },
      );

    expect(failCallback).toHaveBeenCalled();
    expect(successCallback).not.toHaveBeenCalled();
    expect(component.failure()?.code).toBe('INTERNAL_ERROR');
    expect(component.loading()).toBe(false);
    expect(component.empty()).toBe(false);
  });
});
