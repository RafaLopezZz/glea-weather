# AD-T2 alignment

## Objective and authorization

Simplify the AD-T2 guide for the explicitly approved one-person/manual 7211B scope, record the user's lean coverage-summary amendment, then align the plans and Jira. This remains passive documentation and synchronization, not code, SQL/schema or API implementation, or proof of system behavior.

## Scope and constraints

- Allowed surfaces: `docs/GLEA-WEATHER-DEVELOPMENT-GUIDE.md`, `docs/GLEA-WEATHER-PLAN.md`, `docs/GLEA-WEATHER-PLAN-REVISADO.md`, and this file.
- Jira scope: read and update only substantively affected descriptions among KAN-4–18; preserve statuses, parent links, dependencies, assignees, and unchecked implementation acceptance criteria. No new issues or transitions.
- Preserve all existing untracked/human-authored material and historical snapshots; no code, builds, API or database access, secrets, `.env`, staging, branch, push, or commit operations.
- Treat AD-T2-01–19 as approved design; AD-T2-20–22 are retired traceability references, not independent proposals or wholesale approvals. Preserve the separate user-approved AD-T2-14 amendment that derives coverage instead of storing its three summary columns.
- No Engram task mirror could be registered in this runtime; mark the mirror pending rather than inventing a session or claiming synchronization.

## Route and estimate

Delegated direct, one writer: broad reading across the guide and many Jira descriptions is the trigger. Advisory scope: remove approximately 450–650 net guide lines without formatting tricks; report the observed reduction. No commits because this is passive documentation/Jira synchronization.

## Tasks

- [x] **T01 — Prior alignment.** Preserve prior changes and historical hashes as previous evidence only; current hashes and line references must be recomputed.
- [x] **T02 — Simplify guide and record lean coverage policy.** Consolidated repeated state/integrity/timestamp constraints into one matrix at guide line 1060 and shortened repeated AD-T2 text while preserving all 22 primary entries and safeguards. Guide decreased from 3,446 to 2,909 lines (537 net lines removed, within the advisory 450–650 range). The 2026-10-19 amendment is recorded in AD-T2-14 at line 1943; proposed AD-T2-20–22 remain proposals except that narrow amendment.
- [x] **T03 — Align plans and Jira.** Both plans now state the same concise derived-coverage rule. Refreshed the three document hashes on KAN-4–18; substantively updated KAN-5, KAN-11 and KAN-12 only. KAN-11 remains En curso with four unchecked acceptance criteria; all issue statuses, parents, links and assignees matched the pre-edit snapshots.
- [x] **T04 — Verify.** Read back all three documents and KAN-4–18. Each AD-T2-01–22 primary heading occurs once; the normative matrix and amendment anchors resolve by source references; only the single AD-T2-14 amendment names the three `observed_*` columns, explicitly rejecting their storage. All 15 Jira issues contain all three final hashes and preserve workflow metadata. Passive-document structural checks only; no implementation behavior was tested.

## Acceptance and checks

- One normative state/integrity/timestamp matrix avoids repetition and preserves every specified status/time/idempotence/recovery boundary.
- Coverage summary columns are explicitly not stored in the initial schema; unknown and verified-empty remain distinct, and the exact database query stays a G2 question.
- Approved 01–19, retired/integrated references 20–22, and the dated narrow coverage amendment remain clearly distinct; T1/G1 closed, G2 contract and G5 physical evidence pending.
- Plans stay concise, consistent and linked; Jira KAN-11 is `En curso` with acceptance unchecked, all current hashes match, and workflow metadata is unchanged.
- Structural readback only; no runnable RED applies to passive documentation. No API, DB, weather acquisition, code/schema implementation, or runtime behavior is verified.

## Progress and next step

Completed the original alignment and the follow-up retirement on 2026-10-19 under explicit user authorization. Current SHA-256: guide 3274977F5494C33067DB8DCE7088F94F42286A990843667F70DD20162B859851; requested plan 7A8EE942D3BCF737565866EEAE287F5B6C6FCB40C93ED71E73F05635A4E48BDC; revised plan 96B97AD0A2384B5B9CFD6B8A1BDA798BC82608CD4E2BC94533BCCE0C36294C1A. Previous hashes above are historical, not current. Exact SQL/query and PostgreSQL proof remain pending G2; T3 acquisition and G5 physical AEMET evidence remain pending. No code, schema, database, API behavior, builds, or tests were changed/run. No Engram task mirror is available in this runtime and remains pending; no commit was created because this is passive documentation/Jira synchronization.

## Follow-up authorization (2026-10-19)

The user authorized retiring AD-T2-20–22 as independent architectural proposals, retaining their IDs only as a concise traceability mapping, and integrating only essential constraints into the existing design, matrix and G2 verification section. This does not approve those three proposals wholesale, purge historical snapshots, or authorize code/SQL/database changes.

- [x] **T05 — Retire and integrate AD-T2-20–22.** Removed the three long standalone records; preserved their former anchors as traceability aliases and their IDs in the compact map. Essential technical contract, recovery boundaries, and behavior-based G2 evidence now sit in §8.8, the normative matrix, and G2 verification text. No independent 20–22 proposal headings remain.
- [x] **T06 — Align plans and Jira.** Plans now state that the references were retired/integrated, not approved wholesale. Refreshed all three source hashes on KAN-4–18 and updated only relevant stale descriptions on KAN-5, KAN-11, and KAN-12.
- [x] **T07 — Verify.** Confirmed 0 primary AD-T2-20–22 headings, one normative matrix, three concise retired-ID map rows, current hashes on all 15 Jira issues, and unchanged statuses/parents/links/assignees. KAN-11 remains En curso with four unchecked criteria. Guide fell from 2,909 to 2,657 lines (252 net lines removed).

Route: single delegated writer due to broad guide/Jira read and multi-document synchronization. This is passive documentation/Jira work, so no commit is planned. Mirror remains unavailable in this runtime.
