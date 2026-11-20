import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const target = fileURLToPath(new URL('../../../target/', import.meta.url));
const modules = readdirSync(target).filter(name => name.startsWith('scala-'))
  .map(name => join(target, name, 'factgraph-fastopt', 'main.mjs')).filter(existsSync);
assert.equal(modules.length, 1, 'Run a clean factGraphJS/fastLinkJS build before checking exports');
const fg = await import(pathToFileURL(modules[0]).href);
const xml = readFileSync(new URL('../../../../demo/compliance.xml', import.meta.url), 'utf8');
const dictionary = fg.FactDictionaryFactory.importFromXml(xml);
const graph = fg.GraphFactory.apply(dictionary);

const check = (status, missingFacts) => {
  const results = graph.check();
  assert.ok(Array.isArray(results));
  assert.equal(results.length, 1);
  assert.equal(results[0].id, 'security-training-required');
  assert.equal(results[0].status, status);
  assert.equal(results[0].level, 'Error');
  assert.deepEqual(results[0].missingFacts, missingFacts);
  assert.deepEqual(JSON.parse(JSON.stringify(results)), results);
};

check('undetermined', ['/isEmployee']);
assert.equal(graph.set('/asOf', '2026-11-20').errorType, null);
assert.equal(graph.set('/isEmployee', 'true').errorType, null);
check('undetermined', ['/trainingExpiresOn']);
assert.equal(graph.set('/trainingExpiresOn', '2026-10-31').errorType, null);
assert.equal(graph.get('/trainingExpiresOn').complete, true);
check('fail', []);
assert.equal(graph.set('/trainingExpiresOn', '2026-11-20').errorType, null);
check('pass', []);
graph.set('/trainingExpiresOn', '2026-12-31');
check('pass', []);
graph.delete('/trainingExpiresOn');
check('undetermined', ['/trainingExpiresOn']);
graph.set('/isEmployee', 'false');
check('notApplicable', []);

const warning = graph.set('/reportedTrainingHours', '81');
assert.equal(warning.errorType, null);
assert.equal(warning.warnings.length, 1);
assert.equal(warning.warnings[0].level, 'Warn');
assert.equal(warning.warnings[0].actual, '81');
assert.equal(warning.warnings[0].limit, '80');
const rejected = graph.set('/reportedTrainingHours', '-1');
assert.equal(rejected.errorType, 'LimitError');
assert.equal(rejected.errorName, 'Min');
assert.equal(graph.set('/isEmployee', 'true').errorType, null);
assert.equal(graph.get('/reportedTrainingHours').get, 81);

const before = graph.toJSON();
assert.deepEqual(fg.GraphFactory.fromJSON(dictionary, before).check(), graph.check());
graph.check();
assert.equal(graph.toJSON(), before);
assert.deepEqual(fg.GraphFactory.apply(fg.FactDictionaryFactory.importFromXml(
  '<FactDictionaryModule><Facts/></FactDictionaryModule>',
)).check(), []);

// The normalized configuration path is also usable directly from JavaScript.
const meta = new fg.Meta('1.0');
const facts = [fg.DigestNodeWrapperFactory.toNative(new fg.DigestNodeWrapper(
  '/ok', null, { typeName: 'True', children: [], options: {} }, null, null, null,
))];
const config = fg.FactDictionaryConfig.create(meta, facts);
const configured = fg.FactDictionaryConfig.withChecks(config, [fg.CheckConfigFactory.create('direct', '/ok')]);
const direct = fg.GraphFactory.apply(fg.FactDictionaryFactory.fromConfig(configured)).check();
assert.equal(direct[0].status, 'pass');
assert.equal(direct[0].source, null);
assert.deepEqual(fg.GraphFactory.apply(fg.FactDictionaryFactory.fromConfig(config)).check(), []);

for (const hasError of [false, true]) {
  const mixed = fg.GraphFactory.apply(fg.FactDictionaryFactory.importFromXml(`
    <FactDictionaryModule><Facts><Fact path="/hours"><Writable><Int/>
    <Limit type="Max" level="Warn"><Int>10</Int></Limit>
    <Limit type="Max" level="Warn"><Int>20</Int></Limit>
    ${hasError ? '<Limit type="Max"><Int>25</Int></Limit>' : ''}
    </Writable></Fact></Facts></FactDictionaryModule>`));
  const result = mixed.set('/hours', '30');
  assert.equal(result.errorType, hasError ? 'LimitError' : null);
  assert.equal(result.warnings.length, 2);
  assert.deepEqual(result.warnings.map(warning => warning.limit), ['10', '20']);
  assert.equal(mixed.get('/hours').complete, !hasError);
}
console.log('Compliance checks, completeness, warning, rollback, and restore JavaScript exports passed.');
