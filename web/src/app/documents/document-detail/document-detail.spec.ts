import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { DocumentDetail as DocumentDto } from '../../core/document.models';
import { DocumentDetail } from './document-detail';

const IN_REVIEW: DocumentDto = {
  id: 'doc-1',
  templateRef: 'notice-v1',
  title: 'Notice en revue',
  sections: [{ title: 'Objet', content: 'Contenu' }],
  status: 'InReview',
  version: 3,
  createdAt: '2026-11-17T10:00:00Z',
  updatedAt: '2026-11-17T10:01:00Z',
  auditTrail: [
    { version: 1, action: 'CREATED', author: 'walid', occurredAt: '2026-11-17T10:00:00Z' },
    { version: 2, action: 'SUBMITTED', author: 'walid', occurredAt: '2026-11-17T10:00:30Z' },
    { version: 3, action: 'REVIEW_STARTED', author: 'walid', occurredAt: '2026-11-17T10:01:00Z' },
  ],
};

async function setup(loaded: DocumentDto = IN_REVIEW) {
  await TestBed.configureTestingModule({
    imports: [DocumentDetail],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ id: 'doc-1' }) } },
      },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(DocumentDetail);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges();
  http.expectOne('/api/documents/doc-1').flush(loaded);
  fixture.detectChanges();

  return { fixture, element: fixture.nativeElement as HTMLElement, http };
}

function actionLabels(element: HTMLElement): string[] {
  return Array.from(
    element.querySelectorAll(
      '[role="group"][aria-label="Actions disponibles pour ce statut"] button',
    ),
  ).map((button) => (button.textContent ?? '').trim());
}

