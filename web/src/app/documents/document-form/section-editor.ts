import { Component, input, output } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { showsError } from './form-error';

/**
 * One section row. The group is bound directly instead of through `formGroupName`
 * inside `@for`, which avoids a change-detection error when rows are appended.
 */
@Component({
  selector: 'app-section-editor',
  imports: [ReactiveFormsModule],
  templateUrl: './section-editor.html',
})
export class SectionEditor {
  readonly group = input.required<FormGroup>();
  readonly index = input.required<number>();
  readonly remove = output<void>();

  readonly showError = showsError;
}
