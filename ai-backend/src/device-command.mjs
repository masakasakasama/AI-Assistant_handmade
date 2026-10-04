// Recognize only complete imperative commands against the user's known devices.
// Questions, negation, conditions and compound sentences fall through to the intent model.
const norm=s=>String(s||'').normalize('NFKC').toLowerCase().replace(/[\s　の]/g,'');
const kinds=[
  ['air_conditioner',/air conditioner|air_conditioner/i,['エアコン','クーラー','air conditioner','air conditioners','klimaanlage','klimaanlagen']],
  ['light',/light|bulb/i,['照明','電気','ライト','light','lights','licht','lichter']],
  ['plug',/plug/i,['プラグ','コンセント','plug','plugs','steckdose','steckdosen']],
  ['fan',/fan/i,['扇風機','ファン','fan','fans','ventilator']],
  ['device',/humidifier/i,['加湿器','humidifier','luftbefeuchter']]
];
function known(context){
  const section=String(context||'').split('Known devices:')[1]?.split(/Current (?:alarms|local date)/)[0]||'';
  return section.split('\n').filter(line=>line.trim().startsWith('- ')).map(line=>{
    const [name,...fields]=line.trim().slice(2).split('|').map(s=>s.trim());
    const values=Object.fromEntries(fields.map(field=>{const at=field.indexOf('=');return [field.slice(0,at),field.slice(at+1)];}));
    return {name,originalName:values.actualName||'',id:values.id,type:values.type||''};
  }).filter(d=>d.id&&d.name);
}
function targetFor(text,devices){
  const display=text.replace(/^(?:the |die |den |das )/i,'').trim();
  const cleaned=norm(display);
  const exact=devices.filter(d=>[d.name,d.id,d.originalName].filter(Boolean).some(name=>norm(name)===cleaned));
  if(exact.length)return {target:exact.length===1?exact[0].id:display,targetType:null};
  const rooms=[['寝室',/^(寝室|bedroom|schlafzimmer)/],['リビング',/^(リビング|livingroom|wohnzimmer)/]];
  for(const [type,pattern,aliases] of kinds){
    if(!devices.some(d=>pattern.test(d.type)))continue;
    if(aliases.some(alias=>norm(alias)===cleaned))return {target:aliases[0],targetType:type};
    for(const [room,pattern] of rooms){
      const prefix=pattern.exec(cleaned)?.[0];
      if(prefix&&aliases.some(alias=>norm(alias)===cleaned.slice(prefix.length)))return {target:room+'の'+aliases[0],targetType:type};
    }
  }
  return null;
}
export function isActionRequest(text){
  if(typeof text!=='string'||/[?？「」"“”]|(?:ないで|なければ|なら|たら|if\b|unless\b|nicht\b|wenn\b|do not|don.t)/i.test(text))return false;
  return /(?:て|してください|てください|てくれ|てね|てよ|お願い)[。.!！]*$/.test(text.trim())||/^(?:please\s+|bitte\s+)?(?:turn|switch|set|schalte|mach)\b/i.test(text.trim());
}
export function explicitDeviceCommand({text,context}){
  if(!isActionRequest(text))return null;
  const source=text.normalize('NFKC').trim().replace(/[。.!！]+$/,'');
  let target,action,temperatureC=null,language='ja',match;
  if((match=/^(?:お願い[、,]?\s*)?(.+?)(?:を)?\s*(つけて|付けて|点けて|消して|切って|電源を入れて|電源を切って|オンにして|オフにして)(?:ください|くれる|くれ|ね|よ|お願い)?$/.exec(source))){
    target=match[1];action=/消|切|オフ/.test(match[2])?'turn_off':'turn_on';
  }else if((match=/^(?:please\s+)?(?:turn|switch)\s+(on|off)\s+(.+?)(?:,?\s+please)?$/i.exec(source))){
    language='en';target=match[2];action=match[1].toLowerCase()==='on'?'turn_on':'turn_off';
  }else if((match=/^(?:please\s+)?(?:turn|switch)\s+(.+?)\s+(on|off)(?:,?\s+please)?$/i.exec(source))){
    language='en';target=match[1];action=match[2].toLowerCase()==='on'?'turn_on':'turn_off';
  }else if((match=/^(?:bitte\s+)?(?:schalte|mach)\s+(.+?)\s+(ein|an|aus)(?:\s+bitte)?$/i.exec(source))){
    language='de';target=match[1];action=match[2].toLowerCase()==='aus'?'turn_off':'turn_on';
  }else if((match=/^(.+?)(?:を)?\s*(\d{2})\s*(?:度|℃|°c)(?:にして|に設定して)(?:ください|ね)?$/i.exec(source))){
    target=match[1];action='set_ac';temperatureC=Number(match[2]);
  }else return null;
  const selected=targetFor(target,known(context));
  if(!selected)return {model:'local-command',route:'clarify',language,action:null,target:target.trim(),targetType:null,
    confidence:1,goal:null,replyText:language==='de'?`${target.trim()} wurde nicht unter den bekannten Geräten gefunden. Bitte nenne den Gerätenamen oder Raum.`:
      language==='en'?`I could not find ${target.trim()} among the known devices. Please use its listed name or room.`:
      `${target.trim()}が登録機器に見つからないよ。家電一覧の名前か部屋を教えてね。`,evidence:'unknown_explicit_target'};
  return {model:'local-command',route:'device_action',language,action,...selected,temperatureC,
    confidence:1,goal:action==='turn_off'?'off':action==='turn_on'?'on':'set',
    parameters:temperatureC===null?{}:{temperature:temperatureC},executionMode:'execute',
    evidence:'explicit_known_device_command',replyText:null};
}
export function deviceClarification(route,text=""){
  if(!route.target&&!route.targetType&&!route.action&&(!route.goal||route.goal==="none")&&!/エアコン|照明|電気|家電|air conditioner|light|klimaanlage|licht/i.test(text))return route.language==='de'?'Welches Thema oder Ziel meinst du genau?':route.language==='en'?'Which subject or outcome do you mean?':'どの対象について、何をしたいか教えてね。';
  const names={air_conditioner:'エアコン',light:'照明',plug:'プラグ'};
  const target=route.target||names[route.targetType]||'家電';
  const ja=!route.target&&!route.targetType?'操作する家電の名前か部屋を教えてね。':!route.action&&(!route.goal||route.goal==='none')?`${target}をどうしたい？つける、消す、温度変更などを教えてね。`:`${target}への指示を確認したいので、対象と操作をもう一度教えてね。`;
  return route.language==='de'?'Welches Gerät soll ich wie ändern? Bitte nenne Gerät oder Raum und die gewünschte Aktion.':route.language==='en'?'Which device or room should I change, and what should I do?':ja;
}

export function validateDeviceRoute(route,{text=''}){
  if(route.route!=='device_action')return route;
  let reply=null;
  if(/ないで|消すな|つけるな|やめて|\b(?:don't|do not)\s+(?:turn|switch|set)|\bnicht\s+(?:aus|an|ein)\b/i.test(text)){
    reply=route.language==='de'?'Okay, ich führe diese Aktion nicht aus.':route.language==='en'?"Okay, I won't perform that action.":'わかったよ。その操作は行わないよ。';
  }else if(/[「」“”"]|(?:もし|なければ|なら|たら|\bif\b|\bunless\b|\bwenn\b)/i.test(text)){
    reply=route.language==='de'?'Soll ich die Aktion jetzt ausführen? Bedingungen oder zitierte Befehle werden nicht automatisch ausgeführt.':route.language==='en'?'Should I perform the action now? Conditional or quoted commands are not executed automatically.':'今その操作を実行したい？条件付きの指示や引用した指示は、そのまま実行しないよ。';
  }
  if(!reply&&(/[?？]/.test(text)||/(?:して(?:も)?いい|ますか|ですか|かな)[。.!！]*$/.test(text.trim()))) {
    return {...route,confidence:Math.min(Number.isFinite(route.confidence)?route.confidence:0,.5),evidence:'question_requires_confirmation'};
  }
  return reply?{...route,route:'clarify',action:null,goal:null,replyText:reply,blockedReason:'non_executable_utterance'}:route;
}
