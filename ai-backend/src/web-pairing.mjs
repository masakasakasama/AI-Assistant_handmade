import { createHash,randomBytes,createCipheriv,createDecipheriv } from 'node:crypto';
import { neon } from '@neondatabase/serverless';

const hash=value=>createHash('sha256').update(value).digest();
const fail=(code,status)=>Object.assign(new Error(code),{code,status});
function serverKey(){
  const owner=process.env.AI_BACKEND_TOKEN?.trim();
  if(!owner)throw fail('backend_auth_not_configured',503);
  return hash('tatsu-web-pairing-v1:'+owner);
}
export function postgresPairingStore(){
  const connection=process.env.AI_LIMIT_DATABASE_URL;
  if(!connection)throw fail('pairing_unavailable',503);
  const sql=neon(connection);
  const query=(text,values=[])=>sql.query(text,values,{fetchOptions:{signal:AbortSignal.timeout(5000)}});
  return {
    async put(id,payload,expires){
      await query('CREATE TABLE IF NOT EXISTS tatsu_web_pairing (id text PRIMARY KEY, payload jsonb NOT NULL, expires_at timestamptz NOT NULL)');
      await query('DELETE FROM tatsu_web_pairing WHERE expires_at <= NOW()');
      await query('INSERT INTO tatsu_web_pairing (id,payload,expires_at) VALUES ($1,$2::jsonb,$3)',[id,JSON.stringify(payload),expires.toISOString()]);
    },
    async take(id){
      const rows=await query('DELETE FROM tatsu_web_pairing WHERE id=$1 AND expires_at>NOW() RETURNING payload',[id]);
      return rows[0]?.payload;
    }
  };
}
export async function createPairing(body,{store=postgresPairingStore(),now=Date.now()}={}){
  const credentials={ownerToken:process.env.AI_BACKEND_TOKEN?.trim()};
  for(const name of ['switchbotToken','switchbotSecret']){
    const value=body[name];
    if(typeof value!=='string'||value.length>512)throw fail('invalid_pairing_request',400);
    credentials[name]=value.trim();
  }
  const code=randomBytes(32).toString('base64url'),id=hash(code).toString('hex');
  const iv=randomBytes(12),cipher=createCipheriv('aes-256-gcm',serverKey(),iv);
  cipher.setAAD(Buffer.from(id));
  const encrypted=Buffer.concat([cipher.update(JSON.stringify(credentials),'utf8'),cipher.final()]);
  await store.put(id,{iv:iv.toString('base64'),data:encrypted.toString('base64'),tag:cipher.getAuthTag().toString('base64')},new Date(now+10*60_000));
  return {code,expiresInSeconds:600};
}
export async function redeemPairing(body,{store=postgresPairingStore()}={}){
  if(typeof body.code!=='string'||! /^[A-Za-z0-9_-]{43}$/.test(body.code))throw fail('invalid_pairing_request',400);
  const id=hash(body.code).toString('hex'),stored=await store.take(id);
  if(!stored)throw fail('pairing_expired',410);
  try{
    const decipher=createDecipheriv('aes-256-gcm',serverKey(),Buffer.from(stored.iv,'base64'));
    decipher.setAAD(Buffer.from(id));decipher.setAuthTag(Buffer.from(stored.tag,'base64'));
    return JSON.parse(Buffer.concat([decipher.update(Buffer.from(stored.data,'base64')),decipher.final()]).toString('utf8'));
  }catch{throw fail('pairing_expired',410);}
}
