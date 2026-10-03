// Do not stringify Error/cause/request/provider responses: they may contain keys,
// transcripts, prompts, audio or headers. Stable event names only.
export function logRequestFailure() {
  console.error(JSON.stringify({event:'backend_request_failed'}));
}
