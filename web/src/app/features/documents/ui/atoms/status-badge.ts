import { Component, computed, input } from '@angular/core';
import { DocumentStatus } from '../../../../core/models/document.model';
import { STATUS_LABELS, STATUS_TONES } from '../../data/document-status';

/**
 * Smallest visual unit for a document state. It owns the badge shape and the status vocabulary,
 * so a page never re-declares the classes or the French label of a status.
 */
@Component({
  selector: 'app-status-badge',
  template: `<span class="rounded-full px-3 py-1 text-sm font-medium" [class]="tone()">{{
    label()
  }}</span>`,
})
export class StatusBadge {
  readonly status = input.required<DocumentStatus>();

  readonly label = computed(() => STATUS_LABELS[this.status()]);
  readonly tone = computed(() => STATUS_TONES[this.status()]);
}
