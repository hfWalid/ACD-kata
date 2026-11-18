import { Component, inject, input, OnInit, output, signal } from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { DocumentSection } from '../../core/document.models';
import { showsError } from './form-error';
import { SectionEditor } from './section-editor';

export interface DocumentFormValue {
  templateRef: string;
  title: string;
  sections: DocumentSection[];
}

export interface DocumentFormInitial {
  templateRef: string;
  title: string;
  sections: DocumentSection[];
}

type SectionGroup = FormGroup<{ title: FormControl<string>; content: FormControl<string> }>;

/** Rejects blank strings, which `Validators.required` accepts. */
function trimmedRequired(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  return typeof value === 'string' && value.trim().length > 0 ? null : { required: true };
}

@Component({
  selector: 'app-document-form',
  imports: [ReactiveFormsModule, SectionEditor],
  templateUrl: './document-form.html',
})
export class DocumentForm implements OnInit {
  private readonly fb = inject(FormBuilder);

  readonly mode = input<'create' | 'edit'>('edit');
  readonly initial = input<DocumentFormInitial>({ templateRef: '', title: '', sections: [] });
  readonly submitLabel = input('Enregistrer le contenu');
  readonly busy = input(false);
  readonly saved = output<DocumentFormValue>();

  readonly maxSections = 50;
  readonly showError = showsError;
  readonly sections: FormArray<SectionGroup> = this.fb.array<SectionGroup>([]);

  /**
   * Mirrors the form array for `@for`: the array is mutated by the forms API, while a new
   * array reference is required for the loop to reconcile appended and removed rows.
   */
  readonly rows = signal<SectionGroup[]>([]);

  readonly form = this.fb.nonNullable.group({
    templateRef: this.fb.nonNullable.control('', [trimmedRequired, Validators.maxLength(100)]),
    title: this.fb.nonNullable.control('', [trimmedRequired, Validators.maxLength(200)]),
    sections: this.sections,
  });

  ngOnInit(): void {
    const initial = this.initial();
    this.form.patchValue({ templateRef: initial.templateRef, title: initial.title });
    for (const section of initial.sections) {
      this.sections.push(this.sectionGroup(section));
    }
    if (this.mode() === 'create' && this.sections.length === 0) {
      this.addSection();
    }
    this.syncRows();
    this.form.markAsPristine();
  }

  addSection(): void {
    if (this.sections.length < this.maxSections) {
      this.sections.push(this.sectionGroup({ title: '', content: '' }));
      this.syncRows();
    }
  }

  removeSection(index: number): void {
    this.sections.removeAt(index);
    this.syncRows();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saved.emit({
      templateRef: value.templateRef.trim(),
      title: value.title.trim(),
      sections: value.sections.map((section) => ({
        title: section.title.trim(),
        content: section.content,
      })),
    });
  }

  private syncRows(): void {
    this.rows.set([...this.sections.controls]);
  }

  private sectionGroup(section: DocumentSection): SectionGroup {
    return this.fb.nonNullable.group({
      title: this.fb.nonNullable.control(section.title, [
        trimmedRequired,
        Validators.maxLength(200),
      ]),
      content: this.fb.nonNullable.control(section.content, [Validators.maxLength(100_000)]),
    });
  }
}
