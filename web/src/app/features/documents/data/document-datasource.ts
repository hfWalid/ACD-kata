import type { IDatasource, IGetRowsParams } from 'ag-grid-community';
import { ApiFailure, toApiFailure } from '../../../core/api/api-failure';
import { DocumentApi } from '../../../core/api/document-api';
import { DocumentPage } from '../../../core/models/document.model';
import { GridFilters, toQuery } from './document-grid-query';

export interface DatasourceListener {
  loaded(page: DocumentPage): void;
  failed(failure: ApiFailure): void;
}

/** `GET /api/documents` window reader: one request per block, no global load. */
export function createDocumentDatasource(
  api: Pick<DocumentApi, 'list'>,
  filters: () => GridFilters,
  listener?: DatasourceListener,
): IDatasource {
  return {
    getRows(params: IGetRowsParams): void {
      const query = toQuery(
        { startRow: params.startRow, endRow: params.endRow, sortModel: params.sortModel },
        filters(),
      );
      api.list(query).subscribe({
        next: (page) => {
          listener?.loaded(page);
          params.successCallback(page.rows, page.rowCount);
        },
        error: (error: unknown) => {
          listener?.failed(toApiFailure(error));
          params.failCallback();
        },
      });
    },
  };
}
