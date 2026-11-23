# From Fact Graph v3.0 to v3.1
An older version of the fact graph (v3.0) developed for Direct File is used in production code today, see [here](https://github.com/IRS-Public/direct-file/tree/main/direct-file/fact-graph-scala). 

The core v3.1 included in the initial commit of this repository expands on the 3.0 version in several important ways

The main changes are:

- converting the fact-graph to a standalone library
- enabling ways to import a fact dictionary, allowing import directly from XML

Some later changes:

- enable overriding derived facts when testing
- additional debugging functionality built into the library
- new CompNodes for more complex calculations

## 3.1.2-SNAPSHOT compatibility qualification — November 20, 2026

The candidate passed the exercised JVM consumer tests, but is **not a verified
drop-in replacement for the legacy Direct File frontend or its saved returns**.
The [machine-readable evidence](compatibility-3.1.2-snapshot.json) records source
revisions, candidate artifact hashes, probe results, and test limitations.

Consumer builds ran in isolated Git archive copies and a private Maven repository.
Neither consumer working trees nor published artifacts were changed. Comparisons
used both this repository's HEAD (`67bf794`) and the exact graph commit pinned by
Direct File CI (`fe41ec3`), rather than assuming every artifact named `3.1.1` has
the same source. The candidate version was `3.1.2-SNAPSHOT` throughout.

| Consumer or surface | Result |
| --- | --- |
| Fact Graph | Full JVM/JavaScript suites, export checks, and additional cleanup regressions passed. |
| Java tax-advisor | 131 tests passed with static analysis and packaging. The original 122-test comparison also passed on the CI-pinned baseline. |
| Java HTTP/MCP and TypeScript client | Packaged candidate reported health UP with the Redis/preview profiles against Jedis-Mock; discovery, session creation, fact writes/reads, application-restart restoration, and the TypeScript MCP check passed. |
| TypeScript TaxAdvisor | Typecheck, lint, 329 tests, and production build passed; includes 28 mock-backed Redis methods, with 9,000 generated cases across three properties. The additional 28 real-Redis methods were not selected. |
| Withholding estimator | 484 tests passed; site generation, API/session checks, and a Chrome scenario through all seven steps completed. Browser testing confirmed the navigation issue described below. |
| EITC assistant | 219 tests passed; site generation, API/session checks, and a Chrome scenario through all five steps completed. Collection and confirmation flows worked; browser testing found the navigation and edit-link issues below. |
| Direct File Java configuration | Existing source compiled; the previously built Java adapter linked against the candidate. |
| Direct File backend | Main and test sources compiled. Packaging failed on an existing Spotless violation in `BaseRepositoryTest.java`, also reproduced with the CI-pinned baseline. Backend tests were not executed. |
| Redis emulator coverage | Java Redis store/graph integration: 9 methods, zero skips, 15,000 generated Redis cases; the explicit Redis report gate passed. TypeScript production adapters ran against ioredis-mock with a test-only Lua JSON/error shim. Real Redis qualification remains separate. |

### Compatibility findings

- **Legacy frontend API migration is required.** Its checked-in engine accepts
  the four-argument digest constructor, restores a supplied persister through
  `GraphFactory.apply(dictionary, persister)`, and exposes `save()` returning
  `{valid, limitViolations}`. Both 3.1 baselines and the candidate differ: the
  four-argument digest conversion fails, the extra factory argument is ignored,
  and `save()` is not exposed on the JavaScript graph. These mismatches predate
  this candidate. The full frontend application suite was not run.
- **Rejected-write handling requires consumer review.** The actual Direct File
  `FactGraphService.setFacts` helper ignores the return from `set()` and checks a
  subsequent `save()`. Both baselines incorrectly commit a rejected negative
  input on that second save. The candidate keeps it rejected, but the helper
  still reports no violation. Consumers must inspect the setter's first result.
- **Unmodified legacy return JSON is incompatible with both 3.1 baselines and
  the candidate.** All 161 tested fixtures fail loading. A diagnosed cause is
  uPickle's `expected string got sequence`: legacy `EnumWrapper.item.value` fields contain
  zero/one-element arrays. A temporary diagnostic normalization to nullable
  strings allowed 160 fixtures to load and roundtrip on all three versions.
  `/finalTaxAmount`, `/totalTax`, `/agi`, and `/flowIncomplete` matched exactly
  for those 160 fixtures. One fixture still failed loading on all versions.
  This experiment is not a shipped migration or proof of as-is snapshot support.
- **Two candidate regressions were fixed during qualification.** XML severity
  names now accept the existing Java loader's case-insensitive spelling, including
  `level="warn"`. Dictionary cleanup now removes incompatible entries from the
  rollback baseline before retained values are revalidated, preventing a rejected
  edit from restoring a path already reported removed. Shared and property-based
  regressions cover these cases.

### Withholding estimator and EITC application checks

Both default sites generated successfully using the candidate JVM dependency.
The generated vendor assets were verified by SHA-256 against the candidate's
`fastLinkJS` output. The apps retain the historical filename `factgraph-3.1.0.js`;
the filename alone does not establish which engine is included.

Direct JavaScript API probes used each generated dictionary and compared each
app's checked-in engine with the candidate fast and fully optimized artifacts.
They exercised scalar entry, parsing errors, rejected limits, corrections,
collection creation/deletion, JSON roundtrips, and restoration of sessions
produced by the checked-in engines. For the synthetic scenarios, 764 TWE and
459 EITC root fact result records matched after restoration, as did the seeded
collection members. These comparisons include incomplete and provisional
results. One direct TWE calculation read in the partially filled scenario raised
`Division by zero` on the bundled engine, HEAD baseline, and candidate alike;
the machine-readable report identifies that path.

EITC's tax-year cancellation/reset and filing-status cancellation/child-clearing
data operations also passed. The candidate accepts child-name string writes;
the checked-in EITC engine returned `UnsupportedTypeError` for that operation.
Rejected negative replacements retain the previous accepted value through an
unrelated edit and restoration. Both checked-in engines instead persisted the
rejected negative value after the unrelated edit, reproducing the defect fixed
by rollback.

The additional checks exposed existing consumer limitations:

- Wildcard displays read private Scala.js vector fields. They display all values
  at sizes 0, 1, 31, and 32, but only the first 32 at sizes 33 and 64, with both
  bundled engines and the candidate fast build. Fully optimized candidate and
  HEAD-baseline builds break that field access and the private currency-type
  detection. The current app Makefiles copy the fast build.
- Actual Boolean setters persist successfully but return Scala tuples without
  the string setter's `errorType`/`warnings` fields. This contract predates the
  candidate. The generic multi-enum handlers were not exercised because no
  current multi-enum dictionary declarations were found.
- Chrome testing confirmed completeness-driven navigation that permits Next
  while a rejected replacement remains visible. In TWE, an accepted college
  student count of `1`, followed by `-1`, displays a limit error but still allows
  navigation to results after an unrelated edit. Returning and reloading restores
  `1`; a correction to `2` persists. EITC behaves similarly for self-employment
  gross income: an accepted `12000`, rejected `-1`, and unrelated expense edit
  allow navigation, while returning/reloading restores `12000`. The existing
  application handlers inspect graph completeness and do not block the current
  field error. The candidate correctly preserves the accepted values.
- EITC's results-page links for editing income point to `/app/eitc/income/`,
  which returns 404 in the generated site; the actual income page is
  `/app/eitc/agi/`. These links are hardcoded in the existing application flow
  XML and do not depend on the engine version.
- Existing handlers ignore the new warnings array, so adopting `Warn` limits
  needs application UI work.

The follow-up browser run used Google Chrome `157.0.8103.80` on macOS and the
same candidate fast assets, served only on loopback. Synthetic scenarios reached
results through all seven TWE steps and all five EITC steps. Accepted values and
corrections survived reloads. EITC child creation, second-child removal, and
retention of the first child's answers after reload worked. Cancelling an
MFJ-to-single change retained the status and child; confirming it cleared the
old child's answers (the under-25 single branch then created a fresh blank
child). Cancelling a tax-year change retained 2026 and prior answers; confirming
2025 reset the prior screening answers and selected 2025.

