import test from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import net from 'node:net';
import {once} from 'node:events';
const routes=['dispatch','dispatch-jev','router-compare','transcribe'];
async function server(token){
  const socket=net.createServer();socket.listen(0,'127.0.0.1');await once(socket,'listening');const port=socket.address().port;await new Promise(resolve=>socket.close(resolve));
  const child=spawn(process.execPath,['src/server.mjs'],{cwd:new URL('../',import.meta.url),env:{...process.env,PORT:String(port),AI_BACKEND_TOKEN:token,OPENAI_API_KEY:'',JEV_OPENROUTER_API_KEY:''},stdio:['ignore','pipe','pipe']});
  await new Promise((resolve,reject)=>{const timer=setTimeout(()=>{child.kill();reject(new Error('fixture startup timeout'));},8000);child.once('error',error=>{clearTimeout(timer);reject(error)});child.stdout.once('data',()=>{clearTimeout(timer);resolve()});});
  return {base:`http://127.0.0.1:${port}`,async close(){const ended=once(child,'exit');child.kill();await ended;}};
}
test('local HTTP paid routes enforce the same gate as serverless handlers',async()=>{
  for(const token of ['', 'local-fixture-access']){
    const fixture=await server(token);
    try{
      assert.equal((await fetch(fixture.base+'/api/health')).status,200);
      for(const route of routes){
        for(const header of [undefined,'Bearer wrong',...(token?['Bearer '+token]:[])]){
          const response=await fetch(fixture.base+'/api/'+route,{method:'POST',headers:{'Content-Type':'application/json',...(header?{Authorization:header}:{})},body:'{',signal:AbortSignal.timeout(3000)});
          assert.equal(response.status,!token?503:header==='Bearer '+token?400:401);
          assert.ok(!(await response.text()).includes('local-fixture-access'));
        }
      }
    }finally{await fixture.close();}
  }
});
test('local HTTP fails closed for valid paid requests without shared limit configuration',async()=>{
  const saved=process.env.AI_LIMIT_REDIS_URL;delete process.env.AI_LIMIT_REDIS_URL;
  const fixture=await server('limits-local-fixture');
  try{
    const audio=Buffer.alloc(48);audio.write('RIFF');audio.writeUInt32LE(40,4);audio.write('WAVEfmt ',8);audio.writeUInt32LE(16,16);audio.writeUInt16LE(1,20);audio.writeUInt16LE(1,22);audio.writeUInt32LE(16000,24);audio.writeUInt16LE(16,34);audio.write('data',36);audio.writeUInt32LE(4,40);
    for(const route of routes){
      const response=await fetch(fixture.base+'/api/'+route,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer limits-local-fixture'},body:JSON.stringify({text:'Synthetic private text',audioBase64:audio.toString('base64')}),signal:AbortSignal.timeout(3000)});
      assert.equal(response.status,503);assert.equal((await response.json()).error,'usage_limits_not_configured');
    }
    assert.equal((await fetch(fixture.base+'/api/health')).status,200);
  }finally{await fixture.close();if(saved===undefined)delete process.env.AI_LIMIT_REDIS_URL;else process.env.AI_LIMIT_REDIS_URL=saved;}
});
