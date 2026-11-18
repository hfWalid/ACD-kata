export type DocumentStatus =
  'Draft' | 'Submitted' | 'InReview' | 'Approved' | 'Rejected' | 'Published' | 'Archived';

export type DocumentAction =
  'submit' | 'start-review' | 'approve' | 'reject' | 'publish' | 'archive';

export const DOCUMENT_STATUSES: readonly DocumentStatus[] = [
  'Draft',
  'Submitted',
  'InReview',
  'Approved',
  'Rejected',
  'Published',
  'Archived',
];

/** Sort fields accepted by `GET /api/documents`; anything else is rejected by the API. */
export type DocumentSortField = 'title' | 'updatedAt' | 'status' | 'version';

export interface DocumentSection {
  title: string;
  content: string;
}

export interface DocumentSummary {
  id: string;
  title: string;
  status: DocumentStatus;
  version: number;
  updatedAt: string;
}

export interface DocumentPage {
  rowCount: number;
  rows: DocumentSummary[];
}

export interface AuditEntry {
  version: number;
  action: string;
  author: string;
  occurredAt: string;
}

export interface DocumentDetail {
  id: string;
  templateRef: string;
  title: string;
  sections: DocumentSection[];
  status: DocumentStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
  auditTrail: AuditEntry[];
}

/** Exclusive window over the filtered collection, as expected by the server. */
export interface DocumentQuery {
  startRow: number;
  endRow: number;
  sort: string;
  status?: DocumentStatus;
  q?: string;
}

export interface CreateDocumentBody {
  templateRef: string;
  title: string;
  sections: DocumentSection[];
}

export interface UpdateContentBody {
  expectedVersion: number;
  title: string;
  sections: DocumentSection[];
}
