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
