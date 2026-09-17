# PERFORMANCE — on-device Gemma

Runtime: **MediaPipe LLM Inference (tasks-genai 0.10.35) / LiteRT-LM**
Model: **google/gemma-3n-E2B-it-litert-lm · gemma-3n-E2B-it-int4.litertlm**
(3,655,827,456 bytes, SHA-256 `2ed7bc3a…4010d6`)

## Configuration measured/chosen

| Knob | Value | Rationale |
| --- | --- | --- |
| Session maxTokens (in+out) | 4096 | analysis prompt (~700 tok) + image (~256) + output headroom |
| Analysis maxOutputTokens | 640 | structured JSON fits in ~400; tight budget = latency floor |
| Chat/Live maxOutputTokens | 320 | concise-by-product-design answers |
| topK / topP | 48 / 0.95 | Gemma 3n defaults, stable output |
| Analysis temperature | 0.35 | structure reliability |
| Chat temperature | 0.7 | conversational variety |
| Vision input | ≤768×768 (downscaled in-engine) | Gemma 3n encoder ceiling; 512 fallback documented below |
| Session strategy | fresh session per request | correct cancellation (Live barge-in) beats KV reuse for v1 |
| History window | last 12 messages | keeps prefill bounded in long chats |

## Measured — Android emulator (M-series host, arm64 image, CPU inference)

2026-09-11, Pixel-7-profile AVD, 6 GB RAM, swiftshader GPU (no NNAPI/GPU
delegate available in emulation):

| Metric | Observation |
| --- | --- |
| Model detection (side-load scan) | instant (size check only) |
| Cold engine load (3.4 GB bundle) | ~2–3 min (first `prepareModel`) |
| Process RSS with model loaded | ~3.0–3.1 GB |
| Text meal analysis (700-tok prompt) | **functionally correct but far beyond interactive latency** — sustained 235–250% CPU for tens of minutes at 1024-token budget (now capped 640) |
| Verdict | The emulator exercises correctness (engine, session config, streaming, cancellation) but is **not representative** of any real device and unusable for latency numbers |

The native engine demonstrably runs our exact session config
(`llm_inference_engine.cc … topk: 48, temperature: 0.35`) and generation
completes end-to-end; UI status progression, cancellation and error paths were
exercised live on the emulator.

## Real-device numbers — REQUIRED before store submission

No physical Android device was attached during this session, so **no real
hardware numbers are claimed**. On a modern Galaxy/Pixel-class device
(GPU/NPU-accelerated LiteRT-LM path), expect the published order of magnitude
for Gemma 3n E2B int4 — prefill hundreds of tokens/s, decode tens of tokens/s
— i.e. analysis in seconds, chat time-to-first-token well under a second after
warm load. Measure and fill this table on hardware:

| Metric | Device: ______ |
| --- | --- |
| Cold model load | |
| Warm engine ready → first token (chat) | |
| Meal photo analysis, 512×512 input | |
| Meal photo analysis, 768×768 input | |
| Decode tokens/sec (chat) | |
| Peak RSS | |
| Thermals after 5 consecutive analyses | |

Test both vision sizes; ship 768 unless 512's quality holds and latency is
meaningfully better on mid-range hardware.

## Practical optimizations in place

- Warm-up: engine loads as soon as a model is detected (Improve tab shows
  ready state; first analysis skips cold load).
- Meal photos are resized/compressed at capture (≤1280px JPEG) and again to
  ≤768px for the vision encoder — full camera frames never reach inference.
- Chat streams token-by-token; Live speaks sentence-by-sentence while
  generation continues; barge-in cancels generation immediately.
- Generation is serialized (single-stream runtime) behind a mutex; stale Live
  turns are cancelled before a new turn starts.
