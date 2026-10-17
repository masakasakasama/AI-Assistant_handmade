# DeepSeek V4.1 Flash 1017: Tatsu Home optional provider

Official API model ID: `deepseek-flash` (2026-10-17 update).

## Activation

The default configuration is **unchanged**. To opt in on the existing
Tatsu Home AI Backend (Vercel or Node), configure BOTH as server-side secrets:

- `TATSU_CHAT_PROVIDER=deepseek`
- `DEEPSEEK_API_KEY=<your actual DeepSeek API key>`

Deploy the updated backend. Android and Web keep using their existing
backend URL and authentication. No new access token or client-side key.

If either variable is missing, the default stays `current` (OpenAI).
The profile can also be explicitly tested using
`modelProfile: "deepseek"` in the existing authenticated dispatch endpoints.

## Separation

`Jev -> route` is unchanged. Android/Web continue handling SwitchBot,
alarms, and weather. For `simple_chat` and `deep_reasoning` only,
the DeepSeek provider receives the text of the current user question
and minimal general instructions. It does NOT receive the existing
`context` containing devices, alarms, history, location or other state.
No function-call/tool schemas are supplied. Explicit unsafe/private
patterns are rejected before outbound requests.

The legacy non-Jev router remains an OpenAI classifier; on selected
DeepSeek backend it uses DeepSeek only for conversational answers.
No physical action can be initiated by the answer model.

## Important limits

- The sensitive-text filter is heuristic: it cannot guarantee detection
  of identifying text or every private detail. Avoid asking questions
  containing secrets, health, finance, addresses or family details.
- The initial STT provider and Jev/OpenAI intent router are NOT changed.
  Sensitive context is still processed by the existing routing services
  according to their own data processing terms.
- DeepSeek processes the question under its own data handling rules.
  Do not enable DeepSeek for private household logs/voice diaries.
- This backend-only switch applies to both Android and Web. The
  existing UI has no provider toggle; enable/disable centrally on Vercel.
- Configure a spending cap and review API usage in the DeepSeek console.

## Verification

From the repository root: `cd ai-backend && npm test`.

No credentials are committed to GitHub or included in the APK.
