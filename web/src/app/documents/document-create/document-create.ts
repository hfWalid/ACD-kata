import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiFailure, DocumentApi } from '../../core/document-api';
import { DocumentForm, DocumentFormValue } from '../document-form/document-form';

@Component({
  selector: 'app-document-create',
  imports: [DocumentForm],
  templateUrl: './document-create.html',
})
export class DocumentCreate {
  private readonly api = inject(DocumentApi);
  private readonly router = inject(Router);

  readonly busy = signal(false);
  readonly failure = signal<ApiFailure | null>(null);

  create(value: DocumentFormValue): void {
    this.busy.set(true);
    this.failure.set(null);
    this.api
      .create({ templateRef: value.templateRef, title: value.title, sections: value.sections })
      .subscribe({
        next: (document) => void this.router.navigate(['/documents', document.id]),
        error: (failure: ApiFailure) => {
          this.failure.set(failure);
          this.busy.set(false);
        },
      });
  }
}
