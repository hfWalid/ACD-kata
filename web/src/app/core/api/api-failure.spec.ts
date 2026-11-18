import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { ApiFailure, toApiFailure } from './api-failure';

describe('toApiFailure', () => {
  it('keeps the stable problem code, the detail and the rejected fields', () => {
    const failure = toApiFailure(
      new HttpErrorResponse({
        status: 400,
        error: {
          code: 'INVALID_REQUEST',
          detail: 'Request values are invalid',
          errors: [{ field: 'title', message: 'must not be blank' }],
        },
      }),
    );

    expect(failure).toBeInstanceOf(ApiFailure);
    expect(failure).toMatchObject({ status: 400, code: 'INVALID_REQUEST' });
    expect(failure.fieldErrors).toEqual([{ field: 'title', message: 'must not be blank' }]);
  });

  it('reports an unreachable server as a network failure', () => {
    const failure = toApiFailure(new HttpErrorResponse({ status: 0 }));

    expect(failure).toMatchObject({ status: 0, code: 'NETWORK' });
    expect(failure.fieldErrors).toEqual([]);
  });

  it('falls back to an unexpected failure for anything else', () => {
    expect(toApiFailure(new Error('boom'))).toMatchObject({ code: 'UNEXPECTED' });
    const known = new ApiFailure(409, 'VERSION_CONFLICT', 'The document has changed');
    expect(toApiFailure(known)).toBe(known);
  });
});
