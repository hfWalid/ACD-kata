import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiFailureAlert } from '../../core/api-failure-alert';
import { ApiFailure, DocumentApi } from '../../core/document-api';
import {
  DocumentAction,
  DocumentDetail as Document,
  DocumentVersion,
} from '../../core/document.models';
import {
  DocumentForm,
  DocumentFormInitial,
  DocumentFormValue,
} from '../document-form/document-form';
import { actionsFor, isEditable, STATUS_LABELS, STATUS_TONES } from '../document-status';

@Component({
  selector: 'app-document-detail',
  imports: [ApiFailureAlert, DocumentForm, RouterLink],
  templateUrl: './document-detail.html',
})
export class DocumentDetail implements OnInit {
  private readonly api = inject(DocumentApi);
  private readonly route = inject(ActivatedRoute);

  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';

  readonly document = signal<Document | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly failure = signal<ApiFailure | null>(null);
  readonly notice = signal<string | null>(null);
  readonly selectedVersion = signal<DocumentVersion | null>(null);
  readonly versionLoading = signal(false);
  readonly versionFailure = signal<ApiFailure | null>(null);

  readonly labels = STATUS_LABELS;
  readonly tones = STATUS_TONES;

  readonly actions = computed(() => {
    const document = this.document();
    return document ? actionsFor(document.status) : [];
  });
  readonly editable = computed(() => {
    const document = this.document();
    return document ? isEditable(document.status) : false;
  });
  readonly initial = computed<DocumentFormInitial>(() => {
    const document = this.document();
    return document
      ? { templateRef: document.templateRef, title: document.title, sections: document.sections }
      : { templateRef: '', title: '', sections: [] };
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failure.set(null);
    this.closeVersion();
    this.api.get(this.id).subscribe({
      next: (document) => {
        this.document.set(document);
        this.loading.set(false);
      },
      error: (failure: ApiFailure) => {
        this.failure.set(failure);
        this.loading.set(false);
      },
    });
  }

  saveContent(value: DocumentFormValue): void {
    const document = this.document();
    if (!document) {
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    this.notice.set(null);
    this.api
      .updateContent(document.id, {
        expectedVersion: document.version,
        title: value.title,
        sections: value.sections,
      })
      .subscribe({
        next: (updated) => {
          this.document.set(updated);
          this.notice.set(`Contenu enregistré en version ${updated.version}.`);
          this.busy.set(false);
        },
        error: (failure: ApiFailure) => this.handleFailure(failure),
      });
  }

  runAction(action: DocumentAction, label: string): void {
    const document = this.document();
    if (!document) {
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    this.notice.set(null);
    this.api.transition(document.id, action, document.version).subscribe({
      next: (updated) => {
        this.document.set(updated);
        this.notice.set(`${label} : version ${updated.version}.`);
        this.busy.set(false);
      },
      error: (failure: ApiFailure) => this.handleFailure(failure),
    });
  }

  /** Loads the stored values of one version; clicking the open one closes it again. */
  showVersion(version: number): void {
    if (this.selectedVersion()?.version === version) {
      this.closeVersion();
      return;
    }
    this.closeVersion();
    this.versionLoading.set(true);
    this.api.version(this.id, version).subscribe({
      next: (stored) => {
        this.selectedVersion.set(stored);
        this.versionLoading.set(false);
      },
      error: (failure: ApiFailure) => {
        this.versionFailure.set(failure);
        this.versionLoading.set(false);
      },
    });
  }

  closeVersion(): void {
    this.selectedVersion.set(null);
    this.versionFailure.set(null);
    this.versionLoading.set(false);
  }

  formatDate(value: string): string {
    return new Date(value).toLocaleString('fr-FR');
  }

  private handleFailure(failure: ApiFailure): void {
    this.busy.set(false);
    if (failure.code === 'VERSION_CONFLICT') {
      this.notice.set(
        'Le document a changé entre-temps : la version courante est rechargée, votre saisie est conservée.',
      );
      this.load();
      return;
    }
    this.failure.set(failure);
  }
}
