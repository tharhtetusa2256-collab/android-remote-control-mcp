# ARC observation timing (debug-only pilot)

Base: v1.13.0 source commit ed77c267c76b54826b9a033301ac68f111ca079e.

The fresh screen and find handlers emit one bounded metadata-only debug Logcat line under
`MCP:ObservationTiming` when they return or unwind. No new MCP tools, permissions, UI text,
request arguments, node identifiers, package names, tokens or exception messages are logged.
Release builds take the original execution path with a null timer. Existing authorization,
privacy processing, accessibility lock, and action semantics are unchanged by design.

Stage totals are elapsed monotonic nanoseconds, summed across repeated calls, with call counts.
Missing stages are not reached; total includes uninstrumented work. RETURNED means the handler
returned, not that the client received a response. CANCELLED is observed when a cancellation
exception unwinds; it does not measure cancellation request arrival or transport completion.
The cursor pagination path and the rest of the tool set are not profiled. Screenshot stage is
aggregate. A request stuck indefinitely has no final summary. Logging is local debug Logcat;
no new external access or permission is granted. No correlation to client request IDs yet.

Acceptance: build and run unit tests on exact commit; review existing screen/Find/privacy tests;
verify debug log contains fixed fields only; compare matched and unmatched find with fresh screen
on the same authorized app, with privacy settings unchanged. Do not infer causality from stage
timing alone. Do not install, deploy, expose logs or change privacy settings without authorization.
