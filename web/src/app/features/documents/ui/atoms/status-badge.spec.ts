import { TestBed } from '@angular/core/testing';
import { DocumentStatus } from '../../../../core/models/document.model';
import { STATUS_LABELS, STATUS_TONES } from '../../data/document-status';
import { StatusBadge } from './status-badge';

async function render(status: DocumentStatus) {
  await TestBed.configureTestingModule({ imports: [StatusBadge] }).compileComponents();
  const fixture = TestBed.createComponent(StatusBadge);
  fixture.componentRef.setInput('status', status);
  fixture.detectChanges();
  return { fixture, element: fixture.nativeElement as HTMLElement };
}

describe('StatusBadge', () => {
  it('renders the French label and the tone of the status', async () => {
    const { element } = await render('InReview');

    const badge = element.querySelector('span');
    expect(badge?.textContent?.trim()).toBe('En revue');
    expect(badge?.textContent?.trim()).toBe(STATUS_LABELS.InReview);
    expect(badge?.className).toContain(STATUS_TONES.InReview);
  });

  it('follows the status input when it changes', async () => {
    const { fixture, element } = await render('Draft');
    expect(element.querySelector('span')?.textContent?.trim()).toBe('Brouillon');

    fixture.componentRef.setInput('status', 'Archived');
    fixture.detectChanges();

    expect(element.querySelector('span')?.textContent?.trim()).toBe('Archivé');
    expect(element.querySelector('span')?.className).toContain(STATUS_TONES.Archived);
  });
});
