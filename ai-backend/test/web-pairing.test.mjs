import test from 'node:test';
import assert from 'node:assert/strict';
import { createPairing,redeemPairing } from '../src/web-pairing.mjs';
import handler from '../../api/web-pairing.mjs';

function fixture(){
  const rows=new Map();
  return {rows,store:{async put(id,payload,expires){rows.set(id,{payload,expires});},async take(id){const row=rows.get(id);rows.delete(id);return row&&row.expires>Date.now()?row.payload:null;}}};
}
test('pairing encrypts credentials at rest, expires, and can be consumed only once even concurrently',async()=>{
  const previous=process.env.AI_BACKEND_TOKEN;process.env.AI_BACKEND_TOKEN='fixture-owner';
  try{
    const {store,rows}=fixture();
    const grant=await createPairing({switchbotToken:'fixture-switch-token',switchbotSecret:'fixture-secret'},{store});
    const serialized=JSON.stringify([...rows.values()]);
    for(const secret of ['fixture-owner','fixture-switch-token','fixture-secret'])assert.ok(!serialized.includes(secret));
    const results=await Promise.allSettled([redeemPairing({code:grant.code},{store}),redeemPairing({code:grant.code},{store})]);
    assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
    assert.equal(results.find(r=>r.status==='fulfilled').value.switchbotSecret,'fixture-secret');
    assert.equal(results.find(r=>r.status==='rejected').reason.status,410);
    const expired=await createPairing({switchbotToken:'x',switchbotSecret:'y'},{store,now:Date.now()-601_000});
    await assert.rejects(redeemPairing({code:expired.code},{store}),{status:410});
  }finally{if(previous===undefined)delete process.env.AI_BACKEND_TOKEN;else process.env.AI_BACKEND_TOKEN=previous;}
});
test('key rotation invalidates an unused grant and malformed capabilities never reach storage',async()=>{
  const previous=process.env.AI_BACKEND_TOKEN;process.env.AI_BACKEND_TOKEN='old-fixture-owner';
  try{
    const {store}=fixture();const grant=await createPairing({switchbotToken:'x',switchbotSecret:'y'},{store});
    process.env.AI_BACKEND_TOKEN='new-fixture-owner';
    await assert.rejects(redeemPairing({code:grant.code},{store}),{status:410});
    let calls=0;await assert.rejects(redeemPairing({code:'invalid'},{store:{take(){calls++;}}}),{status:400});assert.equal(calls,0);
  }finally{if(previous===undefined)delete process.env.AI_BACKEND_TOKEN;else process.env.AI_BACKEND_TOKEN=previous;}
});
test('creating a pairing capability requires owner authorization before body parsing or network access',async()=>{
  const previous=process.env.AI_BACKEND_TOKEN,oldFetch=globalThis.fetch;process.env.AI_BACKEND_TOKEN='fixture-owner';let calls=0;
  const res={setHeader(){},status(code){this.code=code;return this;},json(body){this.body=body;return this;}};
  globalThis.fetch=()=>{calls++;throw new Error('No external request allowed');};
  try{await handler({method:'POST',headers:{},query:{},body:'bad'},res);assert.equal(res.code,401);assert.equal(calls,0);}
  finally{globalThis.fetch=oldFetch;if(previous===undefined)delete process.env.AI_BACKEND_TOKEN;else process.env.AI_BACKEND_TOKEN=previous;}
});
