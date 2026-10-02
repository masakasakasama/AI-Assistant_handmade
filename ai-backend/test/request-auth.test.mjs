import test from 'node:test';
import assert from 'node:assert/strict';
import {requestAuthError} from '../src/request-auth.mjs';
import dispatch from '../../api/dispatch.mjs';
import jev from '../../api/dispatch-jev.mjs';
import compare from '../../api/router-compare.mjs';
import transcribe from '../../api/transcribe.mjs';
const current='fixture-current-access', rotated='fixture-rotated-access';
for(const [name,handler] of Object.entries({dispatch,jev,compare,transcribe})){
  test(`${name}: authentication precedes body parsing and supports token rotation`,async()=>{
    const previous=process.env.AI_BACKEND_TOKEN;
    const previousFetch=globalThis.fetch;
    globalThis.fetch=()=>assert.fail('auth or malformed input reached provider');
    async function call(header,expected,body='not JSON'){
      const req={method:'POST',headers:{authorization:header},get body(){if(expected!==400)assert.fail('unauthorized request body read');return body;}};
      const res={setHeader(){},status(code){this.code=code;return this;},json(value){this.body=value;return this;}};
      await handler(req,res);assert.equal(res.code,expected);
      assert.ok(!JSON.stringify(res.body).includes(current));assert.ok(!JSON.stringify(res.body).includes(rotated));
    }
    try{
      delete process.env.AI_BACKEND_TOKEN;await call(undefined,503);
      process.env.AI_BACKEND_TOKEN=current;
      for(const header of [undefined,'','Basic fixture','Bearer wrong',['Bearer '+current]])await call(header,401);
      await call('Bearer '+current,400);
      process.env.AI_BACKEND_TOKEN=rotated;
      await call('Bearer '+current,401);await call('Bearer '+rotated,400);
    }finally{
      if(previous===undefined)delete process.env.AI_BACKEND_TOKEN;else process.env.AI_BACKEND_TOKEN=previous;
      globalThis.fetch=previousFetch;
    }
  });
}
test('exact bearer credential matching does not accept prefixes or trailing data',()=>{
  const previous=process.env.AI_BACKEND_TOKEN;
  try{process.env.AI_BACKEND_TOKEN=current;assert.equal(requestAuthError({headers:{authorization:'bearer '+current}}),null);
    for(const value of ['Bearer '+current+' extra','Bearer '+current+'x','Bearer '+current.slice(1)])assert.equal(requestAuthError({headers:{authorization:value}}).status,401);
  }finally{if(previous===undefined)delete process.env.AI_BACKEND_TOKEN;else process.env.AI_BACKEND_TOKEN=previous;}
});
