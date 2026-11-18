import type { SortModelItem } from 'ag-grid-community';
import {
  DocumentQuery,
  DocumentSortField,
  DocumentStatus,
} from '../../../core/models/document.model';

/**
 * The API accepts only these four fields, so an unknown column id falls back to
 * the default sort instead of producing a 400 from `GET /api/documents`.
 */
const ACCEPTED_FIELDS: Readonly<Record<string, DocumentSortField>> = {
  title: 'title',
  updatedAt: 'updatedAt',
  status: 'status',
  version: 'version',
};

export const DEFAULT_SORT = 'updatedAt:desc';

export interface GridFilters {
  readonly status?: DocumentStatus;
  readonly q?: string;
}

export interface GridWindow {
  readonly startRow: number;
  readonly endRow: number;
  readonly sortModel?: readonly SortModelItem[];
}

export function toSort(sortModel?: readonly SortModelItem[]): string {
  const first = sortModel?.[0];
  const field = first ? ACCEPTED_FIELDS[first.colId] : undefined;
  if (!first || !field) {
    return DEFAULT_SORT;
  }
  return `${field}:${first.sort === 'asc' ? 'asc' : 'desc'}`;
}

export function toQuery(window: GridWindow, filters: GridFilters): DocumentQuery {
  const q = filters.q?.trim();
  return {
    startRow: window.startRow,
    endRow: window.endRow,
    sort: toSort(window.sortModel),
    status: filters.status,
    q: q ? q : undefined,
  };
}
