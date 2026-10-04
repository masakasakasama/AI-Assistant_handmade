import {explicitDeviceCommand,isActionRequest} from './device-command.mjs';

// Compound commands are an ordered plan, never an invented combined device name.
export function explicitCommands(input){
  const text=String(input.text||'');
  if(/[?？「」"“”]|(?:ないで|なければ|なら|たら|if\b|unless\b|nicht\b|wenn\b|do not|don.t)/i.test(text))return null;
  const parts=text.split(/\s*(?:[、,;；]|\band\b|\bund\b|それから|そのあと)\s*/i).filter(Boolean);
  if(parts.length===1)return explicitDeviceCommand(input);
  // A comma in a polite prefix/suffix is not a compound command.
  if(parts.some(p=>/^(please|bitte|お願い)$/i.test(p)))return explicitDeviceCommand(input);
  if(parts.length>6)return {model:'local-command',route:'clarify',language:'ja',confidence:1,action:null,replyText:'一度に6つまでの操作をお願い。指示を分けて教えてね。'};
  const steps=parts.map(part=>explicitDeviceCommand({...input,text:part.replace(/も(?=(?:消して|つけて|切って|付けて|点けて|オフにして|オンにして))/,'')}));
  if(steps.every(step=>!step))return null;
  const invalid=steps.findIndex(step=>step?.route!=='device_action');
  if(invalid>=0)return {model:'local-command',route:'clarify',language:steps[0]?.language||'ja',confidence:1,action:null,
    replyText:`「${parts[invalid]}」の対象か操作を確認できなかったよ。まだどの操作も実行していないよ。家電名と操作を教えてね。`};
  return {model:'local-command',route:'device_batch',language:steps[0].language,confidence:1,action:null,steps,executionMode:'execute'};
}

function dateInZone(date,zone){return new Intl.DateTimeFormat('en-CA',{timeZone:zone||'UTC',year:'numeric',month:'2-digit',day:'2-digit'}).format(date);}
export function alarmDate(route,input){
  if(route.route!=='alarm_action'||!['alarm_create','alarm_update'].includes(route.action))return route;
  const text=String(input.text||'');
  const explicit=/\b(\d{4}-\d{2}-\d{2})\b/.exec(text)?.[1];
  const offset=/明後日|übermorgen|day after tomorrow/i.test(text)?2:/明日|\btomorrow\b|\bmorgen\b/i.test(text)?1:/今日|\btoday\b|\bheute\b/i.test(text)?0:null;
  if(!explicit&&offset===null)return route;
  let dateLocal=explicit;
  if(!dateLocal){
    const reference=/Current local date(?: and time)?:\s*([^\n]+)/.exec(input.context||'')?.[1];
    const zone=/Current time zone:\s*([^\n]+)/.exec(input.context||'')?.[1]?.trim();
    let base=reference?.match(/^(\d{4}-\d{2}-\d{2})/)?.[1];
    if(!base&&reference){const parsed=new Date(reference);if(Number.isFinite(parsed.getTime()))try{base=dateInZone(parsed,zone);}catch{}}
    if(!base)return {...route,route:'clarify',action:null,replyText:'端末の日付を確認できなかったよ。日付と時刻をもう一度教えてね。'};
    const shifted=new Date(base+'T12:00:00Z');shifted.setUTCDate(shifted.getUTCDate()+offset);dateLocal=shifted.toISOString().slice(0,10);
  }
  const valid=new Date(dateLocal+'T12:00:00Z');
  if(!Number.isFinite(valid.getTime())||valid.toISOString().slice(0,10)!==dateLocal)return {...route,route:'clarify',action:null,replyText:'その日付は存在しないよ。日付を確認してね。'};
  return {...route,dateLocal};
}
