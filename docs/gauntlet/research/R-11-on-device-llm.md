# R-11 On-device LLM runtimes

Path: `docs/gauntlet/research/R-11-on-device-llm.md`. Task card: `coordination/tasks/cc-research-11.md`. Every claim carries its source URL and access date. Anything not confirmed by a source is marked UNVERIFIED. Access date for every source below is 2026-10-11 unless a different date is written next to it.

Status: DRAFT in progress. Sections are filled one at a time; a section still marked PENDING has not been researched yet.

## Question
Which on-device LLM runtimes can run on Android and Windows inside Command Library, with which model formats and sizes, at what performance, and how does each interoperate with the candidate stacks S1 to S6 (KMP + Compose Multiplatform, Flutter, .NET MAUI, Avalonia, React Native + RN Windows, Tauri 2)? Feeds GAUNTLET.md 2.H and the R-01 "on-device LLM interop" criterion.

## Findings

### F1. Runtimes

Method note: GitHub API and github.com via curl are blocked in this session, so GitHub facts come from raw.githubusercontent.com (primary text of READMEs and docs) and WebFetch of github.com pages (a summarizing fetch; treat counts and sizes from it as secondary to the repo itself). Package sizes were measured by HTTP HEAD and by reading ZIP central directories over HTTP range requests in memory (nothing written to disk).

#### F1.1 llama.cpp (ggml)
- License MIT, "Copyright (c) 2023-2026 The ggml authors". Source: https://raw.githubusercontent.com/ggml-org/llama.cpp/master/LICENSE (accessed 2026-10-11)
- Latest release at access time: tag b11552, published 2026-10-10 (GitHub marks every build as pre-release; llama.cpp ships continuous numbered builds, there is no semver "stable"). Source: https://github.com/ggml-org/llama.cpp/releases (accessed 2026-10-11, via WebFetch summary)
- Model format: GGUF only. Quantizations "1.5-bit, 2-bit, 3-bit, 4-bit, 5-bit, 6-bit, and 8-bit integer quantization". Source: https://raw.githubusercontent.com/ggml-org/llama.cpp/master/README.md (accessed 2026-10-11)
- Backends table (README): CUDA (Nvidia), HIP (AMD), Hexagon (Snapdragon), OpenCL (Adreno GPU), OpenVINO "[In Progress]" (Intel CPUs, GPUs, NPUs), SYCL (Intel GPU), Vulkan (GPU), WebGPU, plus CPU. Source: same README (accessed 2026-10-11)
- Release assets b11552 relevant here (sizes are whole tool bundles, including llama-server, llama-bench and so on, not the minimal library): Android arm64 CPU `llama-b11552-bin-android-arm64.tar.gz` 71.4 MB; Android arm64 Snapdragon (CPU, Adreno OpenCL, Hexagon NPU) 56.1 MB; Windows x64 CPU zip 18.6 MB; Windows x64 Vulkan 31.9 MB; Windows x64 CUDA 12.4 253 MB plus a 373 MB cudart zip; Windows x64 CUDA 13.4 146 MB plus 404 MB cudart; Windows x64 SYCL 142 MB; Windows x64 ROCm 10.0 246 MB; Windows x64 OpenVINO 85.5 MB; Windows arm64 CPU 11.8 MB; Windows arm64 OpenCL Adreno 12.6 MB; Windows arm64 Vulkan 24.8 MB; Windows arm64 CUDA 13.4 139 MB. Source: https://github.com/ggml-org/llama.cpp/releases/expanded_assets/b11552 (accessed 2026-10-11, via WebFetch summary)
- Android: official "GUI binding" in `examples/llama.android` (Kotlin facade `AiChat` / `InferenceEngine`, tokens collected in a Kotlin `Flow`, GGUF metadata reader). "This Android binding supports hardware acceleration up to `SME2` for **Arm** and `AMX` for **x86-64** CPUs", auto-detecting host features. It is an example project to import into Android Studio; no Maven coordinates are documented. NDK cross-compile uses `-DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28`. Source: https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/android.md (accessed 2026-10-11)
- Android GPU/NPU: OpenCL backend targets Qualcomm Adreno; verified Adreno 750, 810, 830, 840 (Snapdragon 8 Gen 3, 7s Gen 3, 8 Elite, 8 Elite Gen 5) and Windows 11 Arm64 on Snapdragon X Elite / X2 Elite (Adreno X1-85, X2-90). "A6x GPUs in phones are likely not supported due to the outdated driver and compiler." Source: https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/backend/OPENCL.md (accessed 2026-10-11)
- Hexagon NPU backend builds via the Qualcomm Snapdragon toolchain Docker images (Hexagon SDK 6.6.0.0) and installs per-generation HTP libraries `libggml-htp-v73/v75/v79/v81.so`; the doc covers Android and Windows-on-Snapdragon. Source: https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/backend/snapdragon/README.md (accessed 2026-10-11). The llama.rn README labels its Hexagon path "Experimental" and says "Qualcomm SM8450+ (8 gen 1 or newer) devices are currently supported & tested". Source: https://raw.githubusercontent.com/mybigday/llama.rn/main/README.md (accessed 2026-10-11)
- Vulkan on Android is flagged unsafe on some drivers by a downstream binding: Pixel 9 Pro (Mali-G715) wrong text for prompts over about 32 tokens in one batch; Galaxy A53 (Mali-G68) crashes while loading; Galaxy S24 (Adreno 750) crashes on first generation; "Keep `auto` or `cpu` unless Vulkan has been validated on your target devices." Source: https://llamadart.leehack.com/docs/platforms/support-matrix (accessed 2026-10-11)
- Streaming and structured output (llama-server): `stream: true` gives per-token SSE; `grammar` (GBNF) and `json_schema` constrain output; OpenAI-compatible `/v1/chat/completions` and `/v1/models`; `--api-key`. Tool calling: "adds support for OpenAI-style function calling ... `llama-server` when started w/ `--jinja`", native handlers for Llama 3.x, Hermes 2/3, Qwen 2.5, Mistral Nemo, Command R7B, GPT-OSS and others, with a "Generic" fallback for other templates. Sources: https://raw.githubusercontent.com/ggml-org/llama.cpp/master/tools/server/README.md and https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/function-calling.md (accessed 2026-10-11)
- Supported model families: not a closed set; the built-in chat template list includes gemma, llama2/3/4, phi3, phi4, qwen via chatml, mistral variants, granite 4.0/4.1, gpt-oss, deepseek, smolvlm and others. Source: llama-server README `--chat-template` row (accessed 2026-10-11)

