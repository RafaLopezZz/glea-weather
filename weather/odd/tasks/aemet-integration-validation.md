# AEMET integration validation

## Objective and scope
Complete the existing two-request station inventory client using observed TDD, preserve constructor-injected RestClient, and document the inspected baseline separately from new work. Station 7211B is an integration exercise, not a scientific station choice.

## Constraints
No real AEMET requests, weather downloads, .env inspection, real credentials, application launches, commits, publication, external operations, artifact deletion, dependency additions or version updates. Simulated HTTP and loopback-only isolated transport tests are allowed. Reject an empty inventory array as a structure error (explicit user decision).

## Tasks and routing
- [ ] T1 partial: Behavior and supplemental regressions are implemented; strict test-first acceptance remains incomplete because some cases lacked an individually observed RED. The initial RED had six genuine behavior failures; a separate seventh failure was an invalid JSON fixture (literal tab), not an implementation gap. The new envelope/status/data-transport tests below are already-green regressions, not retrospective RED.
- [x] T2: Added 2-second JDK connect/read timeouts, disabled redirects, isolated loopback tests, and Spring test property/runner mock isolation. See the accidental first context-test request documented below and in `docs/AEMET-INTEGRATION.md`.
- [ ] T3 partial: Javadoc and Spanish evidence documentation are present. Direct JDK Javadoc generated three production class pages under `target/javadoc-check` with 10 warnings. Maven `javadoc:javadoc` remains unavailable because plugin 3.12.0 is uncached offline; no network fallback was attempted.

## Acceptance criteria and checks
Maintain URI allowlist and first-only API key, URL/header/exception assertions and MockRestServiceServer.verify(). Distinguish HTTP status, AEMET application status, transport failures, JSON syntax and JSON structure for both stages. Reject absent/blank bodies and identifiers. No raw payload or secret in logs or exception chains. Reuse Jackson parser without speculative layers.

Exact checks: `.\mvnw.cmd "-Dtest=AemetRunnerTest" test`; after context isolation `.\mvnw.cmd test`; `.\mvnw.cmd compile`; `.\mvnw.cmd javadoc:javadoc`. Additional focused transport checks may be selected after inspecting existing tools, without network beyond loopback. No clean goal.

## Baseline evidence
Parent observed focused suite: 21 tests, zero failures/errors/skips, BUILD SUCCESS on 2026-10-12. Baseline runner creates parser per run, prints downloaded content, implicitly accepts empty arrays and catches HTTP 4xx without stage distinction. Whole weather directory was untracked; preserve existing files. Full suite not run before Spring test isolation.

## Delivery and review
Local-only delivery; no work-unit commits because user expressly prohibits them. Forecast: approximately 450–650 authored changed lines including tests/docs; one local candidate, no PR planning or publishing. Receipt-driven development is off, decided by global preference; do not start native review. Assess risk after writing for proportional independent verification.

## Regression additions in this follow-up
Array/scalar envelopes, null/string `estado` (alongside existing missing and integer-200 coverage), and second-stage transport exception sanitization were added as already-green regressions. Null, empty, and whitespace response bodies are covered for both stages. No retrospective RED is claimed.

## Direct Javadoc command
Before output generation, `Test-Path -LiteralPath "target"` and `Test-Path -LiteralPath "target\surefire-reports\TEST-com.glea.weather.AemetRunnerTest.xml"` both returned `True`. The PowerShell runner extracts only the Surefire `java.class.path` property into a variable and never prints it:

```powershell
$report = Select-Xml -Path "target\surefire-reports\TEST-com.glea.weather.AemetRunnerTest.xml" -XPath "/testsuite/properties/property[@name='java.class.path']"
$classpath = $report.Node.GetAttribute("value")
if ([string]::IsNullOrWhiteSpace($classpath)) { throw "Successful runner Surefire XML has no java.class.path property." }
& javadoc -private -encoding UTF-8 -docencoding UTF-8 -charset UTF-8 -notimestamp -d "target\javadoc-check" -classpath $classpath -sourcepath "src\main\java" -subpackages "com.glea.weather"
```

## Progress and next step
Focused runner: `.\mvnw.cmd -o "-Dtest=AemetRunnerTest" test` — 41 tests, 0 failures/errors/skips, BUILD SUCCESS. Full suite: `.\mvnw.cmd -o test` — 46 tests, 0 failures/errors/skips, BUILD SUCCESS. Compile: `.\mvnw.cmd -o compile` — BUILD SUCCESS. Direct JDK Javadoc: extracted only `java.class.path` from the successful runner Surefire XML without printing it; generated three production class pages and index at `target/javadoc-check` with 10 warnings (uncommented constants/fields and `WeatherApplication` members). The independent verifier earlier reported 12 warnings. Maven `javadoc:javadoc` was not rerun; its prior offline failure is due to uncached plugin 3.12.0. Spring context remains mocked with an explicit synthetic property override; never rerun unmocked. Historical incidents remain disclosed: an earlier unmocked context test contacted the real inventory endpoint with a synthetic key (HTTP 401), and a separate assertion exposed a redacted inherited sensitive property but made no request. No `.env` was inspected. There is no real integration claim: the final automated suite uses only simulated responses and loopback. Native assessment was unassessable because the project is untracked; independent verification was handled conservatively. RDD remains off; no commits. Mirror this document to Engram topic `odd/aemet-integration-validation/tasks` after updates.
