# Release process baseline on November 17 2026

Snapshot supporting the [TaxAdvisor release policy](RELEASING.md). Repository
inspection and read-only AWS queries were performed on November 17, 2026.
The attached release-process brief supplies design guidance; its implementation
commands were not treated as authorization to publish or deploy a release.

## Source and version inventory

| Unit | Observed baseline |
| --- | --- |
| Fact Graph | Scala 3.3.6, sbt 1.11.4, JVM/Scala.js cross-project; `gov.irs:factgraph_3:3.1.1`; `build.sbt` version `3.1.1` |
| Calculator | Java/Maven service under `qcloop/direct-file/direct-file/tax-advisor`; project version `0.0.1`; CI uses Corretto 25 and the shared Maven parent/library dependency closure |
| TaxAdvisor | pnpm 10.18.2/Turborepo/Next.js workspace; root version `0.0.1`; CI/build runtime uses Node 22 |
| Infra | JSON CloudFormation, Python validation/deployment tooling, cfn-lint/Guard policies, environment and image manifests; no product version file found |

Remote tag inventory returned only Fact Graph's bare `3.1.1` tag, resolving to
`4eaf8f9eb195c2b94247802fed5e8e7f91e16464`. No remote tags were returned for
`qcloop/direct-file`, `qcloop/TaxAdvisor` or `qcloop/TaxAdvisor-Infra`.
GitHub Release objects, current merge settings, protection/ruleset configuration,
Actions write permissions and release App availability remain administrator
verification items. Version fields alone were not used as published baselines.

The active Fact Graph branch is `Release/3.1.1`. Its tagged commit is not an
ancestor of the local `main` ref at `1437545`; remote `main` ancestry must be
rechecked at bootstrap. Integrate the intended changes through review and select
a new release version according to compatibility; preserve the existing tag.
The current README calls out consumer rebuild and persistence compatibility for
the rational-arithmetic changes, which need explicit release notes.

Source files inspected include `build.sbt`, `Makefile`, Fact Graph's three
workflows, the calculator POM/Dockerfile/PR CI scripts, TaxAdvisor's root
`package.json`, Dockerfile and PR workflow, and Infra's templates, scripts,
schemas, `docs/cicd.md`, deployment records and manifests.

## Verified AWS delivery configuration

AWS identity was checked against account `961116410386`, region `us-west-2`.
Both named delivery pipelines are CodePipeline V2 with `QUEUED` execution:

| Pipeline | Source repository | Current automatic trigger |
| --- | --- | --- |
| `taxadvisor-pilot-web-delivery` | `qcloop/TaxAdvisor` | Push to `main` |
| `taxadvisor-pilot-calculator-delivery` | `qcloop/direct-file` | Push to `main` |

Both use the source action `Application`, CodeConnections and `CODE_ZIP` artifacts.
The web pipeline was read back with the stages `Source`, `BuildAndTest`, `Prepare`,
`Approval` and `DeployAndVerify`. Its `ApproveRelease` action is a manual AWS
approval, delivered through the existing operations SNS topic.

ECR read-back confirms both `taxadvisor-pilot-registry/web` and
`taxadvisor-pilot-registry/calculator` have immutable image tags and scan-on-push.
The two runtime stacks and delivery stacks were `UPDATE_COMPLETE` at inspection.
Only the pilot is represented in the inspected environment configuration.

Current runtime CloudFormation image parameters matched the local
`TaxAdvisor-Infra/manifests/pilot.json` image identities:

| Component | ECR digest | Source SHA recorded in the local manifest |
| --- | --- | --- |
| Web | `sha256:101bf37f94799e2ca2a44e97f59b78358bb8a9a50dd23e066487186d5b3b5567` | `9ca50864635bffb1b209d66dec3a375e2789dc29` |
| Calculator | `sha256:93c48b433b3c1494c1b716e7235b6e05a286bdf90d1f644557443328495d173c` | `72367d84aa10bb42927fec70b63fff80497f5d9d` |

The source SHAs above are manifest assertions; the read-back directly establishes
the runtime image parameters. It does not independently re-qualify those images.
The local manifest already had an uncommitted change and was preserved.