This is a browser smoke run of the default English multi-question flow, not an
exhaustive browser suite. PDF generation, other locales, mobile layouts, and the
optional single-question mode were not exercised. Neither application's working
tree was modified.

### Redis mock qualification for the two advisor apps

The Java `RedisSessionStoreTest` now starts Jedis-Mock on a random loopback port
by default and retains the production Lettuce client, JSON mapper, and Lua
scripts. Four shared store properties each run 3,000 cases; expiry/sliding TTL,
independent-client visibility/CAS, and deletion also pass. A Spring service
integration property adds 3,000 cases rebuilding Fact Graph from Redis-persisted
facts and provenance. Explicit `REDIS_URL` selects a real endpoint and fails if
unavailable; it no longer silently aborts the suite.

The packaged Java service was also started with the Redis profile against a
separate Jedis-Mock server. It created a planning session and stored a synthetic
fact through MCP. After restarting only the application, a fresh MCP connection
read the same session/fact successfully. The TypeScript application's
`pnpm mcp:check` discovered all 15 tools on that Redis-backed runtime.

The TypeScript app always runs its conversation/intake, document, rate-limit,
and budget contracts against ioredis-mock. Three additional 3,000-case properties
exercise Unicode message replacement, independent-client intake CAS, and budget
reservation idempotency/settlement. The mock does not provide Redis's `cjson`
global or `redis.error_reply`; a test-only JSON codec/error prelude supplies those
facilities while the production scripts execute unchanged. It does not simulate
the application scripts' results. `TEST_REDIS_URL` adds the same contracts against
real Redis; CI retains that layer.

Both applications now contain these test/dependency/documentation changes; no
production application algorithms were changed. Validation ran in isolated
copies against the exact candidate; the TypeScript persistence tests were also
run from its updated working tree. Emulator results do not establish real Redis
restart durability, eviction, TLS/auth, failover, cluster routing, or exact Lua
numeric/TTL semantics. Generated intake revisions cover 0 through 1,000,000;
budget properties cover units through 1,000. Source-review follow-ups, including
deletion interleaved inside message persistence and extreme numeric boundaries,
are recorded in the applications' documentation.

Before a release-wide compatibility claim, resolve the legacy client contracts,
provide and test an explicit saved-return migration where needed, correct
consumer rejection reporting, qualify against real Redis, and complete the
backend build. Existing tax calculations and fact-result completeness semantics
were not changed by the new check API.

