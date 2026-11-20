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
| TaxAdvisor | 122 tests passed with static analysis and packaging. The CI-pinned baseline passed the same 122 tests. |
| TaxAdvisor HTTP/MCP | Packaged candidate reported readiness UP; initialization, tool discovery, and session creation passed using the preview profile and in-memory store. |
| Withholding estimator | 484 tests passed against the corrected candidate. |
| EITC assistant | 219 tests passed against the corrected candidate. |
| Direct File Java configuration | Existing source compiled; the previously built Java adapter linked against the candidate. |
| Direct File backend | Main and test sources compiled. Packaging failed on an existing Spotless violation in `BaseRepositoryTest.java`, also reproduced with the CI-pinned baseline. Backend tests were not executed. |
| Redis | No Redis service was available; zero Redis cases executed. The explicit Redis CI gate fails, even though Maven reports a successful test invocation. |

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

Before a release-wide compatibility claim, resolve the legacy client contracts,
provide and test an explicit saved-return migration where needed, correct
consumer rejection reporting, and qualify the Redis-backed runtime and complete
backend build. Existing tax calculations and fact-result completeness semantics
were not changed by the new check API.

