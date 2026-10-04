const normalize=value=>String(value||'').toLowerCase()
  .replace(/air_conditioner|air conditioner|klimaanlage/g,'エアコン')
  .replace(/living room|livingroom|wohnzimmer/g,'リビング')
  .replace(/bedroom|schlafzimmer/g,'寝室')
  .replace(/lights|light|licht|電気|ライト/g,'照明').replace(/[\s　の]/g,'');
const room=value=>{
  const text=normalize(value);
  return ['寝室','リビング','その他'].find(name=>text.includes(name))||null;
};
export function resolveDeviceTargets(target,targetType,devices){
  const text=String(target||'').trim(),normalized=normalize(text),explicitRoom=room(text);
  const declared=normalize(targetType);
  const labels=['エアコン','照明','プラグ','扇風機','加湿器'];
  const types={air_conditioner:'エアコン',light:'照明',plug:'プラグ',fan:'扇風機',humidifier:'加湿器'};
  const type=types[declared]|| (labels.includes(declared)?declared:
    normalized.includes('エアコン')?'エアコン':normalized.includes('照明')?'照明':labels.find(label=>normalized.includes(label))||null);
  const unique=[...new Map(devices.map(device=>[device.deviceId,device])).values()];
  const candidates=unique.filter(device=>(!explicitRoom||(device.room||room(device.name))===explicitRoom)&&
    (!type||(type==='エアコン'?normalize(device.type)==='エアコン':type==='照明'?/light|bulb/i.test(device.type):new RegExp(({プラグ:'plug',扇風機:'fan',加湿器:'humidifier'})[type],'i').test(device.type))));
  if(!text)return type?candidates:[];
  if(type&&[type,explicitRoom,`${explicitRoom||''}${type}`].includes(normalized))return candidates;
  return candidates.filter(device=>device.deviceId===text||normalize(device.name)===normalized||
    (!explicitRoom&&device.originalName&&normalize(device.originalName)===normalized));
}
