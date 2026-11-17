import { randomBytes } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const destination = process.argv[2]
  ? resolve(process.argv[2])
  : fileURLToPath(new URL('../.env.local', import.meta.url));

try {
  const template = await readFile(new URL('../.env.example', import.meta.url), 'utf8');
  const passwordLine = /^POSTGRES_PASSWORD=.*$/gm;
  if ([...template.matchAll(passwordLine)].length !== 1) {
    throw new Error('The local environment template requires one password entry');
  }
  const contents = template.replace(passwordLine, `POSTGRES_PASSWORD=${randomBytes(24).toString('hex')}`);
  try {
    await writeFile(destination, contents, { flag: 'wx', mode: 0o600, encoding: 'utf8' });
    console.log(`Created local environment file: ${destination}`);
  } catch (error) {
    if (error.code !== 'EEXIST') {
      throw error;
    }
    console.log(`Retained existing local environment file: ${destination}`);
  }
} catch {
  console.error(`Failed to initialize local environment file: ${destination}`);
  process.exitCode = 1;
}
