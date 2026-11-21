import { section, titledWith, uniqueToken } from '../support/api';

/**
 * S9 — Concurrence optimiste, vue du navigateur.
 *
 * The page loads version 1 and keeps that number. Another user moves the document in the
 * meantime. When the stale page asks for a transition with `expectedVersion: 1`, the server
 * answers `409 VERSION_CONFLICT`, and the interface reloads the current state instead of
 * pretending the action worked.
 *
 * This is the kind of behaviour that unit tests verify in pieces and only a browser can show end
 * to end: two clients, one document, one winner.
 */
describe('Conflit de version', () => {
  it('recharge l’état courant quand un autre client a déjà agi', () => {
    cy.createDocument({
      title: titledWith(uniqueToken(), 'conflit'),
      sections: [section('Objet', 'Conflit')],
    }).then((draft) => {
      // The page is opened before the competing change, so its copy of the version is already old.
      cy.visit(`/documents/${draft.id}`);
      cy.get('app-status-badge').should('contain', 'Brouillon');
      cy.contains('Version 1');

      // Someone else submits through the API while the page is displayed.
      cy.transitionDocument(draft.id, 'submit', draft.version);
    });

    // The browser still holds version 1: this click must lose.
    cy.contains('button', 'Soumettre').click();

    cy.contains('Le document a changé entre-temps').should('be.visible');
    // After the reload the page shows the winner's state, and the stale action is gone.
    cy.get('app-status-badge').should('contain', 'Soumis');
    cy.contains('Version 2');
    cy.contains('button', 'Soumettre').should('not.exist');
    cy.contains('button', 'Commencer la revue').should('be.visible');
  });
});
