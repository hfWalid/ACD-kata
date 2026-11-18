import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { ApiFailure, DocumentApi, PEDAGOGICAL_AUTHOR } from './document-api';
import { DocumentDetail } from './document.models';

const DOCUMENT: DocumentDetail = {
  id: 'doc-1',
  templateRef: 'notice-v1',
  title: 'Notice',
  sections: [{ title: 'Objet', content: 'Contenu' }],
  status: 'Draft',
  version: 1,
  createdAt: '2026-11-17T10:00:00Z',
  updatedAt: '2026-11-17T10:00:00Z',
  auditTrail: [
    {
      version: 1,
      action: 'CREATED',
      author: PEDAGOGICAL_AUTHOR,
      occurredAt: '2026-11-17T10:00:00Z',
    },
  ],
};

describe('DocumentApi', () => {
  let api: DocumentApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(DocumentApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests one exclusive window with the default sort and no filter', async () => {
    const response = firstValueFrom(api.list({ startRow: 0, endRow: 25, sort: 'updatedAt:desc' }));
    const request = http.expectOne((candidate) => candidate.url === '/api/documents');

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('startRow')).toBe('0');
    expect(request.request.params.get('endRow')).toBe('25');
    expect(request.request.params.get('sort')).toBe('updatedAt:desc');
    expect(request.request.params.has('filter.status')).toBe(false);
    expect(request.request.params.has('q')).toBe(false);

    request.flush({ rowCount: 1, rows: [] });
    await expect(response).resolves.toEqual({ rowCount: 1, rows: [] });
  });

  it('adds the status filter and the search term when provided', async () => {
    void firstValueFrom(
      api.list({ startRow: 25, endRow: 50, sort: 'title:asc', status: 'InReview', q: 'contrat' }),
    );
    const request = http.expectOne((candidate) => candidate.url === '/api/documents');

    expect(request.request.params.get('startRow')).toBe('25');
    expect(request.request.params.get('filter.status')).toBe('InReview');
    expect(request.request.params.get('q')).toBe('contrat');
    request.flush({ rowCount: 0, rows: [] });
  });

  it('sends mutations with the pedagogical author and the expected version', async () => {
    const created = firstValueFrom(
      api.create({ templateRef: 'notice-v1', title: 'Notice', sections: [] }),
    );
    const post = http.expectOne((candidate) => candidate.url === '/api/documents');
    expect(post.request.method).toBe('POST');
    expect(post.request.headers.get('X-User')).toBe(PEDAGOGICAL_AUTHOR);
    expect(post.request.body).toEqual({ templateRef: 'notice-v1', title: 'Notice', sections: [] });
    post.flush(DOCUMENT);
    await expect(created).resolves.toEqual(DOCUMENT);

    const updated = firstValueFrom(
      api.updateContent('doc-1', { expectedVersion: 1, title: 'Corrigé', sections: [] }),
    );
    const put = http.expectOne((candidate) => candidate.url === '/api/documents/doc-1/content');
    expect(put.request.method).toBe('PUT');
    expect(put.request.headers.get('X-User')).toBe(PEDAGOGICAL_AUTHOR);
    expect(put.request.body).toEqual({ expectedVersion: 1, title: 'Corrigé', sections: [] });
    put.flush(DOCUMENT);
    await updated;

    const transitioned = firstValueFrom(api.transition('doc-1', 'start-review', 2));
    const action = http.expectOne(
      (candidate) => candidate.url === '/api/documents/doc-1/start-review',
    );
    expect(action.request.method).toBe('POST');
    expect(action.request.body).toEqual({ expectedVersion: 2 });
    action.flush(DOCUMENT);
    await transitioned;
  });

  it('reads a single document by identifier', async () => {
    const response = firstValueFrom(api.get('doc-1'));
    http.expectOne('/api/documents/doc-1').flush(DOCUMENT);
    await expect(response).resolves.toEqual(DOCUMENT);
  });

  it('keeps the stable problem code and the server detail on failure', async () => {
    const response = firstValueFrom(api.transition('doc-1', 'submit', 1));
    http.expectOne('/api/documents/doc-1/submit').flush(
      { code: 'EMPTY_DOCUMENT', detail: 'A submitted document requires non-empty sections' },
      {
        status: 409,
        statusText: 'Conflict',
      },
    );

    await expect(response).rejects.toMatchObject({
      code: 'EMPTY_DOCUMENT',
      status: 409,
      detail: 'A submitted document requires non-empty sections',
    });
    await response.catch((error: unknown) => {
      expect(error).toBeInstanceOf(ApiFailure);
    });
  });

  it('keeps the fields rejected by the server on a validation failure', async () => {
    const response = firstValueFrom(
      api.create({ templateRef: 'NOTE', title: '', sections: [{ title: 'Objet', content: 'x' }] }),
    );
    http.expectOne('/api/documents').flush(
      {
        code: 'INVALID_REQUEST',
        detail: 'Request values are invalid',
        errors: [
          { field: 'sections[0].title', message: 'must not be blank' },
          { field: 'title', message: 'must not be blank' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );

    await expect(response).rejects.toMatchObject({
      code: 'INVALID_REQUEST',
      status: 400,
      fieldErrors: [
        { field: 'sections[0].title', message: 'must not be blank' },
        { field: 'title', message: 'must not be blank' },
      ],
    });
  });

  it('reports an unreachable server as a network failure', async () => {
    const response = firstValueFrom(api.get('doc-1'));
    http
      .expectOne('/api/documents/doc-1')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    await expect(response).rejects.toMatchObject({ code: 'NETWORK', status: 0 });
  });
});
