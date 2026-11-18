import { TestBed } from '@angular/core/testing';
import { DocumentForm, DocumentFormInitial, DocumentFormValue } from './document-form';

async function setup(initial?: DocumentFormInitial, mode: 'create' | 'edit' = 'edit') {
  await TestBed.configureTestingModule({ imports: [DocumentForm] }).compileComponents();
  const fixture = TestBed.createComponent(DocumentForm);
  fixture.componentRef.setInput('mode', mode);
  if (initial) {
    fixture.componentRef.setInput('initial', initial);
  }
  fixture.detectChanges();

  const saved: DocumentFormValue[] = [];
  fixture.componentInstance.saved.subscribe((value) => saved.push(value));
  return { fixture, element: fixture.nativeElement as HTMLElement, saved };
}

describe('DocumentForm', () => {
  it('starts a creation with a template reference and one empty section', async () => {
    const { element } = await setup(undefined, 'create');

    expect(element.querySelector('input#templateRef')).not.toBeNull();
    expect(element.querySelector('label[for="title"]')?.textContent).toContain('Titre');
    expect(element.querySelector('label[for="section-title-0"]')).not.toBeNull();
    expect(element.querySelector('textarea#section-content-0')).not.toBeNull();
  });

  it('shows an existing document without offering to change its template', async () => {
    const { element } = await setup({
      templateRef: 'notice-v1',
      title: 'Notice',
      sections: [{ title: 'Objet', content: 'Contenu' }],
    });

    expect(element.querySelector('input#templateRef')).toBeNull();
    expect(element.querySelector('#templateRef')?.textContent).toContain('notice-v1');
    expect((element.querySelector('input#title') as HTMLInputElement).value).toBe('Notice');
    expect((element.querySelector('textarea#section-content-0') as HTMLTextAreaElement).value).toBe(
      'Contenu',
    );
  });

  it('refuses a blank title and exposes the field error to assistive technology', async () => {
    const { fixture, element, saved } = await setup({
      templateRef: 'notice-v1',
      title: '   ',
      sections: [],
    });

    element.querySelector('form')?.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(saved).toHaveLength(0);
    expect(element.querySelector('#title-error')?.textContent).toContain('200 caractères maximum');
    expect(element.querySelector('input#title')?.getAttribute('aria-invalid')).toBe('true');
    expect(element.querySelector('input#title')?.getAttribute('aria-describedby')).toBe(
      'title-error',
    );
  });

  it('emits the normalized payload for a valid document', async () => {
    const { fixture, saved } = await setup({
      templateRef: ' notice-v1 ',
      title: ' Notice ',
      sections: [{ title: ' Objet ', content: 'Contenu\n' }],
    });

    fixture.componentInstance.submit();

    expect(saved).toEqual([
      {
        templateRef: 'notice-v1',
        title: 'Notice',
        sections: [{ title: 'Objet', content: 'Contenu\n' }],
      },
    ]);
  });

  it('adds and removes sections', async () => {
    const { fixture, element } = await setup(undefined, 'create');
    expect(element.querySelectorAll('textarea').length).toBe(1);

    fixture.componentInstance.addSection();
    fixture.detectChanges();
    expect(element.querySelectorAll('textarea').length).toBe(2);

    fixture.componentInstance.removeSection(0);
    fixture.detectChanges();
    expect(element.querySelectorAll('textarea').length).toBe(1);
  });
});
