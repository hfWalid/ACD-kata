import { section, titledWith, uniqueToken } from '../support/api';

/**
 * S9 — Parcours de contribution, entièrement par l'interface.
 *
 * These two specs are the ones a human would demo: fill the form, submit, read the audit trail.
 * Nothing is stubbed, so a regression anywhere between the browser, the proxy, the REST contract,
 * the domain rules and PostgreSQL fails here.
 *
 * Selectors: labels and visible text first (`#title`, `cy.contains('button', 'Soumettre')`),
 * because they are what a user perceives and they fail when accessibility breaks. The component
 * selector `app-status-badge` is used for the status because the word "Brouillon" also appears in
 * a help sentence on the same page — a `cy.contains` would be ambiguous.
 */
describe('Contribution', () => {
  it('crée un brouillon, le soumet et laisse une trace auditable', () => {
    const title = titledWith(uniqueToken(), 'notice');

    cy.visit('/documents/new');
    cy.get('#templateRef').type('notice-v1');
    cy.get('#title').type(title);
    // The create form starts with one empty section row; filling it is enough.
    cy.get('#section-title-0').type('Objet');
    cy.get('#section-content-0').type('Contenu saisi par le parcours end-to-end.');
    cy.contains('button', 'Créer le brouillon').click();

    // The list route is generic on purpose: the id is unknown before the create.
    cy.location('pathname').should('match', /^\/documents\/[0-9a-f-]{36}$/);
    cy.contains('h1', title).should('be.visible');
    cy.get('app-status-badge').should('contain', 'Brouillon');
    cy.contains('Version 1');

    cy.contains('button', 'Soumettre').click();

    cy.get('app-status-badge').should('contain', 'Soumis');
    cy.contains('Soumettre : version 2.');
    cy.contains('button', 'Commencer la revue').should('be.visible');
    // Two versions, one per command, and the second one names who acted.
    cy.get('tbody tr').should('have.length', 2);
    cy.get('tbody tr')
      .eq(1)
      .within(() => {
        cy.contains('SUBMITTED');
        cy.contains('web-kata');
      });
  });

  it('affiche le refus du domaine quand on soumet un document vide', () => {
    const title = titledWith(uniqueToken(), 'vide');

    cy.visit('/documents/new');
    cy.get('#templateRef').type('notice-v1');
    cy.get('#title').type(title);
    // Deleting the default row is how a user creates a document with no section at all.
    cy.contains('button', 'Supprimer').click();
    cy.contains('button', 'Créer le brouillon').click();

    cy.get('app-status-badge').should('contain', 'Brouillon');
    cy.contains('button', 'Soumettre').click();

    // The message is the one the server produced: the interface does not reinterpret a 409.
    cy.get('[role="alert"]').should('contain', 'EMPTY_DOCUMENT');
    cy.get('app-status-badge').should('contain', 'Brouillon');
    cy.contains('Version 1');
  });
});
