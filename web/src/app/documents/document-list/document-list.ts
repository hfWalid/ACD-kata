import { Component, computed, inject, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AgGridAngular } from 'ag-grid-angular';
import {
  AllCommunityModule,
  ColDef,
  GridApi,
  GridOptions,
  GridReadyEvent,
  IDatasource,
  ModuleRegistry,
  RowClickedEvent,
  themeQuartz,
  ValueFormatterParams,
} from 'ag-grid-community';
import { ApiFailure, DocumentApi } from '../../core/document-api';
import { DOCUMENT_STATUSES, DocumentStatus, DocumentSummary } from '../../core/document.models';
import { createDocumentDatasource } from '../document-datasource';
import { STATUS_LABELS, STATUS_TONES } from '../document-status';

// Registered in this lazily loaded chunk so the initial bundle stays small.
ModuleRegistry.registerModules([AllCommunityModule]);

const PAGE_SIZE = 25;
const PAGE_SIZES = [25, 50, 100];

/** Delay before a typed search term is applied to the server query. */
export const SEARCH_DEBOUNCE_MS = 300;

@Component({
  selector: 'app-document-list',
  imports: [AgGridAngular, FormsModule, RouterLink],
  templateUrl: './document-list.html',
})
export class DocumentList implements OnDestroy {
  private readonly api = inject(DocumentApi);
  private readonly router = inject(Router);

  readonly statuses = DOCUMENT_STATUSES;
  readonly statusLabels = STATUS_LABELS;
  readonly pageSize = PAGE_SIZE;

  readonly statusFilter = signal<DocumentStatus | ''>('');
  /** Raw input value, updated on every keystroke. */
  readonly searchInput = signal('');
  /** Term actually sent to the server, applied after the debounce delay. */
  readonly search = signal('');
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly failure = signal<ApiFailure | null>(null);

  readonly hasFilters = computed(() => this.statusFilter() !== '' || this.search() !== '');
  readonly empty = computed(() => !this.loading() && !this.failure() && this.total() === 0);
  readonly summary = computed(() => {
    if (this.failure()) {
      return '';
    }
    const documents = `${this.total()} document(s)`;
    return this.hasFilters() ? `${documents} pour ces filtres` : documents;
  });

  readonly columnDefs: ColDef<DocumentSummary>[] = [
    { field: 'title', headerName: 'Titre', flex: 2, minWidth: 220 },
    {
      field: 'status',
      headerName: 'Statut',
      flex: 1,
      minWidth: 160,
      valueFormatter: (params: ValueFormatterParams<DocumentSummary, DocumentStatus>) =>
        params.value ? STATUS_LABELS[params.value] : '',
      cellClass: (params) =>
        params.value ? `font-medium ${STATUS_TONES[params.value as DocumentStatus]}` : '',
    },
    { field: 'version', headerName: 'Version', width: 110, type: 'numericColumn' },
    {
      field: 'updatedAt',
      headerName: 'Mis à jour le',
      flex: 1,
      minWidth: 200,
      valueFormatter: (params: ValueFormatterParams<DocumentSummary, string>) =>
        params.value ? new Date(params.value).toLocaleString('fr-FR') : '',
    },
  ];

  private readonly datasource: IDatasource = createDocumentDatasource(
    this.api,
    () => ({ status: this.statusFilter() || undefined, q: this.search() }),
    {
      loaded: (page) => {
        this.total.set(page.rowCount);
        this.loading.set(false);
      },
      failed: (failure) => {
        this.failure.set(failure);
        this.loading.set(false);
      },
    },
  );

  readonly gridOptions: GridOptions<DocumentSummary> = {
    theme: themeQuartz,
    rowModelType: 'infinite',
    columnDefs: this.columnDefs,
    cacheBlockSize: PAGE_SIZE,
    pagination: true,
    paginationPageSize: PAGE_SIZE,
    paginationPageSizeSelector: PAGE_SIZES,
    cacheOverflowSize: 1,
    maxConcurrentDatasourceRequests: 2,
    infiniteInitialRowCount: PAGE_SIZE,
    defaultColDef: { sortable: true, resizable: true },
    overlayNoRowsTemplate: '<span class="text-slate-600">Aucun document pour ces filtres.</span>',
    localeText: {
      noRowsToShow: 'Aucun document',
      page: 'Page',
      of: 'sur',
      to: 'à',
      firstPage: 'Première page',
      previousPage: 'Page précédente',
      nextPage: 'Page suivante',
      lastPage: 'Dernière page',
      pageSizeSelectorLabel: 'Lignes par page :',
      ariaPageSizeSelectorLabel: 'Lignes par page',
      thousandSeparator: ' ',
    },
    datasource: this.datasource,
  };

  private gridApi?: GridApi<DocumentSummary>;
  private searchTimer?: ReturnType<typeof setTimeout>;

  onGridReady(event: GridReadyEvent<DocumentSummary>): void {
    this.gridApi = event.api;
  }

  /** The status filter applies immediately: a select does not produce keystrokes. */
  onStatusChange(value: string): void {
    this.statusFilter.set(value as DocumentStatus | '');
    this.reload();
  }

  /** Typing is debounced so the server is queried for the term being written, not each letter. */
  onSearchInput(value: string): void {
    this.searchInput.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.applySearch(value), SEARCH_DEBOUNCE_MS);
  }

  clearFilters(): void {
    clearTimeout(this.searchTimer);
    this.statusFilter.set('');
    this.searchInput.set('');
    this.search.set('');
    this.reload();
  }

  open(event: RowClickedEvent<DocumentSummary>): void {
    if (event.data) {
      void this.router.navigate(['/documents', event.data.id]);
    }
  }

  ngOnDestroy(): void {
    clearTimeout(this.searchTimer);
  }

  private applySearch(value: string): void {
    const term = value.trim();
    if (term === this.search()) {
      return;
    }
    this.search.set(term);
    this.reload();
  }

  /**
   * A new query restarts the row model: back to page one, no stale block, a single load.
   * The datasource reads the filters lazily, so re-setting the same instance is enough.
   */
  private reload(): void {
    this.failure.set(null);
    this.loading.set(true);
    this.gridApi?.setGridOption('datasource', this.datasource);
  }
}
