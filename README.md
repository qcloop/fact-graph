# Fact Graph

## What is the Fact Graph?

The Fact Graph is a production-ready knowledge graph for modeling, among other things, the United States Internal Revenue Code and related tax law.
It can be used in JavaScript as well as any JVM language (Java, Kotlin, Scala, Clojure, etc.).

## Onboarding and Set Up
See [ONBOARDING.md](ONBOARDING.md) for environment/developer setup.

See [the Fact Graph 3.1 ADR](docs/fact-graph-3.1-adr.md) for more information about the fact graph and how it has been changed since early 2025
See [here](docs/from-3.0-to-3.1.md) for a brief description of changes between the older versions of the Fact Graph and the current v3.1 in this repository 

## Compliance and checklist examples

Fact Graph can calculate policy requirements as well as tax amounts. A supplied
answer may be complete and still fail a requirement: an expired training date is
valid input, while a requirement for current training fails.

The [security-training example](demo/compliance.xml) uses an explicit `/asOf`
date, writable answers, a derived Boolean, and a named check:

```js
const dictionary = fg.FactDictionaryFactory.importFromXml(xml);
const graph = fg.GraphFactory.apply(dictionary);
graph.set('/asOf', '2026-11-20');
graph.set('/isEmployee', 'true');
graph.set('/trainingExpiresOn', '2026-10-31'); // Accepted input; policy fails.
const results = graph.check();
// [{ id: 'security-training-required', status: 'fail', ... }]
```

An unanswered date produces `undetermined`; a confirmed non-employee produces
`notApplicable`. A date on or after `/asOf` produces `pass`. Checks report findings
without blocking writes or saving the graph. See the [check contract and XML
syntax](docs/fact-dictionary-specification-3.1.md#policy-checks).

Writable limits also accept `level="Warn"`. In JavaScript, `graph.set()` returns
these in its `warnings` array while `errorType` stays `null` on a successful write.
Errors continue to reject the write. Check every setter's return value.

To run the browser demos, build the module, copy it, and serve the repository root:

```sh
sbt 'factGraphJS/fastLinkJS'
bash demo/copy-fg.sh
python3 -m http.server 8000 --bind 127.0.0.1
```

Open `http://localhost:8000/demo/` to choose a demo:

- **Fact dictionary explorer** (`demo/explorer.html`): upload an XML dictionary,
  set inputs, and inspect derived facts, saved state, and any declared checks.
- **Compliance checklist** (`demo/compliance.html`): the security-training example
  loads automatically. Compare answered inputs with the policy result, try the
  four preset scenarios, or enter more than 80 reported training hours to see a
  non-blocking warning. The example policy and threshold are illustrative.

Each page owns its graph; loading a dictionary in the explorer does not affect
the checklist. Both demos use the same generated library module.

## Exact rational arithmetic

Rational numerators and denominators use arbitrary-precision integers internally. Arithmetic and
ordering must not wrap before fraction reduction. Supplied fractions stay unreduced: same-denominator
addition/subtraction preserve their denominator, and `StepwiseMultiply` intentionally uses the raw
rate's denominator as its step size. Case-class equality remains representation-based.

The existing `new Rational(Int, Int)`, `RationalFactory` exports, Int factory overloads and Int
component accessors remain available. An Int accessor or integer rounding result outside the Int
range throws `ArithmeticException` instead of wrapping. Fraction strings and `toString` support wide
components. Rebuild consumers after upgrading; the internal Scala case-class representation changed.

Persistence retains numeric `n`/`d` fields for Int-sized components. Wider components serialize as
base-10 strings so JavaScript JSON numbers cannot lose precision; the reader accepts legacy numeric
components and exact wide strings. Every reader of a newly persisted wide fraction needs the updated
engine. Dollar conversion and mixed arithmetic round the exact ratio to cents with HALF_EVEN;
Floor/Ceiling, cent truncation and decimal formatting use their documented rounding modes.

Validate and publish the JVM dependency to the local Maven repository before building a consumer:

```sh
make format_check test
sbt 'factGraphJVM/publishM2'
```

The JVM suite includes 23 Hegel properties with 3,000-case budgets (JNA, test scope only, Java 17+).
Generators cover full Int components and signed fractions materialized up to 256 bits. Shared
regressions run on both JVM and JavaScript; a separate 200,000-bit probe checks bulk arithmetic,
formatting, hashing and JSON. Existing tests cover the remaining primitive-conversion rejections.
An independent implementation-only review supplied the signed-denominator, raw-rate, persistence,
rounding and currency-bridge contracts.

Failure ledger for the rational arithmetic fix (2026-11-17):

- Fixed unchecked Int multiplication/addition/comparison, including the mileage products
  `16366 * 181 * 725` and `2671001 * 67 * 12`, signed denominators and Int.MinValue normalization.
- Removed Float/narrowing conversions in rational consumers and retained raw stepwise semantics.
- Fixed a reproduced parser bug: the regex could accept a valid prefix before a final line terminator.
- Harness correction: property node evaluation requires importing the shared `Factual` test given.
- Integration correction: Scala.js class exports require a protected/public primary constructor.
  Moving the annotation to a secondary constructor compiled but omitted the JavaScript export; a plain
  JavaScript smoke check caught it. The primary constructor is protected, the Int constructor remains
  public, and `make test` / `make ci_test` now check the real module's constructor and factory exports.

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

## Releasing
See the [TaxAdvisor release process](docs/RELEASING.md) for Fact Graph publication,
calculator and web releases, AWS promotion, and recovery.

## Repository Update Frequency 

This repository is updated frequently. Development occurs in a private repository and approved changes to `main` are pushed to this repository in real-time.

## Useful documentation
* [ScalaTest](https://www.scalatest.org/) - the testing framework we use
* [scala-xml](https://www.scala-lang.org/api/2.12.19/scala-xml/scala/xml/) - the standard implementation of XML (don't be put off by the sparse-seeming API docs, the function definitions have very good examples)

## Legal Disclaimer: Public Repository Access

> **No Endorsement or Warranty**
>
> The Internal Revenue Service (IRS) does not endorse, maintain, or guarantee the accuracy, completeness, or functionality of the code in this repository.
> The IRS assumes no responsibility or liability for any use of the code by external parties, including individuals, developers, or organizations.
> This includes—but is not limited to—any tax consequences, computation errors, data loss, or other outcomes resulting from the use or modification of this code.
>
> Use of the code in this repository is at your own risk. Users of this repository are responsible for complying with any open source or third-party licenses.


  
## Authorities
Legal foundations for this work include:
* Source Code Harmonization And Reuse in Information Technology Act" of 2024, Public Law 118 - 187
* OMB Memorandum M-16-21, “Federal Source Code Policy: Achieving Efficiency,
Transparency, and Innovation through Reusable and Open Source Software,” August 8,
2016
* Federal Acquisition Regulation (FAR) Part 27 – Patents, Data, and Copyrights
* Digital Government Strategy: “Digital Government: Building a 21st Century Platform to
Better Serve the American People,” May 23, 2012
* Federal Information Technology Acquisition Reform Act (FITARA), December 2014
(National Defense Authorization Act for Fiscal Year 2015, Title VIII, Subtitle D)
* E-Government Act of 2002, Public Law 107-347
* Clinger-Cohen Act of 1996, Public Law 104-106
