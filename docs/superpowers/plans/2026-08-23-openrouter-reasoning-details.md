# OpenRouter Reasoning Details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve OpenRouter `reasoning_details[]` through Kai's agentic tool loop and produce a tested Android debug APK.

**Architecture:** Keep the OpenAI-compatible payload opaque by storing it as `JsonArray?`. Extend provider gating with an OpenRouter-only mode that emits both the existing reasoning string and the opaque details, then thread the field through response DTO, loop result, `History`, and persisted `Conversation.Message`. Persist the minimal tool-call envelope and tool-result IDs alongside the details so a restored conversation can reconstruct the original OpenAI-compatible message sequence without changing other providers.

**Tech Stack:** Kotlin Multiplatform, kotlinx.serialization JSON, Compose, Gradle 9/AGP 9.3.1, JDK 21, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-23-openrouter-reasoning-details.md`

## Global Constraints

- Base branch commit: `03f8043cd557287c799aabaa310c3aed93d53e1a`.
- Target branch: `fix/openrouter-reasoning-details` in `LaCroixEdu/Kai`.
- Do not update or force-reset fork `main`.
- Preserve `reasoning_details` as opaque JSON; do not model provider-specific members.
- Emit the field only for OpenRouter; strict providers must remain unchanged.
- Preserve existing `reasoning_content` behavior and tests.
- No API keys or signing secrets in source or Actions.

---

### Task 1: Establish regression CI and reproduce the protocol gap

**Files:**
- Create: `.github/workflows/openrouter-android-debug.yml`
- Create: `composeApp/src/commonTest/kotlin/com/inspiredandroid/kai/ui/chat/OpenRouterReasoningDetailsRoundTripTest.kt`

**Interfaces:**
- Consumes: existing `OpenAICompatibleChatResponseDto`, `History.toGroqMessageDto`, and `ReasoningRequestMode`.
- Produces: a failing behavioral test that requires `reasoningDetails: JsonArray?` and an OpenRouter-only request mode.

- [ ] **Step 1: Add branch CI**

Create a workflow triggered by pushes to `fix/openrouter-reasoning-details` and manual dispatch. It must set up JDK 21 and Android SDK, run `:composeApp:desktopTest`, assemble `:androidApp:assembleFossDebug`, and upload `androidApp/build/outputs/apk/foss/debug/androidApp-foss-debug.apk` as `kai-openrouter-debug-apk`.

- [ ] **Step 2: Write the failing round-trip test**

Decode a literal response containing an assistant `tool_calls` entry and two opaque `reasoning_details` objects. Construct the corresponding `History` assistant entry and encode the next request message. Assert the encoded `reasoning_details` equals the independently parsed literal array.

- [ ] **Step 3: Add the provider-gating assertion**

Encode the same `History` entry with `ReasoningRequestMode.NONE` and assert the serialized JSON object does not contain `reasoning_details`.

- [ ] **Step 4: Push only tests and workflow**

Expected CI result: FAIL during Kotlin compilation because `reasoningDetails` and the OpenRouter-specific enum mode do not exist. This is the required RED evidence.

### Task 2: Add opaque wire fields and provider gating

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/network/dtos/openaicompatible/OpenAICompatibleChatResponseDto.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/network/dtos/openaicompatible/OpenAICompatibleChatRequestDto.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/Service.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/ui/chat/ChatUiState.kt`

**Interfaces:**
- Produces: `Message.reasoningDetails: JsonArray?`, `History.reasoningDetails: JsonArray?`, and `ReasoningRequestMode.REASONING_CONTENT_AND_DETAILS`.
- Consumes: opaque arrays returned by OpenRouter; no knowledge of their member schema.

- [ ] **Step 1: Deserialize the response field**

Add `@SerialName("reasoning_details") val reasoningDetails: JsonArray? = null` to the OpenAI-compatible response message.

- [ ] **Step 2: Add the request field**

Add the same serialized field to `OpenAICompatibleChatRequestDto.Message`.

- [ ] **Step 3: Add provider mode**

Add `REASONING_CONTENT_AND_DETAILS` to `ReasoningRequestMode`, set only `Service.OpenRouter` to it, and retain all other service assignments.

- [ ] **Step 4: Thread through History and mapper**

Add `reasoningDetails: JsonArray? = null` to `History`. For assistant tool-call messages, emit the string for both reasoning-enabled modes, emit details only for `REASONING_CONTENT_AND_DETAILS`, and emit neither in `NONE`.

- [ ] **Step 5: Run CI**

The round-trip and gating tests may still fail until the tool-loop mapping is completed in Task 3; compilation must succeed.

