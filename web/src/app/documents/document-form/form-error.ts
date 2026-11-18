import { AbstractControl } from '@angular/forms';

/** A field error is shown once the user has touched or edited the field. */
export function showsError(control: AbstractControl | null | undefined): boolean {
  return control != null && control.invalid && (control.touched || control.dirty);
}
