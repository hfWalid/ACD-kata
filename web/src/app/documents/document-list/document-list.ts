import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AgGridAngular } from 'ag-grid-angular';
import {
  AllCommunityModule,
  ColDef,
  GridApi,
  GridOptions,
  GridReadyEvent,
  ModuleRegistry,
  RowClickedEvent,
  themeQuartz,
  ValueFormatterParams,
} from 'ag-grid-community';
import { ApiFailure, DocumentApi } from '../../core/document-api';
import { DOCUMENT_STATUSES, DocumentStatus, DocumentSummary } from '../../core/document.models';
import { createDocumentDatasource } from '../document-datasource';
import { DEFAULT_SORT } from '../document-grid-query';
import { STATUS_LABELS, STATUS_TONES } from '../document-status';

// Registered in this lazily loaded chunk so the initial bundle stays small.
ModuleRegistry.registerModules([AllCommunityModule]);

const BLOCK_SIZE = 25;

@Component({
  selector: 'app-document-list',
  imports: [AgGridAngular, FormsModule, RouterLink],
  templateUrl: './document-list.html',
})
export class DocumentList {
  private readonly api = inject(DocumentApi);
  private readonly router = inject(Router);

  readonly statuses = DOCUMENT_STATUSES;
  readonly statusLabels = STATUS_LABELS;
  readonly defaultSort = DEFAULT_SORT;
  readonly blockSize = BLOCK_SIZE;

  readonly statusFilter = signal<DocumentStatus | ''>('');
  readonly search = signal('');
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly failure = signal<ApiFailure | null>(null);

  readonly empty = computed(() => !this.loading() && !this.failure() && this.total() === 0);
  readonly summary = computed(() =>
    this.failure() ? '' : `${this.total()} document(s), chargés par blocs de ${this.blockSize}.`,
  );

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

  readonly gridOptions: GridOptions<DocumentSummary> = {
    theme: themeQuartz,
    rowModelType: 'infinite',
    columnDefs: this.columnDefs,
    cacheBlockSize: BLOCK_SIZE,
    cacheOverflowSize: 1,
    maxConcurrentDatasourceRequests: 2,
    infiniteInitialRowCount: BLOCK_SIZE,
    defaultColDef: { sortable: true, resizable: true },
    overlayNoRowsTemplate: '<span class="text-slate-600">Aucun document pour ces filtres.</span>',
    localeText: { noRowsToShow: 'Aucun document' },
    datasource: createDocumentDatasource(
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
    ),
  };

  private gridApi?: GridApi<DocumentSummary>;

  onGridReady(event: GridReadyEvent<DocumentSummary>): void {
    this.gridApi = event.api;
  }

  /**
   * Sorting is handled by AG Grid, which re-reads the blocks on its own. Only the toolbar
   * filters change outside the grid, so they must purge the cache explicitly.
   */
  refresh(): void {
    this.failure.set(null);
    this.loading.set(true);
    this.gridApi?.purgeInfiniteCache();
  }

  onStatusChange(value: string): void {
    this.statusFilter.set(value as DocumentStatus | '');
    this.refresh();
  }

  onSearch(value: string): void {
    this.search.set(value);
    this.refresh();
  }

  open(event: RowClickedEvent<DocumentSummary>): void {
    if (event.data) {
      void this.router.navigate(['/documents', event.data.id]);
    }
  }
}
