import { createServer } from 'node:http';
import { readFile,stat } from 'node:fs/promises';
import { resolve,extname } from 'node:path';
const root=resolve('dist'),port=Number(process.env.WEB_PORT||4173);
const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json','.webmanifest':'application/manifest+json','.glb':'model/gltf-binary','.png':'image/png'};
createServer(async(req,res)=>{
  const pathname=new URL(req.url,'http://localhost').pathname;
  const file=resolve(root,'.'+(pathname==='/'?'/index.html':decodeURIComponent(pathname)));
  if(file!==root&&!file.startsWith(root+'/')){res.writeHead(403).end();return;}
  try{if(!(await stat(file)).isFile())throw new Error();res.writeHead(200,{'Content-Type':mime[extname(file)]||'application/octet-stream','Cache-Control':'no-store'});res.end(await readFile(file));}
  catch{res.writeHead(404).end('Not found');}
}).listen(port,'127.0.0.1',()=>console.log(`Web preview: http://127.0.0.1:${port}`));
