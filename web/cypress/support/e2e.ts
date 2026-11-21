/**
 * Loaded automatically before every spec (see `supportFile` in `cypress.config.ts`).
 *
 * This is the place for genuinely global behaviour: registering commands, ignoring an exception
 * thrown by an unrelated third party, or logging in once. Anything narrower belongs in the spec
 * that needs it, so a reader of that spec sees everything it depends on.
 */
import './commands';
