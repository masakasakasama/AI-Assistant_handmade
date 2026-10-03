import test from 'node:test';
import assert from 'node:assert/strict';
import {claimRequest,CLAIM_SCRIPT} from '../src/request-limits.mjs';
import {apiError} from '../src/openai.mjs';
import {transcriptionError} from '../src/transcribe.mjs';
import {logRequestFailure} from '../src/safe-logging.mjs';
import dispatch from '../../api/dispatch.mjs';
import jev from '../../api/dispatch-jev.mjs';
import compare from '../../api/router-compare.mjs';
import transcribe from '../../api/transcribe.mjs';
const env={AI_LIMIT_REDIS_URL:'https://limits.example.test',AI_LIMIT_REDIS_TOKEN:'fixture-secret',AI_DAILY_REQUEST_LIMIT:'3',AI_RATE_REQUEST_LIMIT:'2'};
const now=Date.parse('2026-10-03T00:00:01Z');
function store() {
 const values=new Map();
 // Deterministic shared-store fixture; real Redis EVAL acceptance is deployment work.
 return async (url,opts)=>{
  assert.equal(opts.headers.Authorization,'Bearer fixture-secret');
  const [command,script,numKeys,day,minute,dailyLimit,rateLimit]=JSON.parse(opts.body);
  assert.equal(command,'EVAL');assert.equal(script,CLAIM_SCRIPT);assert.equal(numKeys,'2');
  const d=values.get(day)||0,m=values.get(minute)||0;
  const result=d>=Number(dailyLimit)?[0,1]:m>=Number(rateLimit)?[0,2]:[1,0];
  if(result[0]){values.set(day,d+1);values.set(minute,m+1);}
  return Response.json({result});
 };
}
test('rate/day counters are shared across requests, token rotation and UTC windows',async()=>{
 const fetchImpl=store();
 await claimRequest({env,fetchImpl,now});await claimRequest({env,fetchImpl,now});
 await assert.rejects(claimRequest({env,fetchImpl,now}),e=>e.status===429&&e.code==='request_rate_limit'&&e.retryAfterSeconds===59);
 await claimRequest({env:{...env,AI_BACKEND_TOKEN:'rotated'},fetchImpl,now:now+60000});
 await assert.rejects(claimRequest({env,fetchImpl,now:now+120000}),e=>e.code==='daily_request_limit');
 await claimRequest({env,fetchImpl,now:now+86400000});
});
test('concurrent accepted claims never exceed the shared fixture limit',async()=>{
 const fetchImpl=store();const results=await Promise.allSettled(Array.from({length:20},()=>claimRequest({env,fetchImpl,now})));
 assert.equal(results.filter(r=>r.status==='fulfilled').length,2);
});
test('unknown configuration/store outcomes fail closed without reflecting secrets',async()=>{
 for(const patch of [{AI_DAILY_REQUEST_LIMIT:''},{AI_RATE_REQUEST_LIMIT:'0'},{AI_DAILY_REQUEST_LIMIT:'NaN'},{AI_LIMIT_REDIS_URL:'http://example.test'},{AI_LIMIT_REDIS_TOKEN:''}]){
  await assert.rejects(claimRequest({env:{...env,...patch},fetchImpl:()=>assert.fail('invalid configuration sent'),now}),e=>e.status===503);
 }
 for(const fetchImpl of [async()=>{throw new Error('fixture-secret private conversation');},async()=>Response.json({result:'unexpected'}),async()=>new Response('fixture-secret',{status:500})]){
  await assert.rejects(claimRequest({env,fetchImpl,now}),e=>e.status===503&&!JSON.stringify(apiError(e)).includes('fixture-secret'));
 }
});
test('all serverless routes reject a denied claim before provider calls',async()=>{
 const keys=Object.keys(env).concat('AI_BACKEND_TOKEN');const previous=keys.map(k=>process.env[k]);const oldFetch=globalThis.fetch;
 try{
  Object.assign(process.env,env,{AI_BACKEND_TOKEN:'fixture-owner'});let calls=0;
  globalThis.fetch=async url=>{assert.equal(new URL(url).hostname,'limits.example.test');calls++;return Response.json({result:[0,2]});};
  const audio=Buffer.alloc(48);audio.write('RIFF');audio.writeUInt32LE(40,4);audio.write('WAVEfmt ',8);audio.writeUInt32LE(16,16);audio.writeUInt16LE(1,20);audio.writeUInt16LE(1,22);audio.writeUInt32LE(16000,24);audio.writeUInt16LE(16,34);audio.write('data',36);audio.writeUInt32LE(4,40);
  for(const handler of [dispatch,jev,compare,transcribe]){
   const res={setHeader(){},status(n){this.code=n;return this;},json(body){this.body=body;}};
   await handler({method:'POST',headers:{authorization:'Bearer fixture-owner'},body:{text:'private fixture prompt',audioBase64:audio.toString('base64')}},res);
   assert.equal(res.code,429);assert.equal(res.body.error,'request_rate_limit');assert.ok(!JSON.stringify(res.body).includes('private fixture'));
  }
  assert.equal(calls,4);
 }finally{globalThis.fetch=oldFetch;keys.forEach((k,i)=>{if(previous[i]===undefined)delete process.env[k];else process.env[k]=previous[i];});}
});
test('unexpected provider errors and logs expose no prompt/audio/token/cause',()=>{
 const error=new Error('fixture-secret prompt audio',{cause:new Error('Authorization Bearer hidden')});
 for(const failure of [apiError(error),transcriptionError(error)])assert.ok(!JSON.stringify(failure).includes('fixture-secret'));
 const old=console.error;const logs=[];console.error=(...args)=>logs.push(args);
 try{logRequestFailure(error);}finally{console.error=old;}
 assert.deepEqual(logs,[[JSON.stringify({event:'backend_request_failed'})]]);
});

test('free database enforces the same rate/day failures and hides storage errors',async()=>{
 const databaseEnv={AI_LIMIT_DATABASE_URL:'postgresql://user:fixture-secret@limits.neon.tech/neondb',AI_DAILY_REQUEST_LIMIT:'3',AI_RATE_REQUEST_LIMIT:'2'};
 let seen;
 await claimRequest({env:databaseEnv,now,databaseClaimImpl:async(...args)=>{seen=args;return [1,0];}});
 assert.deepEqual(seen,[databaseEnv.AI_LIMIT_DATABASE_URL,'tatsu-home:requests:v1:day:2026-10-03','tatsu-home:requests:v1:minute:'+Math.floor(now/60000),3,2]);
 for(const [result,code] of [[[0,1],'daily_request_limit'],[[0,2],'request_rate_limit']])await assert.rejects(claimRequest({env:databaseEnv,now,databaseClaimImpl:async()=>result}),e=>e.status===429&&e.code===code);
 for(const databaseClaimImpl of [async()=>{throw Error('fixture-secret');},async()=>undefined,async()=>[1,1]])await assert.rejects(claimRequest({env:databaseEnv,now,databaseClaimImpl}),e=>e.status===503&&!JSON.stringify(apiError(e)).includes('fixture-secret'));
 for(const patch of [{AI_LIMIT_DATABASE_URL:'https://limits.neon.tech'}, {AI_LIMIT_DATABASE_URL:'postgresql://u:s@untrusted.test/db'}, {AI_RATE_REQUEST_LIMIT:'0'}])await assert.rejects(claimRequest({env:{...databaseEnv,...patch},now,databaseClaimImpl:()=>assert.fail('Invalid configuration connected to store')}),e=>e.code==='usage_limits_not_configured');
});
