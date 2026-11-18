import {
  DOCUMENT_STATUSES,
  DocumentAction,
  DocumentStatus,
} from '../../../core/models/document.model';
import {
  actionsFor,
  isEditable,
  STATUS_LABELS,
  STATUS_TONES,
  statusLabel,
} from './document-status';

/** Same table as `docs/domain-model.md`: the front only displays what the domain allows. */
const EXPECTED_ACTIONS: Record<DocumentStatus, DocumentAction[]> = {
  Draft: ['submit'],
  Submitted: ['start-review'],
  InReview: ['approve', 'reject'],
  Approved: ['publish'],
  Rejected: ['submit'],
  Published: ['archive'],
  Archived: [],
};

describe('document status', () => {
  it('exposes exactly the legal actions for every status', () => {
    for (const status of DOCUMENT_STATUSES) {
      expect(actionsFor(status).map((descriptor) => descriptor.action)).toEqual(
        EXPECTED_ACTIONS[status],
      );
      expect(actionsFor(status).every((descriptor) => descriptor.label.length > 0)).toBe(true);
    }
  });

  it('allows content changes only in the editable statuses', () => {
    for (const status of DOCUMENT_STATUSES) {
      expect(isEditable(status)).toBe(status === 'Draft' || status === 'Rejected');
    }
  });

  it('labels and tones every status', () => {
    for (const status of DOCUMENT_STATUSES) {
      expect(statusLabel(status).length).toBeGreaterThan(0);
      expect(STATUS_LABELS[status]).toBe(statusLabel(status));
      expect(STATUS_TONES[status]).toContain('bg-');
    }
    expect(statusLabel('Rejected')).toBe('Rejeté');
  });
});
