import {ProviderError} from './provider-error.mjs';
// Do not stringify Error/cause/request/provider responses: they may contain keys,
// transcripts, prompts, audio or headers. Stable event names only.
export function logRequestFailure(error) {
  const facts=error instanceof ProviderError?{provider:error.provider,upstreamStatus:error.status,reason:error.reason}:{};
  console.error(JSON.stringify({event:'backend_request_failed',...facts}));
}
