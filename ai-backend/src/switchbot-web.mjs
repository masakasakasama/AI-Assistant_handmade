import { createHmac, randomUUID } from 'node:crypto';
import types from './switchbot-types.mjs';

function invalid(message) { const error=new Error(message);error.code='invalid_switchbot_request';error.status=400;return error; }
export function profileForDevice(device) {
  const type=device.type.trim().toLowerCase();
  const profile=(on,off,onLabel='ON',offLabel='OFF')=>({on,off,onLabel,offLabel});
  const command=name=>({command:name,parameter:'default',commandType:'command'});
  if(device.infrared)return ['','others','other','unknown','infrared'].includes(type)?null:profile(command('turnOn'),command('turnOff'));
  if(type==='bot')return device.botMode==='switchMode'?profile(command('turnOn'),command('turnOff')):profile(command('press'),null,'押す');
  if(types.simpleVacuumTypes.includes(type))return profile(command('start'),command('stop'),'掃除開始','停止');
  if(types.wetVacuumTypes.includes(type)||types.advancedVacuumTypes.includes(type)) {
    const param={fanLevel:1,times:1};if(types.wetVacuumTypes.includes(type))param.waterLevel=1;
    return profile({command:'startClean',parameter:{action:'sweep',param},commandType:'command'},command('pause'),'掃除開始','一時停止');
  }
  if(type==='blind tilt')return profile(command('fullyOpen'),command('closeDown'),'開く','閉じる（下向き）');
  if(types.standardPowerTypes.includes(type))return profile(command('turnOn'),command('turnOff'),type.startsWith('curtain')?'開く':'ON',type.startsWith('curtain')?'閉じる':'OFF');
  return null;
}

export function switchbotRequestHeaders(token,secret,timestamp=Date.now().toString(),nonce=randomUUID()) {
  return {Authorization:token,sign:createHmac('sha256',secret).update(token+timestamp+nonce).digest('base64'),t:timestamp,nonce,'Content-Type':'application/json; charset=utf8'};
}

export async function switchbotWeb(body,{fetchImpl=fetch}={}) {
  const {token,secret,operation}=body;
  if(!['list','command'].includes(operation))throw invalid('操作種別が不正です');
  if([token,secret].some(value=>typeof value!=='string'||value.length<8||value.length>512||/\s/.test(value)))throw invalid('SwitchBotのTokenとSecretを設定してください');
  async function request(path,payload) {
    const response=await fetchImpl('https://api.switch-bot.com/v1.1'+path,{method:payload?'POST':'GET',headers:switchbotRequestHeaders(token,secret),body:payload?JSON.stringify(payload):undefined,redirect:'error',signal:AbortSignal.timeout(15_000)});
    if(!response.ok)throw new Error('switchbot_http_failure');
    const data=await response.json();
    if(data.statusCode!==100) { const error=new Error('SwitchBotが操作を受け付けませんでした');error.code='switchbot_rejected';throw error; }
    return data.body;
  }
  const inventory=await request('/devices');
  const devices=[...(inventory.deviceList||[]).map(d=>({deviceId:d.deviceId,name:d.deviceName||d.deviceId,type:d.deviceType||'Unknown',infrared:false})),...(inventory.infraredRemoteList||[]).map(d=>({deviceId:d.deviceId,name:d.deviceName||d.deviceId,type:d.remoteType||'Unknown',infrared:true}))];
  const deviceId=operation==='command'?body.deviceId:null;
  if(operation==='command'&&(typeof deviceId!=='string'||!/^[A-Za-z0-9:_-]{1,128}$/.test(deviceId)))throw invalid('機器IDが不正です');
  // Read Bot mode instead of inventing an OFF command for a press-mode Bot.
  const bots=devices.filter(d=>!d.infrared&&d.type.toLowerCase()==='bot'&&(!deviceId||d.deviceId===deviceId));
  await Promise.all(bots.map(async d=>{try{d.botMode=(await request('/devices/'+encodeURIComponent(d.deviceId)+'/status')).deviceMode;}catch{d.botMode=null;}}));
  if(operation==='list')return {devices:devices.map(d=>({...d,profile:profileForDevice(d)}))};
  const device=devices.find(d=>d.deviceId===deviceId);
  if(!device)throw invalid('機器一覧にない対象は操作できません');
  let payload;
  if(body.action==='ac') {
    if(!device.infrared||device.type.toLowerCase()!=='air conditioner')throw invalid('この機器は赤外線エアコンではありません');
    const {temperature,mode,fanSpeed,power}=body;
    if(!Number.isInteger(temperature)||temperature<16||temperature>30||!Number.isInteger(mode)||mode<1||mode>5||!Number.isInteger(fanSpeed)||fanSpeed<1||fanSpeed>4||typeof power!=='boolean')throw invalid('エアコンの指定値が範囲外です');
    payload={command:'setAll',parameter:[temperature,mode,fanSpeed,power?'on':'off'].join(','),commandType:'command'};
  } else {
    if(!['on','off'].includes(body.action))throw invalid('対応していない操作です');
    const profile=profileForDevice(device);payload=profile?.[body.action];
    if(!payload)throw invalid('この機種・モードにはその操作がありません');
  }
  await request('/devices/'+encodeURIComponent(deviceId)+'/commands',payload);
  return {accepted:true,deviceId,command:payload.command};
}
