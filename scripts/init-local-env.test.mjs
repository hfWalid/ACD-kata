import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtemp, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const script = fileURLToPath(new URL('./init-local-env.mjs', import.meta.url));

test('creates a private local environment file from any working directory', async (t) => {
  const directory = await mkdtemp(join(tmpdir(), 'acd-local-env-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const destination = join(directory, '.env.local');

  const result = spawnSync(process.execPath, [script, '.env.local'], {
    cwd: directory,
    encoding: 'utf8',
  });

  assert.ok(result.status === 0, 'Initialization succeeds');
  const contents = await readFile(destination, 'utf8');
  const password = contents.match(/^POSTGRES_PASSWORD=([0-9a-f]{48})$/m)?.[1];
  const port = Number(contents.match(/^POSTGRES_PORT=(\d+)$/m)?.[1]);
  assert.ok(Boolean(password), 'A generated password replaces the template placeholder');
  assert.ok(port >= 1 && port <= 65_535, 'The template provides a valid local port');
  assert.ok(((await stat(destination)).mode & 0o777) === 0o600, 'New files have mode 0600');
  assert.ok(!result.stdout.includes(password), 'Standard output contains no password');
  assert.ok(!result.stderr.includes(password), 'Standard error contains no password');
  assert.ok(result.stdout.includes('Created'), 'Output reports creation');
});

test('a second invocation retains the generated file byte for byte', async (t) => {
  const directory = await mkdtemp(join(tmpdir(), 'acd-local-env-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const destination = join(directory, '.env.local');
  const first = spawnSync(process.execPath, [script, destination], { encoding: 'utf8' });
  assert.ok(first.status === 0, 'First initialization succeeds');
  const original = await readFile(destination);

  const second = spawnSync(process.execPath, [script, destination], { encoding: 'utf8' });

  assert.ok(second.status === 0, 'Repeated initialization succeeds');
  assert.ok(original.equals(await readFile(destination)), 'The existing file is unchanged');
  assert.ok(second.stdout.includes('Retained'), 'Output reports retention');
});

test('an existing custom environment file and its permissions are preserved', async (t) => {
  const directory = await mkdtemp(join(tmpdir(), 'acd-local-env-'));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const destination = join(directory, 'custom environment.local');
  const original = Buffer.from('# Custom local settings\nPOSTGRES_PASSWORD=test-only-existing-value\nPOSTGRES_PORT=55499\n');
  await writeFile(destination, original, { flag: 'wx', mode: 0o640 });
  const originalMode = (await stat(destination)).mode;

  const result = spawnSync(process.execPath, [script, destination], { encoding: 'utf8' });

  assert.ok(result.status === 0, 'Existing custom settings are accepted');
  assert.ok(original.equals(await readFile(destination)), 'Custom content is unchanged');
  assert.ok((await stat(destination)).mode === originalMode, 'Existing permissions are unchanged');
  assert.ok(result.stdout.includes('Retained'), 'Output reports retention');
  assert.ok(!result.stdout.includes('test-only-existing-value'), 'Output contains no existing password');
  assert.ok(!result.stderr.includes('test-only-existing-value'), 'Errors contain no existing password');
});
