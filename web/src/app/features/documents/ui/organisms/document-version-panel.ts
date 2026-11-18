import { Component, input, output } from '@angular/core';
import { DocumentVersion } from '../../../../core/models/document.model';
import { STATUS_LABELS } from '../../data/document-status';

/**
 * Read-only view of one stored version. It receives the values and reports that it must close;
 * loading and failure stay with the page that owns the request.
 */
@Component({
  selector: 'app-document-version-panel',
  templateUrl: './document-version-panel.html',
})
export class DocumentVersionPanel {
  readonly version = input.required<DocumentVersion>();
  readonly closed = output<void>();

  readonly labels = STATUS_LABELS;

  formatDate(value: string): string {
    return new Date(value).toLocaleString('fr-FR');
  }
}
