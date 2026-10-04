// Browser-profile vault. Credentials never enter assets, URLs or logs.
const empty=()=>({ownerToken:'',switchbotToken:'',switchbotSecret:''});
async function record(operation,value){
  const db=await new Promise((resolve,reject)=>{
    const request=indexedDB.open('tatsu-credentials',1);
    request.onupgradeneeded=()=>request.result.createObjectStore('vault');
    request.onsuccess=()=>resolve(request.result);
    request.onerror=request.onblocked=()=>reject(new Error('認証情報の保存先を開けませんでした。'));
  });
  try{return await new Promise((resolve,reject)=>{
    const transaction=db.transaction('vault',operation==='read'?'readonly':'readwrite');
    const store=transaction.objectStore('vault');
    const request=operation==='read'?store.get('credentials'):operation==='clear'?store.delete('credentials'):store.put(value,'credentials');
    let result;request.onsuccess=()=>{result=request.result;};
    transaction.oncomplete=()=>resolve(result);
    transaction.onerror=transaction.onabort=()=>reject(new Error('認証情報を保存できませんでした。'));
  });}finally{db.close();}
}
export async function readCredentials(){
  const stored=await record('read');if(!stored)return null;
  const plaintext=await crypto.subtle.decrypt({name:'AES-GCM',iv:stored.iv},stored.key,stored.data);
  return JSON.parse(new TextDecoder().decode(plaintext));
}
export async function saveCredentials(credentials){
  const key=await crypto.subtle.generateKey({name:'AES-GCM',length:256},false,['encrypt','decrypt']);
  const iv=crypto.getRandomValues(new Uint8Array(12));
  const data=await crypto.subtle.encrypt({name:'AES-GCM',iv},key,new TextEncoder().encode(JSON.stringify(credentials)));
  await record('write',{key,iv,data});return credentials;
}
export async function clearCredentials(){await record('clear');}
export const emptyCredentials=empty;
