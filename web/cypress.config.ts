import { defineConfig } from 'cypress';

/**
 * Cypress configuration.
 *
 * Three things matter here and nowhere else:
 * - `baseUrl` makes every `cy.visit('/documents')` relative, so the same specs run against the dev
 *   server, a preview build or a deployed environment (`--config baseUrl=https://...`).
 * - `specPattern` / `supportFile` are explicit so a reader never has to guess which files run.
 * - `retries` in run mode absorbs the one real source of flake in an end-to-end suite: two servers
 *   and a database answering a little late. It is not a way to hide a broken test: a spec that
 *   needs a retry to pass is printed as flaky in the run summary, which is the signal to fix it.
 */
export default defineConfig({
  e2e: {
    baseUrl: 'http://127.0.0.1:4200',
    specPattern: 'cypress/e2e/**/*.cy.ts',
    supportFile: 'cypress/support/e2e.ts',
    fixturesFolder: 'cypress/fixtures',
    // Videos are for debugging a CI failure you cannot reproduce; screenshots are enough here.
    video: false,
    screenshotOnRunFailure: true,
    viewportWidth: 1280,
    viewportHeight: 900,
    // The API answers in milliseconds; this is the budget before Cypress gives up on a command.
    defaultCommandTimeout: 8_000,
    // A real request to the backend is expected on most pages, so allow it to be slow once.
    responseTimeout: 15_000,
    retries: { runMode: 1, openMode: 0 },
  },
});
