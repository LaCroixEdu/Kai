# OpenRouter reasoning_details protocol fix

## Goal

Produce a sideloadable Kai Android APK in which OpenRouter reasoning-capable models can complete an assistant tool call, receive its tool result, and continue without losing the provider's opaque `reasoning_details[]` state or returning HTTP 400.

## Required behavior

- Base all work on `SimonSchubert/Kai` commit `03f8043cd557287c799aabaa310c3aed93d53e1a`.
- Work only on `LaCroixEdu/Kai:fix/openrouter-reasoning-details`; do not modify the stale fork `main`.
- Deserialize OpenRouter `reasoning_details` without assigning a provider-specific schema.
- Preserve the exact JSON value through the OpenAI-compatible response, tool-loop history, persisted conversation, and next OpenRouter assistant message.
- Emit `reasoning_details` only for OpenRouter. Providers using `ReasoningRequestMode.NONE` or `REASONING_CONTENT` must keep their existing payload shape.
- Preserve DeepSeek and other existing `reasoning_content` behavior.
- Treat optional root-level `reasoning` request controls as a separate follow-up; do not add them unless tests show they are needed.
- Build an unsigned/developer-signed FOSS debug APK in GitHub Actions without repository secrets.
- Never commit API keys, signing secrets, exported Kai settings, or personal credentials.

## Acceptance criteria

1. A literal OpenRouter assistant tool-call response containing opaque `reasoning_details[]` can be decoded and re-encoded on the next assistant history message byte-for-JSON-value unchanged.
2. Non-OpenRouter modes omit `reasoning_details`.
3. `reasoning_content` tests remain green.
4. Conversation JSON save/load retains `reasoning_details`, while old JSON without the field still loads with `null`.
5. Focused tests and the full desktop test suite pass.
6. `:androidApp:assembleFossDebug` succeeds on GitHub Actions and uploads `androidApp-foss-debug.apk`.
7. The APK's package/signature implications are reported before installation guidance is given.

