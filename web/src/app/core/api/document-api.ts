import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { toApiFailure } from './api-failure';
import {
  CreateDocumentBody,
  DocumentAction,
  DocumentDetail,
  DocumentPage,
  DocumentQuery,
  DocumentVersion,
  UpdateContentBody,
} from '../models/document.model';

const BASE = '/api/documents';

/**
 * Pedagogical author, like `X-User: walid` in the Swagger walkthrough.
 * It is not authentication: the API trusts it and stores it in the audit trail.
 */
export const PEDAGOGICAL_AUTHOR = 'web-kata';

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
