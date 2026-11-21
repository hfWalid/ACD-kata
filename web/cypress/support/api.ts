/**
 * Shared vocabulary for the end-to-end specs.
 *
 * Two habits keep this suite trustworthy:
 *
 * 1. **Arrange through the API, act through the interface.** Creating a document by clicking
 *    through a form takes seconds and couples every spec to the form. `cy.request` prepares the
 *    state in milliseconds and fails with a clear message when the API is wrong; only the
 *    behaviour under test goes through the browser. This is the official recommendation from the
 *    Cypress docs ("best practices → server communication").
 *
 * 2. **Every run owns its data.** The database also holds the 10 000 seeded demo documents, so a
 *    spec must never assert on "the first row". Each run stamps its documents with a token and
 *    filters on it, which makes the suite re-runnable without truncating anything.
 */

/** The author used by the interface itself (`document-api.ts`), so assertions can name who acted. */
export const UI_AUTHOR = 'web-kata';

/** The author used by the specs when they prepare data through the API. */
export const API_AUTHOR = 'e2e-runner';

export interface SectionInput {
  title: string;
  content: string;
}

export interface CreateDocumentInput {
  title: string;
  templateRef?: string;
  sections?: SectionInput[];
}

/** The read model the API returns; only the fields the specs assert on. */
export interface DocumentView {
  id: string;
  title: string;
  templateRef: string;
  status: string;
  version: number;
  sections: SectionInput[];
  auditTrail: { version: number; action: string; author: string }[];
}

/**
 * A short, unique, sortable suffix: base 36 timestamp plus randomness. Sortable matters — the grid
 * spec asserts on ordering, so the titles it creates must order predictably.
 */
export function uniqueToken(): string {
  return `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 5)}`;
}

/** Titles start with the token so a search for it isolates exactly this run's documents. */
export function titledWith(token: string, name: string): string {
  return `E2E ${token} ${name}`;
}

export function section(title: string, content: string): SectionInput {
  return { title, content };
}