A calculator execution for `7f05dfbec3767772cc0dd8a00063264115867b22` was
`InProgress` at inspection. It was not treated as deployed, approved or successful.
Release adoption must account for candidates already in flight.

## Dependency and script provenance

The live calculator CodeBuild configuration pins trusted bundle SHA-256
`246e85ec55108a25d4fe9558d842220cfc16238eb719041d7f12bb61444927a5`.
That S3 bundle was downloaded to temporary storage, checksum-verified and
inspected without executing its contents.

| Build path | Fact Graph source SHA | Graph source archive SHA-256 |
| --- | --- | --- |
| Calculator repository PR script | `fe41ec31b38e7d4389d8e0a5258941cdfdf19782` | `ca93ae31a49b77e55c332998d44b09c6bdb0886dd7deb84fbe82129a8ac0f05a` |
| Active AWS calculator bundle | `fe41ec31b38e7d4389d8e0a5258941cdfdf19782` | `ca93ae31a49b77e55c332998d44b09c6bdb0886dd7deb84fbe82129a8ac0f05a` |
| Local Infra `scripts/cicd/test-mcp.sh` | `3c9c2a83657be3d8e1beb9a9530c376a6dc15911` | `d1291777df0036d39a632f974ec185fc13829891fd69d2bb9b5e73ecc849cf08` |

PR CI and the active AWS bundle agree; the local Infra source is older. Neither
pin equals the current `3.1.1` tag SHA. A repeated package version across these
source revisions is not sufficient dependency provenance. Reconcile the local
Infra source before redeploying its trusted bundle, then adopt released graph
bytes and one dependency lock shared by PR and AWS builds.

The existing delivery scripts publish images using `<commit>-<build-id>` tags.
Those tags are immutable, but two runs for one source/version can still publish
different image digests. The target process therefore adds a write-once
component/version-to-artifact mapping and promotion without rebuilding.

## Existing controls and required corrections

Existing controls to preserve are isolated ARM64 builds, fresh calculator Maven
dependencies, required Redis tests, container/MCP smoke checks, image-only change
sets, manual approval, stack freshness checks, deployed-image verification,
versioned S3 records and scoped preparation/deployment roles.

The inspected local `build.py` and `release.py` reject HIGH and CRITICAL ECR
findings. Older passages in `docs/cicd.md` and approval email text describe HIGH
findings as reviewable; reconcile that wording with the deployed trusted code
during rollout. Image scanning remains an OS-package check, alongside application
tests and static/dependency analysis.

Fact Graph's inherited CI needs fork-specific review: the email job uses
`github.base_ref` even for pushes, Java 21 is declared without an explicit Java
setup step, the security workflow checks out `IRSDigitalService/fact-graph`, and
the inherited public-sync workflow targets `IRS-Public/fact-graph` with a force
push. Establish CI against this fork's exact source and explicitly decide whether
the inherited mirroring workflow belongs here before installing release automation.

The calculator Dockerfile copies `tax-advisor-0.0.1.jar`; automated version changes
must update the packaging contract. The calculator release scope must include
shared Maven modules and tax knowledge, rather than just service source files.

The current release manifest schema permits only web/calculator image/source
pairs, Bedrock configuration and `draft`/`ready` status for `pilot`. Semantic
versions, dependency identities, approval records, acceptance status and environment
release sets need separate schemas or an explicit schema migration.

The bundled 2026 knowledge manifest is `draft`, with no human approvals; required
roles are `tax_specialist`, `engineering_owner` and `content_owner`. Its release ID
is `federal-2026-1dbebd4fbf6cf434`. Preserve the explicit pilot exception without
presenting an engineering release as tax-content sign-off.

Infra's existing delivery documentation reports a GitHub plan limitation on
mandatory private UI branch protection. Reverify current plan capabilities and
settings; do not claim required reviews/checks are enforced without that evidence.

No tags, GitHub settings, pipeline configuration, approvals, AWS resources or
deployments were changed while establishing this baseline.
