# Local observation timing display (debug-only draft)

Based on profiler commit d4cba3b0c2ac84082471e8740430a13b4f6d7ddb. The original detailed Logcat entry remains.
This draft uses the existing app-local server log repository, with one compact numeric
SERVER entry per profiled fresh screen/find invocation. No new MCP tool, external
endpoint, permission, screenshot, UI text, request ID, caller ID or exception message.
The compact message uses integer milliseconds (truncation below 1ms), fixed stage names,
and omits per-stage counts. The existing persistent log retention and UI apply.
The server log store limits entry data to 500 bytes; the added test checks every stage
with deliberately large elapsed durations. The local log queue is asynchronous; delivery
to storage is not guaranteed merely by a returned request. Existing full Logs page must
be verified on-device; no change to its rendering is proposed.

Only debug builds and the two unprefixed observation tool registrations attach the
internal coroutine context element. Existing caller context is retained. No release
profiling, raw content logging or change to the privacy gate. Existing cancellation and
authorization regressions, debug unit tests and full Android compile must pass before
installation. This draft is independent of the running pinned build; it does not alter it.
No device runtime result or build success is claimed yet.
