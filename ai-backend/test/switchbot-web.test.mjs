import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { profileForDevice,switchbotWeb,switchbotRequestHeaders } from '../src/switchbot-web.mjs';
import types from '../src/switchbot-types.mjs';
import { createHmac } from 'node:crypto';
import handler from '../../api/switchbot.mjs';

test('SwitchBot endpoint rejects missing server auth and unauthorized callers before any external request',async()=>{
  const oldToken=process.env.AI_BACKEND_TOKEN,oldFetch=globalThis.fetch;let calls=0;
  const res={setHeader(){},status(code){this.code=code;return this;},json(body){this.body=body;return this;}};
  globalThis.fetch=()=>{calls++;throw new Error('Unexpected network request');};
  try{
    delete process.env.AI_BACKEND_TOKEN;await handler({method:'POST',headers:{},body:'invalid'},res);assert.equal(res.code,503);
    process.env.AI_BACKEND_TOKEN='fixture-owner-token';await handler({method:'POST',headers:{authorization:'Bearer revoked-token'},body:'invalid'},res);assert.equal(res.code,401);
    assert.equal(calls,0);
  }finally{globalThis.fetch=oldFetch;if(oldToken===undefined)delete process.env.AI_BACKEND_TOKEN;else process.env.AI_BACKEND_TOKEN=oldToken;}
});

test('Web command types match the audited Android profiles',async()=>{
  const kotlin=await readFile(new URL('../../app/src/main/java/com/tatsu/homehub/model/SwitchBotControlProfiles.kt',import.meta.url),'utf8');
  for(const [name,values]of Object.entries(types)){
    const list=new RegExp(`private val ${name} = setOf\\(([\\s\\S]*?)\\)\\s*(?=\\n\\s*(?:private val|fun ))`).exec(kotlin)?.[1];
    assert.deepEqual(values,[...list.matchAll(/"([^"\n]+)"/g)].map(match=>match[1]));
  }
  assert.equal(profileForDevice({type:'K10+ Pro'}).on.command,'start');
  assert.deepEqual(profileForDevice({type:'Floor Cleaning Robot S20'}).on.parameter,{action:'sweep',param:{fanLevel:1,times:1,waterLevel:1}});
  assert.equal(profileForDevice({type:'Bot',botMode:null}).off,null);
  assert.equal(profileForDevice({type:'Bot',botMode:'switchMode'}).off.command,'turnOff');
  for(const type of ['Hub 2','Lock','Relay Switch 2PM','Unknown'])assert.equal(profileForDevice({type}),null);
});

test('signing uses documented HMAC over token, timestamp and nonce',()=>{
  const headers=switchbotRequestHeaders('token','secret','123','nonce');
  assert.equal(headers.sign,createHmac('sha256','secret').update('token123nonce').digest('base64'));
});

test('commands are derived from fresh provider inventory and startClean remains an object',async()=>{
  const calls=[];const fetchImpl=async(url,options)=>{calls.push({url,options});return Response.json({statusCode:100,body:calls.length===1?{deviceList:[{deviceId:'abc',deviceName:'Cleaner',deviceType:'Floor Cleaning Robot S20'}]}:{}});};
  const accepted=await switchbotWeb({token:'test-token',secret:'test-secret',operation:'command',deviceId:'abc',action:'on'},{fetchImpl});
  assert.equal(accepted.accepted,true);const payload=JSON.parse(calls[1].options.body);assert.equal(payload.command,'startClean');assert.equal(typeof payload.parameter,'object');
  assert.equal(calls[1].options.redirect,'error');
});

test('invented device types, targets, commands and unsafe AC values cannot issue commands',async()=>{
  for(const request of [{deviceId:'absent',action:'on'},{deviceId:'abc',action:'off'},{deviceId:'abc',action:'erase'},{deviceId:'abc',action:'ac',temperature:99,mode:1,fanSpeed:1,power:true}]){
    let commands=0;const fetchImpl=async(url,options)=>{if(options.method==='POST')commands++;return Response.json({statusCode:100,body:{deviceList:[{deviceId:'abc',deviceName:'Hub',deviceType:'Hub 2'}]}});};
    await assert.rejects(switchbotWeb({token:'test-token',secret:'test-secret',operation:'command',type:'Plug',...request},{fetchImpl}),{code:'invalid_switchbot_request'});assert.equal(commands,0);
  }
});

test('SwitchBot business rejection is never reported as success',async()=>{
  let calls=0;await assert.rejects(switchbotWeb({token:'test-token',secret:'test-secret',operation:'command',deviceId:'abc',action:'on'},{fetchImpl:async()=>Response.json(++calls===1?{statusCode:100,body:{deviceList:[{deviceId:'abc',deviceType:'Plug'}]}}:{statusCode:190,body:{}})}),{code:'switchbot_rejected'});
});
