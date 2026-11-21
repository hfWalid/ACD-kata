#!/usr/bin/env node
/**
 * Creates the SonarQube token a scan needs, on a freshly started local instance.
 *
 * Why a script at all: a scanner authenticates with a *token*, never with a password, so someone
 * has to create that token once. Doing it here means `make quality-up && make quality-token` is
 * enough, and it documents the API at the same time.
 *
 * The three steps are ordered because the server refuses the next one until the previous is done:
 *
 *   1. wait for `/api/system/status` to report `UP` (Elasticsearch boots first, ~1 minute);
 *   2. change the default `admin` / `admin` password, which the instance forces on first login;
 *   3. create a user token named after this repository.
 *
 * Nothing secret is printed except the token, which is written to `.env.local` (git-ignored).
 */
import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HOST = process.env['SONAR_HOST_URL'] ?? 'http://127.0.0.1:9000';
const ENV_FILE = resolve(process.argv[2] ?? fileURLToPath(new URL('../.env.local', import.meta.url)));
const TOKEN_NAME = `acd-kata-${new Date().toISOString().slice(0, 10)}`;

const sleep = (ms) => new Promise((done) => setTimeout(done, ms));

async function call(path, options = {}) {
  const response = await fetch(`${HOST}${path}`, {
    ...options,
    headers: { 'content-type': 'application/x-www-form-urlencoded', ...options.headers },
  });
  const text = await response.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = text;
  }
  return { status: response.status, body };
}

/** 1. SonarQube answers 200 on /api/system/status before it is ready; the body says UP or STARTING. */
async function waitUntilUp(timeoutMs = 180_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const { status, body } = await call('/api/system/status');
      if (status === 200 && body?.status === 'UP') {
        return;
      }
      process.stdout.write(`  statut : ${body?.status ?? 'injoignable'}\r`);
    } catch {
      process.stdout.write('  statut : injoignable\r');
    }
    await sleep(3_000);
  }
  throw new Error(`SonarQube n'a pas démarré sur ${HOST} en 3 minutes`);
}

function basic(login, password) {
  return `Basic ${Buffer.from(`${login}:${password}`).toString('base64')}`;
}

async function readEnvFile() {
  try {
    return await readFile(ENV_FILE, 'utf8');
  } catch {
    throw new Error(`Fichier d'environnement introuvable : ${ENV_FILE} (lancer make local-env)`);
  }
}

function valueOf(contents, key) {
  const match = contents.match(new RegExp(`^${key}=(.*)$`, 'm'));
  return match ? match[1].trim() : null;
}

/**
 * 2. The first login of a fresh instance must change the password before any other call succeeds.
 * When the password was already changed, the API answers 401 and we move on with the stored one.
 */
async function ensureAdminPassword(adminPassword) {
  const probe = await call('/api/user_tokens/search?login=admin', {
    headers: { authorization: basic('admin', adminPassword) },
  });
  if (probe.status === 200) {
    return adminPassword;
  }
  // Two details the API documentation does not make obvious: the call authenticates with the
  // *current* password (`admin` / `admin` on a new instance) and it is a POST with query
  // parameters, not a JSON body.
  const changed = await call(
    `/api/users/change_password?login=admin&previousPassword=admin&password=${encodeURIComponent(adminPassword)}`,
    { method: 'POST', headers: { authorization: basic('admin', 'admin') } },
  );
  if (changed.status !== 204 && changed.status !== 200) {
    throw new Error(
      `Impossible de définir le mot de passe administrateur (${changed.status}). ` +
        'Vérifier SONAR_ADMIN_PASSWORD dans .env.local.',
    );
  }
  return adminPassword;
}

/** 3. A token is shown once by SonarQube; that is why it is written to the environment file. */
async function createToken(adminPassword) {
  const created = await call(`/api/user_tokens/generate?name=${encodeURIComponent(TOKEN_NAME)}`, {
    method: 'POST',
    headers: { authorization: basic('admin', adminPassword) },
  });
  if (created.status !== 200 || !created.body?.token) {
    throw new Error(`Création du jeton impossible (${created.status}) : ${JSON.stringify(created.body)}`);
  }
  return created.body.token;
}

async function main() {
  const contents = await readEnvFile();
  const adminPassword = process.env['SONAR_ADMIN_PASSWORD'] ?? valueOf(contents, 'SONAR_ADMIN_PASSWORD');
  if (!adminPassword) {
    throw new Error('SONAR_ADMIN_PASSWORD est absent de .env.local');
  }

  console.log(`Attente de SonarQube sur ${HOST}…`);
  await waitUntilUp();
  console.log(`SonarQube est prêt sur ${HOST}`);

  const password = await ensureAdminPassword(adminPassword);
  const token = await createToken(password);

  const updated = contents.match(/^SONAR_TOKEN=.*$/m)
    ? contents.replace(/^SONAR_TOKEN=.*$/m, `SONAR_TOKEN=${token}`)
    : `${contents.trimEnd()}\nSONAR_TOKEN=${token}\n`;
  await writeFile(ENV_FILE, updated, { mode: 0o600 });
  console.log(`Jeton écrit dans ${ENV_FILE} (SONAR_TOKEN).`);
}

await main();
