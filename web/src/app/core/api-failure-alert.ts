import { Component, input } from '@angular/core';
import { ApiFailure } from './document-api';

/**
 * Renders a failed request: the `problem+json` detail and code, plus the fields the server
 * rejected when it performs Bean Validation. The messages come verbatim from the API, which is
 * the only side that can state the constraint it enforced.
 */
@Component({
  selector: 'app-api-failure-alert',
  template: `
    @if (failure(); as problem) {
      <div role="alert" class="rounded-md bg-rose-50 px-4 py-3 text-sm text-rose-800">
        <p>{{ problem.detail }} (code {{ problem.code }})</p>
        @if (problem.fieldErrors.length > 0) {
          <ul class="mt-2 list-disc space-y-1 pl-5">
            @for (error of problem.fieldErrors; track error.field + ':' + error.message) {
              <li>
                <span class="font-medium">{{ error.field }}</span> : {{ error.message }}
              </li>
            }
          </ul>
        }
      </div>
    }
  `,
})
export class ApiFailureAlert {
  readonly failure = input<ApiFailure | null>(null);
}
