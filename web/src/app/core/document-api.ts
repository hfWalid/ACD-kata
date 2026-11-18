import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import {
  CreateDocumentBody,
  DocumentAction,
  DocumentDetail,
  DocumentPage,
  DocumentQuery,
  DocumentVersion,
  UpdateContentBody,
} from './document.models';

const BASE = '/api/documents';

/**
 * Pedagogical author, like `X-User: walid` in the Swagger walkthrough.
 * It is not authentication: the API trusts it and stores it in the audit trail.
 */
export const PEDAGOGICAL_AUTHOR = 'web-kata';

/** Failure carrying the stable `code` of the problem+json response. */
export class ApiFailure extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly detail: string,
    readonly fieldErrors: readonly FieldError[] = [],
  ) {
    super(detail);
    this.name = 'ApiFailure';
  }
}

/** One rejected input, as named by the server (`title`, `sections[0].content`, `author`). */
export interface FieldError {
  field: string;
  message: string;
}

interface ProblemBody {
  code?: string;
  detail?: string;
  errors?: FieldError[];
}

export function toApiFailure(error: unknown): ApiFailure {
  if (error instanceof ApiFailure) {
    return error;
  }
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ProblemBody | null;
    if (error.status === 0) {
      return new ApiFailure(0, 'NETWORK', 'Le serveur est injoignable.');
    }
    return new ApiFailure(
      error.status,
      body?.code ?? 'UNEXPECTED',
      body?.detail ?? 'La requête a échoué.',
      Array.isArray(body?.errors) ? body.errors : [],
    );
  }
  return new ApiFailure(0, 'UNEXPECTED', 'Erreur inattendue.');
}

@Injectable({ providedIn: 'root' })
export class DocumentApi {
  private readonly http = inject(HttpClient);

  list(query: DocumentQuery): Observable<DocumentPage> {
    let params = new HttpParams()
      .set('startRow', query.startRow)
      .set('endRow', query.endRow)
      .set('sort', query.sort);
    if (query.status) {
      params = params.set('filter.status', query.status);
    }
    if (query.q) {
      params = params.set('q', query.q);
    }
    return this.request(this.http.get<DocumentPage>(BASE, { params }));
  }

  get(id: string): Observable<DocumentDetail> {
    return this.request(this.http.get<DocumentDetail>(`${BASE}/${id}`));
  }

  /** Values stored for one version; the server answers 404 when it has no snapshot. */
  version(id: string, version: number): Observable<DocumentVersion> {
    return this.request(this.http.get<DocumentVersion>(`${BASE}/${id}/versions/${version}`));
  }

  create(body: CreateDocumentBody): Observable<DocumentDetail> {
    return this.request(this.http.post<DocumentDetail>(BASE, body, { headers: this.author() }));
  }

  updateContent(id: string, body: UpdateContentBody): Observable<DocumentDetail> {
    return this.request(
      this.http.put<DocumentDetail>(`${BASE}/${id}/content`, body, { headers: this.author() }),
    );
  }

  transition(
    id: string,
    action: DocumentAction,
    expectedVersion: number,
  ): Observable<DocumentDetail> {
    return this.request(
      this.http.post<DocumentDetail>(
        `${BASE}/${id}/${action}`,
        { expectedVersion },
        { headers: this.author() },
      ),
    );
  }

  private author(): HttpHeaders {
    return new HttpHeaders({ 'X-User': PEDAGOGICAL_AUTHOR });
  }

  private request<T>(source: Observable<T>): Observable<T> {
    return source.pipe(catchError((error: unknown) => throwError(() => toApiFailure(error))));
  }
}
