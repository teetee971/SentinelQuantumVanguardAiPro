# Local-only inference bridge — experimental, not Android-qualified

Budget: zero USD. No cloud API, API key, paid service or generated canned answer.
Runtime: llama.cpp (MIT), pinned commit e8fbcad855c5d8c315aa1dde231836ae472f5779.
Model: Qwen/Qwen2.5-0.5B-Instruct-GGUF (Apache-2.0), revision
9217f5db79a29953eb74d5343926648285ec7e67, qwen2.5-0.5b-instruct-q4_k_m.gguf.
SHA-256: 74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db.
Check the upstream license and notices before redistributing runtime or weights.
Weights and binaries are not committed or embedded in the APK.

Build llama.cpp on the target ARM64 machine with CMake, GGML_NATIVE=OFF,
GGML_OPENMP=OFF, LLAMA_BUILD_SERVER=OFF and LLAMA_CURL=OFF; build target
llama-completion. Install requirements in an isolated virtual environment.
Launch service.py with --binary PATH --model PATH --pairing-file NEW_PRIVATE_PATH.
It binds only 127.0.0.1:18080. The pairing file is created exclusively with mode0600;
never commit, log, export or include it in a delivery archive.

The authenticated challenge is valid for five seconds and consumed once. Request
and response MACs bind exact UTF-8 bodies to the challenge. The engine receives
only typed text, never authority/approval/execution objects. Recommendations are
strictly limited to NONE or CREATE_AUDIT_CHECKPOINT; this service executes neither.
Prompts travel to the child via stdin rather than process arguments. Cancelled
children are killed and reaped. Process runtime is capped at30 seconds.

Observed x86_64 smoke: genuine model returned 2+2=4 through the authenticated
service; replay returned409. This proves only that synthetic smoke, not model
reliability or Android integration. A smaller SmolLM2 trial failed this question.
Qwen used about594MiB peak RSS in an earlier CLI smoke; ARM64 memory, latency,
battery and thermal behaviour remain unmeasured.

BLOCKERS: no Android client, pairing UI, authenticated human decision flow,
production action adapter or persistent Android audit journal is wired to this
bridge. No S24+ inference was executed. The APK does not contain this engine.
Do not expose this experimental HTTP server beyond loopback. Pairing, lifecycle,
loss of service, cancellation, replay, model output validation and human approval
must be verified on S24+ before enabling any action. No production readiness claim.
