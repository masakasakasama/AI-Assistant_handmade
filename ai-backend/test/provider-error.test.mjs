import test from 'node:test';
import assert from 'node:assert/strict';
import {providerError} from '../src/provider-error.mjs';
import {apiError,createResponse} from '../src/openai.mjs';
import {logRequestFailure} from '../src/safe-logging.mjs';
for(const [status,code,message,reason] of [[401,'invalid_api_key','Incorrect API key provided: fixture-secret','authentication_failed'],[401,'api_key_expired','key fixture-secret','credential_expired'],[401,'invalid_api_key','Your API key has expired: fixture-secret','credential_expired'],[429,'insufficient_quota','fixture-secret','quota_exceeded'],[404,'model_not_found','fixture-secret','model_not_found'],[403,'permission_error','fixture-secret','permission_denied'],[429,'rate_limit_exceeded','fixture-secret','rate_limited'],[503,'server_error','fixture-secret','unavailable']])test('safe provider failure '+reason+'/'+code,()=>{
 const error=providerError('openai',status,{error:{code,message}});
 const response=apiError(error);assert.equal(response.status,502);assert.equal(response.body.error,'upstream_'+reason);assert.equal(response.body.upstreamStatus,status);
 const old=console.error;let line;console.error=s=>line=s;
 try{logRequestFailure(error);}finally{console.error=old;}
 assert.equal(JSON.parse(line).reason,reason);
 assert.ok(!JSON.stringify({error,response,line,stack:error.stack}).includes('fixture-secret'));
});
test('OpenAI HTTP auth failures are classified without retaining the raw response',async()=>{
 const oldFetch=globalThis.fetch,oldKey=process.env.OPENAI_API_KEY;process.env.OPENAI_API_KEY='fixture-secret';
 globalThis.fetch=async()=>new Response(JSON.stringify({error:{code:'api_key_expired',message:'API key fixture-secret expired'}}),{status:401});
 try{await assert.rejects(createResponse({model:'test'}),e=>e.reason==='credential_expired'&&!e.stack.includes('fixture-secret'));}finally{globalThis.fetch=oldFetch;if(oldKey===undefined)delete process.env.OPENAI_API_KEY;else process.env.OPENAI_API_KEY=oldKey;}
});
