import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import {
  CreateDocumentBody,
  DocumentAction,
  DocumentDetail,
  DocumentPage,
  DocumentQuery,
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
  ) {
    super(detail);
    this.name = 'ApiFailure';
  }
}

interface ProblemBody {
  code?: string;
  detail?: string;
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
