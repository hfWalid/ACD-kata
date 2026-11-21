import { section, titledWith, uniqueToken } from '../support/api';

/**
 * S9 — Parcours de décision.
 *
 * The document is prepared through the API (`cy.createDocument`), then every step under test is a
 * click. This is the pattern to copy: the fastest possible setup, and a browser for what the
 * browser is responsible for. The assertions on the audit trail prove the four commands ran — not
 * only that the screen said "Approuvé".
 */
describe('Décision', () => {
  it('approuve un document soumis et conserve les quatre étapes', () => {
    cy.createDocument({ title: titledWith(uniqueToken(), 'approbation') }).then((draft) => {
      cy.transitionDocument(draft.id, 'submit', draft.version).then((submitted) => {
        cy.visit(`/documents/${submitted.id}`);
      });
    });

    cy.get('app-status-badge').should('contain', 'Soumis');
    cy.contains('button', 'Commencer la revue').click();

    cy.get('app-status-badge').should('contain', 'En revue');
    cy.contains('button', 'Approuver').click();

    cy.get('app-status-badge').should('contain', 'Approuvé');
    cy.contains('Approuver : version 4.');
    cy.contains('button', 'Publier').should('be.visible');
    // An approved document is not editable: the form gives way to the read-only sections.
    cy.contains("Le contenu n'est modifiable qu'à l'état Brouillon ou Rejeté.");

    cy.get('tbody tr').should('have.length', 4);
    cy.get('tbody tr')
      .eq(3)
      .within(() => {
        cy.contains('APPROVED');
        cy.contains('web-kata');
      });
  });

  it('rejette, laisse corriger puis resoumet', () => {
    cy.createDocument({
      title: titledWith(uniqueToken(), 'rejet'),
      sections: [section('Objet', 'Premier jet')],
    }).then((draft) => {
      cy.transitionDocument(draft.id, 'submit', draft.version).then((submitted) => {
        cy.transitionDocument(submitted.id, 'start-review', submitted.version).then((inReview) => {
          cy.visit(`/documents/${inReview.id}`);
        });
      });
    });

    cy.get('app-status-badge').should('contain', 'En revue');
    cy.contains('button', 'Rejeter').click();
    cy.get('app-status-badge').should('contain', 'Rejeté');

    // A rejected document becomes editable again, and resubmitting moves it back to Submitted.
    cy.contains('button', 'Enregistrer le contenu').should('be.visible');
    cy.get('#title').clear().type('Titre corrigé après rejet');
    cy.contains('button', 'Enregistrer le contenu').click();
    cy.contains('Contenu enregistré en version 5.');

    cy.contains('button', 'Soumettre à nouveau').click();
    cy.get('app-status-badge').should('contain', 'Soumis');
    cy.contains('Soumettre à nouveau : version 6.');
  });
});
