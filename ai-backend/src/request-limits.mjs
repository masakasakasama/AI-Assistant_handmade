// A shared atomic store is required: process memory and Vercel tmp files reset.
export const CLAIM_SCRIPT = `
local daily = tonumber(redis.call('GET', KEYS[1]) or '0')
local minute = tonumber(redis.call('GET', KEYS[2]) or '0')
if daily >= tonumber(ARGV[1]) then return {0, 1} end
if minute >= tonumber(ARGV[2]) then return {0, 2} end
if redis.call('INCR', KEYS[1]) == 1 then redis.call('EXPIRE', KEYS[1], 172800) end
if redis.call('INCR', KEYS[2]) == 1 then redis.call('EXPIRE', KEYS[2], 120) end
return {1, 0}
`;
export class RequestLimitError extends Error {
  constructor(code, status, retryAfterSeconds) {
    super(code);Object.assign(this,{code,status,retryAfterSeconds});
  }
}
export function requestLimitFailure(error) {
  if (!(error instanceof RequestLimitError)) return null;
  return {status:error.status,body:{error:error.code,message:'Backendの利用制限により、このリクエストを実行できません。',...(error.retryAfterSeconds?{retryAfterSeconds:error.retryAfterSeconds}:{})}};
}
export async function claimRequest({env=process.env,fetchImpl=fetch,now=Date.now()}={}) {
  const daily=Number(env.AI_DAILY_REQUEST_LIMIT), minute=Number(env.AI_RATE_REQUEST_LIMIT);
  let endpoint;
  try {endpoint=new URL(env.AI_LIMIT_REDIS_URL);} catch {}
  if (!endpoint || endpoint.protocol!=='https:' || endpoint.username || endpoint.password || !env.AI_LIMIT_REDIS_TOKEN
    || !Number.isSafeInteger(daily) || daily<1 || !Number.isSafeInteger(minute) || minute<1) {
    throw new RequestLimitError('usage_limits_not_configured',503);
  }
  const date=new Date(now).toISOString().slice(0,10), bucket=Math.floor(now/60000);
  // One owner/application-wide namespace, independent of auth token rotation.
  const prefix='tatsu-home:requests:v1:';
  let result;
  try {
    const response=await fetchImpl(endpoint,{method:'POST',headers:{Authorization:`Bearer ${env.AI_LIMIT_REDIS_TOKEN}`,'Content-Type':'application/json'},
      body:JSON.stringify(['EVAL',CLAIM_SCRIPT,'2',prefix+'day:'+date,prefix+'minute:'+bucket,String(daily),String(minute)]),signal:AbortSignal.timeout(2000)});
    if(!response.ok)throw new Error('store unavailable');
    result=(await response.json()).result;
    if(!Array.isArray(result) || result.length!==2 || !((result[0]===1 && result[1]===0) || (result[0]===0 && [1,2].includes(result[1]))))throw new Error('invalid store result');
  } catch {throw new RequestLimitError('usage_limits_unavailable',503);}
  if(result[0]===0) {
    const dailyDenied=result[1]===1;
    throw new RequestLimitError(dailyDenied?'daily_request_limit':'request_rate_limit',429,
      dailyDenied?Math.ceil((Date.parse(date+'T00:00:00Z')+86400000-now)/1000):60-Math.floor(now/1000)%60);
  }
}
