// Keep upstream bodies and messages out of errors, responses and logs: an auth
// failure can contain part of an API key. Only finite, allowlisted facts survive.
const labels={credential_expired:'認証情報の期限が切れています',authentication_failed:'API認証が拒否されています',quota_exceeded:'残高または利用枠が不足しています',permission_denied:'利用権限がありません',model_not_found:'指定モデルを利用できません',rate_limited:'呼び出し回数の上限に達しています',invalid_request:'APIへの要求が拒否されています',unavailable:'APIサービスが応答できません',invalid_response:'APIの応答を処理できません',provider_error:'API呼び出しに失敗しました'};
export class ProviderError extends Error {
  constructor(provider,status,reason){
    const safeProvider=['openai','jev'].includes(provider)?provider:'openai';
    const safeReason=Object.hasOwn(labels,reason)?reason:'provider_error';
    super(`${safeProvider==='openai'?'OpenAI':'Jev'}: ${labels[safeReason]}`);
    this.name='ProviderError';this.provider=safeProvider;this.status=Number.isInteger(status)&&status>=100&&status<=599?status:null;this.reason=safeReason;
  }
}
export function providerError(provider,status,body={}){
  const detail=body?.error??body??{};
  const code=typeof detail.code==='string'?detail.code:'';
  const type=typeof detail.type==='string'?detail.type:'';
  const message=typeof detail.message==='string'?detail.message:'';
  const reason=/^(?:api_key_expired|expired_api_key|token_expired|expired_token)$/.test(code)||/(?:api[ _-]?key|token).{0,50}expir|expir.{0,50}(?:api[ _-]?key|token)/i.test(message)?'credential_expired':
    /^(?:insufficient_quota|quota_exceeded|billing_hard_limit_reached|billing_not_active)$/.test(code)||type==='insufficient_quota'?'quota_exceeded':
    code==='model_not_found'||status===404?'model_not_found':status===401?'authentication_failed':status===402?'quota_exceeded':status===403?'permission_denied':status===429?'rate_limited':status===400?'invalid_request':status>=500?'unavailable':'provider_error';
  return new ProviderError(provider,status,reason);
}
export function providerFailure(error){
  if(!(error instanceof ProviderError))return null;
  return {status:502,body:{error:'upstream_'+error.reason,message:error.message,provider:error.provider,upstreamStatus:error.status}};
}
