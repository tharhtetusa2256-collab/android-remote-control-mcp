# Progressive observation — isolated synthetic pilot

This experiment is intentionally outside the Android Gradle source sets. It uses only invented
field costs, a synthetic snapshot identity and simulated receipts. It does not process UI text,
read private app data, add an MCP tool, emit pages, alter the model, change privacy settings,
or replace the production fail-closed path. No early release is implemented.

Baseline: v1.13.0 commit ed77c267c76b54826b9a033301ac68f111ca079e. WindowPacker already
packs short fields using a 254-content-token target and flags fields at the 1536-token model
limit. OrtPiiModelRunner rejects the entire inference if a field is truncated; RedactionEngine
collects and redacts all fields before screen output pagination. This pilot does not change that.

Run from this directory with Kotlin/JVM:
```sh
kotlinc ProgressiveReadPilot.kt ProgressiveReadPilotTest.kt -include-runtime -d /tmp/arc-progressive-pilot.jar
java -jar /tmp/arc-progressive-pilot.jar
```

Local isolated checks: 10/10 passed. Git blob IDs of tested files:
79be0f444ed6030b0db32faba93827a8e614dd03 (pilot)
c037cc55e6c6c46c754222a842ea2198450db209 (checks).

The planner keeps whole fields and uses synthetic costs plus estimated separators, not a
real token guarantee. Oversized fields are rejected, not truncated or disclosed. Receipts
are ordered; failure, duplicate, cancellation and snapshot mismatch latch stop. A boolean
receipt is only a mock, not trusted evidence of privacy analysis. There is no concurrency,
durable recovery, authorization, actual screen identity or privacy-policy binding yet.

Next gate: finish exact-head profiler build and measure runtime stages; evaluate real tokenizer
and model with synthetic multilingual fixtures, full-field context, boundary cases and
equivalence against existing privacy behavior. Do not weaken protection or release unverified
content. Verify trusted snapshot/policy binding and cancellation before any live integration.
Actual first-page latency, detection accuracy and Android compatibility remain unmeasured.
