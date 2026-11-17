import assert from 'node:assert/strict';
import { existsSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// Discover the current Scala output; reject ambiguity rather than test a stale build after an upgrade.
const target = fileURLToPath(new URL('../../../target/', import.meta.url));
const modules = readdirSync(target).filter(name => name.startsWith('scala-'))
  .map(name => join(target, name, 'factgraph-fastopt', 'main.mjs')).filter(existsSync);
assert.equal(modules.length, 1, 'Run a clean factGraphJS/fastLinkJS build before checking exports');
const fg = await import(pathToFileURL(modules[0]).href);

assert.equal(new fg.Rational(1, 2).toString(), '1/2');
assert.equal(fg.RationalFactory(29, 40).toString(), '29/40');
assert.equal(fg.RationalFactory(7).toString(), '7/1');
const wide = '123456789012345678901234567890/365';
assert.equal(fg.RationalFactory(wide).toString(), wide);
assert.throws(() => new fg.Rational(1, 0));
assert.throws(() => fg.RationalFactory('1/2\n'));
console.log('Rational JavaScript constructor and factory exports passed.');