### Task 3: Preserve details through the live tool loop

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/RemoteDataRepository.kt`

**Interfaces:**
- Consumes: `OpenAICompatibleChatResponseDto.Choice.Message.reasoningDetails`.
- Produces: `LoopChatResult.reasoningDetails` and `History.reasoningDetails` on the exact assistant tool-call turn.

- [ ] **Step 1: Extend the loop result**

Add `val reasoningDetails: JsonArray? = null` to `LoopChatResult`.

- [ ] **Step 2: Capture response details**

In `handleOpenAICompatibleChatWithTools`, copy `message.reasoningDetails` into the returned `LoopChatResult` without transformation.

- [ ] **Step 3: Store the assistant tool-call turn**

In `runToolLoop`, copy `result.reasoningDetails` into the new assistant `History` entry before tool execution.

- [ ] **Step 4: Run focused and full tests**

Run `./gradlew :composeApp:desktopTest --tests '*OpenRouterReasoningDetailsRoundTripTest' --no-daemon --stacktrace`, then `./gradlew :composeApp:desktopTest --no-daemon --stacktrace`. Expected: PASS.

### Task 4: Preserve details in conversation storage

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/Conversation.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/inspiredandroid/kai/data/RemoteDataRepository.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/inspiredandroid/kai/data/ConversationSerializationTest.kt`

**Interfaces:**
- Consumes: `History.reasoningDetails`.
- Produces: backward-compatible `Conversation.Message.reasoningDetails: JsonArray?`, persisted assistant tool calls, and persisted tool-result IDs.

- [ ] **Step 1: Write persistence tests first**

Add one test that serializes and deserializes an assistant tool-call message containing an opaque two-element array and asserts exact equality for the array and tool-call envelope. Include its matching tool result and assert `toolCallId`/`toolName` survive. Add one legacy-input assertion that missing new fields decodes as `null`.

- [ ] **Step 2: Verify RED**

Push the tests alone. Expected CI result: FAIL because `Conversation.Message.reasoningDetails`, `toolCalls`, and `toolCallId` do not exist.

- [ ] **Step 3: Add backward-compatible storage**

Add a serializable `Conversation.ToolCall` value with `id`, `name`, and `arguments`. Add optional `toolCallId`, `toolName`, `toolCalls`, and `@EncodeDefault(EncodeDefault.Mode.NEVER) val reasoningDetails: JsonArray? = null` fields to `Conversation.Message`. Defaults must keep legacy JSON compatible.

- [ ] **Step 4: Map save and load**

Copy `History.reasoningDetails`, `toolCallId`, `toolName`, and mapped tool calls into `Conversation.Message` in `saveCurrentConversation`; restore the same values and rebuild `ToolCallInfo` entries in `loadConversation`.

- [ ] **Step 5: Verify GREEN**

Run the focused persistence test, then build OpenAI messages from the restored assistant/tool pair and assert it still contains the exact `reasoning_details`. Run the full desktop test suite. Expected: PASS.

### Task 5: Update feature documentation and regression matrix

**Files:**
- Modify: `docs/features/reasoning.md`
- Modify: `composeApp/src/commonTest/kotlin/com/inspiredandroid/kai/ui/chat/ToGroqMessageDtoReasoningTest.kt`

**Interfaces:**
- Consumes: finalized provider-mode behavior.
- Produces: documented OpenRouter support and explicit DeepSeek/strict-provider regression coverage.

- [ ] **Step 1: Add mode-specific tests**

Assert `REASONING_CONTENT` still emits the string but strips details, `REASONING_CONTENT_AND_DETAILS` emits both, and `NONE` emits neither.

- [ ] **Step 2: Update docs**

Set `Last verified` to `2026-08-23`, describe exact OpenRouter opaque round-tripping, remove the completed item from Known gaps, and keep root-level reasoning options listed as future work.

- [ ] **Step 3: Run full test suite**

Run `./gradlew :composeApp:desktopTest --no-daemon --stacktrace`. Expected: PASS with no test failures.

### Task 6: Build and inspect the Android artifact

**Files:**
- Verify: `.github/workflows/openrouter-android-debug.yml`
- Verify output: `androidApp/build/outputs/apk/foss/debug/androidApp-foss-debug.apk`

**Interfaces:**
- Produces: downloadable GitHub Actions artifact `kai-openrouter-debug-apk`.

- [ ] **Step 1: Inspect successful workflow jobs and logs**

Confirm tests and `:androidApp:assembleFossDebug` both exit successfully. Fix only reproducible branch-specific failures.

- [ ] **Step 2: Download artifact**

Download the Actions ZIP and verify it contains a non-empty APK at the expected path/name.

- [ ] **Step 3: Report installation compatibility**

The debug APK uses application ID `com.inspiredandroid.kai` and a debug signing certificate. It normally cannot update an official APK signed with the maintainer's release key. Report that the official app may need export/uninstall/import, unless later inspection establishes a compatible signature; do not change the application ID in this patch.

- [ ] **Step 4: Final verification**

Review the branch diff for unrelated changes and secrets, then map every acceptance criterion to fresh test/Actions evidence.
