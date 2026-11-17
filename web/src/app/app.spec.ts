import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  it('shows the scaffold phase and the next learning step', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    expect(page.querySelector('h1')?.textContent).toBe('Kata ACD');
    expect(page.querySelector('main')?.textContent).toContain('Phase S0');
    expect(page.querySelector('main')?.textContent).toContain('Prochaine étape');
  });
});
