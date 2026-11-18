import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  it('offers the document navigation and renders the active route', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const links = Array.from(page.querySelectorAll('nav a')).map((link) =>
      link.getAttribute('href'),
    );

    expect(links).toEqual(['/documents', '/documents/new']);
    expect(page.querySelector('nav')?.getAttribute('aria-label')).toBe('Navigation principale');
    expect(page.querySelector('router-outlet')).not.toBeNull();
  });
});
