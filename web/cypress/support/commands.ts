import { API_AUTHOR, CreateDocumentInput, DocumentView, section } from './api';

/**
 * Custom commands.
 *
 * A command is worth creating when several specs say the same thing. Here that is "prepare a
 * document through the API". Keeping it in one place means a change to the REST contract breaks
 * one file instead of six, and the failure message names the command.
 *
 * Note what these commands are *not*: they do not wrap the user interface (a "login" page object,
 * a "fillForm" helper). Clicking through the real interface is the whole point of the specs.
 */
declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable {
      /** Creates a draft through the REST API and yields the document it produced. */
      createDocument(input: CreateDocumentInput): Chainable<DocumentView>;

      /** Yields the titles currently displayed by the grid, in row order. */
      titles(): Chainable<string[]>;

      /** Moves a document with `submit`, `start-review`, `approve`, … and yields the new state. */
      transitionDocument(
        id: string,
        action: 'submit' | 'start-review' | 'approve' | 'reject' | 'publish' | 'archive',
        expectedVersion: number,
      ): Chainable<DocumentView>;
    }
  }
}

Cypress.Commands.add('createDocument', (input: CreateDocumentInput) => {
  return cy
    .request<DocumentView>({
      method: 'POST',
      url: '/api/documents',
      headers: { 'X-User': API_AUTHOR },
      body: {
        templateRef: input.templateRef ?? 'notice-v1',
        title: input.title,
        sections: input.sections ?? [section('Objet', 'Contenu préparé par le test.')],
      },
    })
    .then((response) => response.body);
});

Cypress.Commands.add('titles', () => {
  // `role="gridcell"` plus `col-id` are AG Grid's stable public hooks; the container class names
  // are internal and change between major versions (they did between 32 and 36).
  return cy
    .get('[role="gridcell"][col-id="title"]')
    .then(($cells) => [...$cells].map((cell) => (cell.textContent ?? '').trim()));
});

Cypress.Commands.add(
  'transitionDocument',
  (id: string, action: string, expectedVersion: number) => {
    return cy
      .request<DocumentView>({
        method: 'POST',
        url: `/api/documents/${id}/${action}`,
        headers: { 'X-User': API_AUTHOR },
        body: { expectedVersion },
      })
      .then((response) => response.body);
  },
);

export {};
