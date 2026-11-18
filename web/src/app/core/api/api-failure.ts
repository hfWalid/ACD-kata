import { HttpErrorResponse } from '@angular/common/http';

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

/**
 * Maps anything the HTTP layer throws onto the single failure type the screens handle. The
 * server's stable `code` and its rejected fields are preserved as they are the contract.
 */
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