describe('DocumentDetail', () => {
  it('renders the loaded document, its status and its audit trail', async () => {
    const { element } = await setup();

    expect(element.querySelector('h1')?.textContent).toContain('Notice en revue');
    expect(element.textContent).toContain('En revue');
    expect(element.textContent).toContain('Version 3');
    expect(element.querySelectorAll('tbody tr').length).toBe(3);
    expect(element.textContent).toContain('REVIEW_STARTED');
  });

  it('exposes only the actions the current status allows', async () => {
    const { element } = await setup();

    expect(actionLabels(element)).toEqual(['Approuver', 'Rejeter']);
    expect(element.querySelector('form')).toBeNull();
    expect(element.textContent).toContain("n'est modifiable qu'à l'état Brouillon ou Rejeté");
  });

  it('offers edition and submission for a draft', async () => {
    const { element } = await setup({ ...IN_REVIEW, status: 'Draft', version: 1 });

    expect(actionLabels(element)).toEqual(['Soumettre']);
    expect(element.querySelector('form')).not.toBeNull();
    expect(element.querySelector('form button[type="submit"]')?.textContent).toContain(
      'Enregistrer le contenu',
    );
  });

  it('saves the content with the version read from the server', async () => {
    const { fixture, element, http } = await setup({ ...IN_REVIEW, status: 'Draft', version: 1 });

    fixture.componentInstance.saveContent({
      templateRef: 'notice-v1',
      title: 'Titre corrigé',
      sections: [{ title: 'Objet', content: 'Nouveau contenu' }],
    });
    const request = http.expectOne('/api/documents/doc-1/content');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      expectedVersion: 1,
      title: 'Titre corrigé',
      sections: [{ title: 'Objet', content: 'Nouveau contenu' }],
    });

    request.flush({ ...IN_REVIEW, status: 'Draft', title: 'Titre corrigé', version: 2 });
    fixture.detectChanges();

    expect(element.querySelector('h1')?.textContent).toContain('Titre corrigé');
    expect(element.textContent).toContain('Contenu enregistré en version 2.');
  });

  it('runs a transition with the current version and refreshes the display', async () => {
    const { fixture, element, http } = await setup();

    fixture.componentInstance.runAction('approve', 'Approuver');
    const request = http.expectOne('/api/documents/doc-1/approve');
    expect(request.request.body).toEqual({ expectedVersion: 3 });

    request.flush({ ...IN_REVIEW, status: 'Approved', version: 4 });
    fixture.detectChanges();

    expect(element.textContent).toContain('Approuvé');
    expect(element.textContent).toContain('Approuver : version 4.');
    expect(actionLabels(element)).toEqual(['Publier']);
  });

  it('displays the domain rule returned by the server instead of guessing it', async () => {
    const { fixture, element, http } = await setup({ ...IN_REVIEW, status: 'Draft', version: 1 });

    fixture.componentInstance.runAction('submit', 'Soumettre');
    http
      .expectOne('/api/documents/doc-1/submit')
      .flush(
        { code: 'EMPTY_DOCUMENT', detail: 'A submitted document requires non-empty sections' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    const alert = element.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('A submitted document requires non-empty sections');
    expect(alert?.textContent).toContain('EMPTY_DOCUMENT');
    http.expectNone('/api/documents/doc-1');
  });

  it('lists the fields the server rejected next to the failure', async () => {
    const { fixture, element, http } = await setup({ ...IN_REVIEW, status: 'Draft', version: 1 });

    fixture.componentInstance.saveContent({
      templateRef: 'notice-v1',
      title: '',
      sections: [],
    });
    http.expectOne('/api/documents/doc-1/content').flush(
      {
        code: 'INVALID_REQUEST',
        detail: 'Request values are invalid',
        errors: [{ field: 'title', message: 'must not be blank' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    fixture.detectChanges();

    const alert = element.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Request values are invalid');
    expect(alert?.textContent).toContain('title : must not be blank');
  });

  it('shows the values stored for one version and closes them again', async () => {
    const { fixture, element, http } = await setup();

    const buttons = () => [...element.querySelectorAll('tbody button')] as HTMLButtonElement[];
    expect(buttons().map((button) => button.textContent?.trim())).toEqual([
      'Voir les valeurs',
      'Voir les valeurs',
      'Voir les valeurs',
    ]);

    buttons()[0].click();
    http.expectOne('/api/documents/doc-1/versions/1').flush({
      version: 1,
      action: 'CREATED',
      author: 'walid',
      occurredAt: '2026-11-17T10:00:00Z',
      title: 'Notice initiale',
      status: 'Draft',
      sections: [{ title: 'Objet', content: 'Première rédaction' }],
    });
    fixture.detectChanges();

    const panel = element.querySelector('#version-values');
    expect(panel?.textContent).toContain('Valeurs de la version 1');
    expect(panel?.textContent).toContain('Notice initiale');
    expect(panel?.textContent).toContain('Première rédaction');
    expect(element.querySelector('tbody button')?.getAttribute('aria-expanded')).toBe('true');

    (element.querySelector('#version-values button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(element.querySelector('#version-values')).toBeNull();
    expect(element.querySelector('tbody button')?.getAttribute('aria-expanded')).toBe('false');
  });

  it('reports a version without stored values instead of inventing them', async () => {
    const { fixture, element, http } = await setup();

    (element.querySelector('tbody button') as HTMLButtonElement).click();
    http.expectOne('/api/documents/doc-1/versions/1').flush(
      {
        code: 'DOCUMENT_VERSION_NOT_FOUND',
        detail: 'No values are stored for this document version',
      },
      { status: 404, statusText: 'Not Found' },
    );
    fixture.detectChanges();

    const alert = element.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('No values are stored for this document version');
    expect(alert?.textContent).toContain('DOCUMENT_VERSION_NOT_FOUND');
    expect(element.querySelector('#version-values')).toBeNull();
  });

  it('reloads the document and keeps the input on a stale version', async () => {
    const { fixture, element, http } = await setup({ ...IN_REVIEW, status: 'Draft', version: 1 });

    fixture.componentInstance.runAction('submit', 'Soumettre');
    http.expectOne('/api/documents/doc-1/submit').flush(
      { code: 'VERSION_CONFLICT', detail: 'The document has changed' },
      {
        status: 409,
        statusText: 'Conflict',
      },
    );
    fixture.detectChanges();

    expect(element.textContent).toContain('rechargée');
    http.expectOne('/api/documents/doc-1').flush({ ...IN_REVIEW, status: 'Submitted', version: 2 });
    fixture.detectChanges();

    expect(element.textContent).toContain('Soumis');
    expect(actionLabels(element)).toEqual(['Commencer la revue']);
  });

  it('reports a missing document without rendering the form', async () => {
    await TestBed.configureTestingModule({
      imports: [DocumentDetail],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 'doc-1' }) } },
        },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(DocumentDetail);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/documents/doc-1').flush(
      { code: 'DOCUMENT_NOT_FOUND', detail: 'The document does not exist' },
      {
        status: 404,
        statusText: 'Not Found',
      },
    );
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('DOCUMENT_NOT_FOUND');
    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('h1')).toBeNull();
  });
});
