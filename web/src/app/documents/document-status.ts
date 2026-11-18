import { DocumentAction, DocumentStatus } from '../core/document.models';

export interface ActionDescriptor {
  readonly action: DocumentAction;
  readonly label: string;
}

export const STATUS_LABELS: Readonly<Record<DocumentStatus, string>> = {
  Draft: 'Brouillon',
  Submitted: 'Soumis',
  InReview: 'En revue',
  Approved: 'Approuvé',
  Rejected: 'Rejeté',
  Published: 'Publié',
  Archived: 'Archivé',
};

/** Tailwind classes must stay literal so they survive the content scan. */
export const STATUS_TONES: Readonly<Record<DocumentStatus, string>> = {
  Draft: 'bg-slate-100 text-slate-700',
  Submitted: 'bg-sky-100 text-sky-800',
  InReview: 'bg-amber-100 text-amber-900',
  Approved: 'bg-emerald-100 text-emerald-800',
  Rejected: 'bg-rose-100 text-rose-800',
  Published: 'bg-indigo-100 text-indigo-800',
  Archived: 'bg-zinc-200 text-zinc-700',
};

/**
 * Mirrors the domain transition table (`docs/domain-model.md`).
 * The server stays the authority: an unexpected action returns 409 INVALID_TRANSITION.
 */
export const ACTIONS_BY_STATUS: Readonly<Record<DocumentStatus, readonly ActionDescriptor[]>> = {
  Draft: [{ action: 'submit', label: 'Soumettre' }],
  Submitted: [{ action: 'start-review', label: 'Commencer la revue' }],
  InReview: [
    { action: 'approve', label: 'Approuver' },
    { action: 'reject', label: 'Rejeter' },
  ],
  Approved: [{ action: 'publish', label: 'Publier' }],
  Rejected: [{ action: 'submit', label: 'Soumettre à nouveau' }],
  Published: [{ action: 'archive', label: 'Archiver' }],
  Archived: [],
};

/** Only these statuses accept a content change on the server. */
export function isEditable(status: DocumentStatus): boolean {
  return status === 'Draft' || status === 'Rejected';
}

export function statusLabel(status: DocumentStatus): string {
  return STATUS_LABELS[status];
}

export function actionsFor(status: DocumentStatus): readonly ActionDescriptor[] {
  return ACTIONS_BY_STATUS[status];
}
