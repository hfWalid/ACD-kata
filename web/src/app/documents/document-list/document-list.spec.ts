import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import type { IGetRowsParams, SortModelItem } from 'ag-grid-community';
import { vi } from 'vitest';
import { DocumentSummary } from '../../core/document.models';
import { DocumentList } from './document-list';

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
 * datasource directly: the window mapping, the states and the cache invalidation are the
 * behaviour S6 must prove, and the template only binds them.
 */
describe('DocumentList', () => {
  let component: DocumentList;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    component = TestBed.runInInjectionContext(() => new DocumentList());
  });

  afterEach(() => http.verify());

  it('loads the first block and exposes the filtered total', () => {
    const { params, successCallback } = rowsParams(0, 25);
    expect(component.loading()).toBe(true);

    component.gridOptions.datasource?.getRows(params);
    const request = http.expectOne((candidate) => candidate.url === '/api/documents');
    expect(request.request.params.get('startRow')).toBe('0');
    expect(request.request.params.get('endRow')).toBe('25');
    expect(request.request.params.get('sort')).toBe('updatedAt:desc');
    request.flush({ rowCount: 42, rows: [ROW] });

    expect(successCallback).toHaveBeenCalledWith([ROW], 42);
    expect(component.total()).toBe(42);
    expect(component.loading()).toBe(false);
    expect(component.failure()).toBeNull();
    expect(component.empty()).toBe(false);
  });

  it('sends the grid sort model and the toolbar filters to the same window', () => {
    component.onStatusChange('InReview');
    component.onSearch(' contrat ');

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
    expect(component.empty()).toBe(true);
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

  it('purges the block cache when a toolbar filter changes', () => {
    const purgeInfiniteCache = vi.fn();
    component.onGridReady({ api: { purgeInfiniteCache } } as never);

    component.onStatusChange('Draft');
    component.onSearch('note');

    expect(purgeInfiniteCache).toHaveBeenCalledTimes(2);
  });
});
