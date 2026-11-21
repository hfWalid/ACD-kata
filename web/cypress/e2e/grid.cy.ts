import { section, titledWith, uniqueToken } from '../support/api';

/**
 * S9 — Grille AG Grid.
 *
 * What makes this an end-to-end test rather than a component test: sorting, filtering and paging
 * are decided by the server. Clicking a header sends `sort=title:asc` to the API and the rows that
 * come back are the server's answer, so the assertion covers the whole chain — datasource, query
 * string, SQL, projection and rendering.
 *
 * The grid renders only the rows in view (infinite row model), which is why the spec asserts on
 * visible cells instead of counting data: with a block of 25 and three documents, all of them are
 * in view, and the count assertion proves the filter reached the server rather than the browser.
 *
 * Selectors use the ARIA roles AG Grid emits (`role="row"`, `role="gridcell"` and `col-id`), not
 * its CSS class names: the classes are internal and were renamed between AG Grid 32 and 36, while
 * the roles are part of what assistive technology reads.
 */
describe('Grille', () => {
  const token = uniqueToken();
  const alpha = titledWith(token, 'alpha');
  const beta = titledWith(token, 'beta');
  const gamma = titledWith(token, 'gamma');

  // Three documents, three statuses, created once for the whole file: setup is not part of the test.
  before(() => {
    cy.createDocument({ title: alpha, sections: [section('Objet', 'Alpha')] });
    cy.createDocument({ title: beta, sections: [section('Objet', 'Beta')] }).then((draft) => {
      cy.transitionDocument(draft.id, 'submit', draft.version);
    });
    cy.createDocument({ title: gamma, sections: [section('Objet', 'Gamma')] })
      .then((draft) => cy.transitionDocument(draft.id, 'submit', draft.version))
      .then((submitted) => cy.transitionDocument(submitted.id, 'start-review', submitted.version))
      .then((inReview) => cy.transitionDocument(inReview.id, 'approve', inReview.version));
  });

  it('filtre par titre côté serveur et affiche le total exact', () => {
    cy.visit('/documents');
    cy.get('#document-search').type(token);

    cy.contains('3 document(s) pour ces filtres');
    cy.get('[role="row"].ag-row').should('have.length', 3);
  });

  it('trie par titre en demandant le tri au serveur', () => {
    cy.visit('/documents');
    cy.get('#document-search').type(token);
    cy.get('[role="gridcell"][col-id="title"]').should('have.length', 3);

    // One click on an unsorted column asks the server for an ascending sort, the next one descending.
    cy.get('.ag-header-cell[col-id="title"]').click();
    cy.titles().should('deep.equal', [alpha, beta, gamma]);

    cy.get('.ag-header-cell[col-id="title"]').click();
    cy.titles().should('deep.equal', [gamma, beta, alpha]);
  });

  it('filtre par statut, puis efface les filtres', () => {
    cy.visit('/documents');
    cy.get('#document-search').type(token);

    // `Draft` is the option value; `Brouillon` is its label. Selecting by value cannot be ambiguous.
    cy.get('#document-status').select('Draft');
    cy.contains('1 document(s) pour ces filtres');
    cy.get('[role="row"].ag-row').should('have.length', 1);
    cy.get('[role="gridcell"][col-id="status"]').should('contain', 'Brouillon');

    cy.contains('button', 'Effacer les filtres').click();
    // Both filters are cleared, so the total is the whole base again — and the label stops saying
    // "pour ces filtres". Asserting a row count here would be wrong: the grid only renders the rows
    // in view, so the number of `.ag-row` elements depends on the viewport, not on the data.
    cy.get('#document-search').should('have.value', '');
    cy.get('#document-status').should('have.value', '');
    // The status paragraph always ends with the count; the filter suffix is what disappears.
    cy.get('p[role="status"]').should('contain.text', 'document(s)');
    cy.get('p[role="status"]').should('not.contain.text', 'pour ces filtres');
  });
});