#### F1.2 Google AI Edge: LiteRT-LM (and MediaPipe LLM Inference)
- LiteRT-LM is "Google's production-ready orchestration layer to run LLMs with LiteRT", license Apache-2.0, latest release v0.18.0 (Google Maven `lastUpdated` 2026-10-06). Kotlin API "Stable" ("Android apps & JVM"), Python "Stable", C++ "Stable", Swift and JavaScript "Early Preview", Flutter "Community". Sources: https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/README.md and https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml (accessed 2026-10-11)
- Platform and backend matrix (vendor-published): CPU on Android, iOS, macOS, Windows, Linux, IoT; GPU on Android, iOS, macOS, Windows, Linux; NPU on Android and Windows (the Windows NPU cell is marked with a rocket icon, no status label given). Source: https://developers.google.com/edge/litert-lm/overview (accessed 2026-10-11, via WebFetch summary)
- Model format `.litertlm`. Supported models and sizes (MB) per the overview page: Gemma4-E2B 2583; Gemma4-E4B 3654; Gemma-3n-E2B 2965; Gemma-3n-E4B 4235; Gemma3-1B 1005; FunctionGemma 289; phi-4-mini 3906; Qwen2.5-1.5B 1598; Qwen3-0.6B 586; Qwen2.5-0.5B 521. Source: same overview page (accessed 2026-10-11). The README also lists Llama, Phi-4 and Qwen families as supported.
- Kotlin API facts: Gradle `com.google.ai.edge.litertlm:litertlm-android` (Android) and `com.google.ai.edge.litertlm:litertlm-jvm` (desktop JVM; page says "JVM (Linux, macOS, Windows)"). Create `EngineConfig` with model path and backend (`Backend.CPU()`, `Backend.GPU()`, `Backend.NPU(nativeLibraryDir=...)`), `engine.initialize()` "can take a significant amount of time (e.g., up to 10 seconds)". Streaming: `sendMessageAsync(contents)` returns a `Flow<Message>`, plus a callback variant (`onMessage`, `onDone`, `onError`). Tool calling: `@Tool` / `@ToolParam` annotated `ToolSet`, or `OpenApiTool`; automatic or manual tool loop (`automaticToolCalling = false`); "Requires a model with tool support, such as FunctionGemma". Android GPU needs `uses-native-library` entries for `libvndksupport.so` and `libOpenCL.so`. Structured output is not covered on that page. Source: https://developers.google.com/edge/litert-lm/android (accessed 2026-10-11, via WebFetch summary)
- Measured artifact facts, version 0.18.0 (Google Maven, HEAD plus ZIP directory read): `litertlm-android-0.18.0.aar` is 20,905,807 bytes and holds `jni/arm64-v8a/liblitertlm_jni.so` (22,155,680 bytes uncompressed, 9,706,385 compressed) and `jni/x86_64/liblitertlm_jni.so` (26,353,160 uncompressed); there is no armeabi-v7a. `litertlm-jvm-0.18.0.jar` is 131,540,571 bytes and holds native JNI libraries for `windows-x86_64` (`litertlm_jni.dll`, 50,169,344 bytes uncompressed, 17,703,467 compressed), `darwin-aarch64`, `linux-aarch64` and `linux-x86_64`; there is NO `windows-aarch64` entry. JVM POM dependencies: gson 2.14.0, kotlin-reflect 2.4.0, kotlinx-coroutines-core-jvm 1.11.0. Sources: https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/0.18.0/litertlm-android-0.18.0.aar and https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-jvm/0.18.0/litertlm-jvm-0.18.0.jar (accessed 2026-10-11)
- Windows GPU backend is Direct3D 12 (not CUDA); the LiteRT-LM native bundle for Windows ships `dxil.dll` and `dxcompiler.dll`; "Windows arm64 has no LiteRT-LM runtime." Source (third-party binding docs, secondary): https://llamadart.leehack.com/docs/platforms/support-matrix (accessed 2026-10-11). Consistent with the missing `windows-aarch64` native in the official JVM jar (measured above).
- CLI: `litert-lm run --from-huggingface-repo=...`, `litert-lm import`, `litert-lm serve` (OpenAI-compatible server, example endpoint `http://localhost:9379/v1/chat/completions`). Sources: https://raw.githubusercontent.com/google-ai-edge/LiteRT-LM/main/README.md and https://developers.googleblog.com/bringing-gemma-4-12b-to-your-laptop-unlocking-local-agentic-workflows-with-google-ai-edge/ (accessed 2026-10-11)
- MediaPipe LLM Inference API (`com.google.mediapipe:tasks-genai`, `.task` models): status vs LiteRT-LM not yet checked, see F1.5. flutter_edge_ai lists MediaPipe `.task` as Android, iOS and Web only (secondary, https://pub.dev/packages/flutter_edge_ai, accessed 2026-10-11).

#### F1.3 ONNX Runtime GenAI (Microsoft)
- Latest NuGet `Microsoft.ML.OnnxRuntimeGenAI` 0.17.1 published 2026-09-29; 0.17.0 2026-09-25; 0.16.0 2026-09-13 (NuGet dates). GitHub release notes give 28 Sep for 0.17.0 and 22 Sep for 0.16.0, so NuGet and GitHub dates differ; UNVERIFIED which is the tag date. Sources: https://api.nuget.org/v3/registration5-gz-semver2/microsoft.ml.onnxruntimegenai/index.json and https://github.com/microsoft/onnxruntime-genai/releases (WebFetch summary), accessed 2026-10-11
- License: the NuGet nuspec points to a `LICENSE` file inside the package; the SPDX identifier was not read. UNVERIFIED (tried: nuspec only). Source: https://api.nuget.org/v3-flatcontainer/microsoft.ml.onnxruntimegenai/0.17.1/microsoft.ml.onnxruntimegenai.nuspec (accessed 2026-10-11)
- Support matrix from the README: OS Linux, Windows, Mac, Android; architectures x86, x64, arm64; APIs Python, C#, C/C++, "Java ^" where "^ Requires build from source"; hardware acceleration CPU, CUDA, DirectML, NvTensorRtRtx, OpenVINO, QNN, WebGPU; features Multi-LoRA, continuous decoding, constrained decoding. Model architectures include AMD OLMo, ChatGLM, DeepSeek, ERNIE 4.5, Gemma, gpt-oss, Granite, LFM2, Llama, Mistral, Nemotron, Phi, Qwen, SmolLM3, Whisper. "ONNX Runtime GenAI powers Foundry Local, Windows ML, and the Visual Studio Code AI Toolkit." Source: https://raw.githubusercontent.com/microsoft/onnxruntime-genai/main/README.md (accessed 2026-10-11)
- Tool calling and structured output: README says it implements "grammar specification for tool calling"; v0.16.0 notes: "The Engine can generate tool calls and enforce structured output with guidance." Source: README above and https://github.com/microsoft/onnxruntime-genai/releases (WebFetch summary, accessed 2026-10-11)
- Java API: "Package publication is pending", no Maven coordinates, API "in preview". Maven Central has no `onnxruntime-genai-android` metadata (empty response). Sources: https://onnxruntime.ai/docs/genai/api/java.html (WebFetch summary) and https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-genai-android/maven-metadata.xml, accessed 2026-10-11
- Measured NuGet package facts (0.17.1): `Microsoft.ML.OnnxRuntimeGenAI` nupkg is 112,268,220 bytes and carries `runtimes/win-x64/native/onnxruntime-genai.dll` (8,645,944 bytes), `runtimes/win-arm64/native/onnxruntime-genai.dll` (8,444,216), linux-x64, linux-arm64, osx-arm64, an iOS xcframework zip and `runtimes/android/native/onnxruntime-genai.aar` (21,518,898 bytes). It has a `net9.0-android31.0` target group, so .NET for Android and MAUI Android is a first-class target. It depends on `Microsoft.ML.OnnxRuntime` 1.30.0 (ORT core, a separate package, size not counted here) and `Microsoft.ML.OnnxRuntimeGenAI.Managed` 0.17.1 (293,396 bytes). The inner AAR holds `classes.jar` (48,661 bytes, a Java API) and `jni/arm64-v8a/` with `libonnxruntime-genai.so` 6,692,904 bytes, `libonnxruntime-genai-jni.so` 2,015,176 bytes and `libmat.so` 28,597,536 bytes (purpose of libmat UNVERIFIED), plus x86_64. Other packages: `.WinML` 13,780,883 bytes; `.Cuda` 30,864,025; `.Foundry` 34,813,522; `.DirectML` last version 0.14.1 (4,931,998 bytes, not updated since); `.QNN` last version 0.13.2 (2,353,762 bytes). Sources: https://api.nuget.org/v3-flatcontainer/microsoft.ml.onnxruntimegenai/0.17.1/microsoft.ml.onnxruntimegenai.0.17.1.nupkg and sibling nuspec URLs (accessed 2026-10-11)
- Using the AAR inside the NuGet package from a Gradle project as a local file dependency is technically possible (it is a standard AAR) but Microsoft documents no such path. UNVERIFIED as a supported configuration.

### F1.4 to F1.7 (MLC LLM, ExecuTorch, MediaPipe status, others)
PENDING

### F2. Windows-native options
PENDING

### F3. Model candidates and licenses
PENDING

### F4. Published performance figures
PENDING

### F5. Interop per stack
PENDING

### F6. Packaging cost
PENDING

### F7. Model download manager facts
PENDING

## Options
PENDING

## Recommendation
PENDING

## Risks
PENDING

## Confidence
PENDING
