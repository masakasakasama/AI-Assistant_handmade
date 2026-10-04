import test from 'node:test';
import assert from 'node:assert/strict';
import {dispatch} from '../src/dispatch.mjs';
import {dispatchJev} from '../src/dispatch-jev.mjs';
for(const [execute,routeName] of [[dispatch,'routeIntent'],[dispatchJev,'routeIntentJev']])test('fractional millisecond boundaries do not invent negative overhead: '+execute.name,async()=>{
 const times=[.4,1,1,1.6,1.6,1.6,1.6];
 const result=await execute({text:'hello'},{now:()=>times.shift()??1.6,[routeName]:async()=>({route:execute===dispatch?'deep_reasoning':'simple_chat',language:'en',model:'fixture'}),answerSimple:async()=>({model:'fixture',text:'hello'}),reason:async()=>({model:'fixture',text:'hello'})});
 assert.equal(result.timings.totalMs,2);
 // Without TTFT/generation measurements, answer time remains explicitly unaccounted.
 assert.equal(result.timings.unaccountedMs,result.timings.answerMs);
 assert.equal(result.timings.timingError,null);
 assert.equal(result.timings.routerMs+result.timings.answerStartWaitMs+result.timings.answerMs+result.timings.responseAssemblyMs,result.timings.totalMs);
});
