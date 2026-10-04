import { createMascot } from './mascot.js';
import { startCapture,audioBase64 } from './audio.js';
import { resolveDeviceTargets } from './device-targets.js';
import { readCredentials,saveCredentials,clearCredentials,emptyCredentials } from './credentials.js';

const $=selector=>document.querySelector(selector);
const defaults={answerMode:'balanced',language:'auto',place:'元浅草',latitude:35.7126,longitude:139.78,readAloud:true};
const load=(storage,key,fallback)=>{try{return JSON.parse(storage.getItem(key))??fallback;}catch{return fallback;}};
let settings={...defaults,...load(localStorage,'tatsu-web-settings',{})};
const pairingCode=new URLSearchParams(location.hash.slice(1)).get('pair');
if(pairingCode)window.history.replaceState(null,'',location.pathname+location.search);
let credentials=emptyCredentials(),credentialNotice='',importedCredentials=false,credentialChannel=null;
try{
  const stored=await readCredentials(),legacy=load(sessionStorage,'tatsu-web-credentials',null);
  credentials=stored||legacy||emptyCredentials();
  if(!stored&&legacy)credentials=await saveCredentials(legacy);
  sessionStorage.removeItem('tatsu-web-credentials');
}catch{credentialNotice='認証情報の保存先を開けませんでした。ブラウザーのサイト保存設定を確認してください。';}
async function importPairing(code){
  try{
    $('#pairing-status').hidden=false;$('#pairing-status').textContent='Androidの設定を引き継いでいます…';
    const response=await fetch('/api/web-pairing?operation=redeem',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({code}),cache:'no-store',redirect:'error',signal:AbortSignal.timeout(15_000)});
    if(!response.ok)throw new Error('引き継ぎリンクを使えません。Androidの設定から、もう一度Web版を開いてください。');
    credentials=await saveCredentials(await response.json());
    importedCredentials=true;
    credentialNotice='Androidの設定を引き継ぎました。次回も入力せず使えます。';
  }catch{credentialNotice=credentials.ownerToken?'このブラウザーに保存済みの設定で使えます。':'引き継ぎを完了できませんでした。Androidの設定から、もう一度Web版を開いてください。';}
  $('#pairing-status').textContent=credentialNotice;
  credentialChannel?.postMessage('changed');
}
if(pairingCode)await importPairing(pairingCode);
let alarms=load(localStorage,'tatsu-web-alarms',[]),devices=[],history=[],weather=null;
let mascot=null,capture=null,busy=false,generation=0,phase='IDLE',tab='home',abort=null,toastTimer,confirmResolve=null;
let recording=false,utterance=null,alarmAudio=null,alarmInterval=null,wakeLock=null,updateReady=false,registration=null;
const labels={IDLE:['待機','話しかけてね'],LISTENING:['聞き取り中','きいてるよ'],THINKING:['考え中','ちょっと待ってね'],CONFIRMING:['確認中','確認してね'],EXECUTING:['操作中','操作しているよ'],SPEAKING:['お話し中','おはなししてるよ'],ERROR:['エラー','もう一度ためしてね']};

function toast(text){$('#toast').textContent=text;$('#toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('#toast').hidden=true,6500);}
function save(storage,key,value){try{storage.setItem(key,JSON.stringify(value));return true;}catch{toast('ブラウザーの保存容量が不足しています。この画面を閉じると設定が失われる場合があります。');return false;}}
function setPhase(next){phase=next;mascot?.setPhase(next);const [status,caption]=labels[next];$('#voice-state').textContent=status;$('#mascot-caption').textContent=caption;}
function setBusy(value){busy=value;$('#listen').disabled=value;$('#chat-form button[type="submit"]').disabled=value;$('#compare-form button').disabled=value;$('#cancel').hidden=!value;$('#stop-recording').hidden=!recording;}
function setError(error){const text=error?.name==='NotAllowedError'?'マイクの使用を許可してください。Safariのサイト設定から変更できます。':error?.message||String(error);$('#voice-error').textContent=text;$('#voice-error').hidden=false;setPhase('ERROR');}
function showTab(name){tab=name;document.querySelectorAll('[data-page]').forEach(page=>page.hidden=page.dataset.page!==name);document.querySelectorAll('[data-tab]').forEach(button=>{if(button.dataset.tab===name)button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');});mascot?.setActive(name==='home');if(name==='alarms')renderAlarms();}
document.querySelectorAll('[data-tab]').forEach(button=>button.addEventListener('click',()=>showTab(button.dataset.tab)));
$('#go-talk').onclick=()=>showTab('home');
function stopTts(){if(utterance){utterance.onstart=utterance.onend=utterance.onerror=null;utterance=null;}window.speechSynthesis?.cancel();$('#stop-speaking').hidden=true;if(phase==='SPEAKING')setPhase('IDLE');}
function primeTts(){if(!settings.readAloud||!window.speechSynthesis)return;const silent=new SpeechSynthesisUtterance(' ');silent.volume=0;window.speechSynthesis.speak(silent);}
function cancel(){generation++;abort?.abort();abort=null;capture?.cancel();capture=null;recording=false;stopTts();if(confirmResolve)finishConfirmation(false);setBusy(false);setPhase('IDLE');releaseWakeLock();}
$('#cancel').onclick=cancel;$('#stop-recording').onclick=()=>capture?.stop();$('#stop-speaking').onclick=stopTts;

const errorMessages={backend_auth_not_configured:'サーバーのBackend認証設定が未完了です。',unauthorized:'設定でBackend認証トークンを確認してください。',usage_limits_not_configured:'サーバーの利用上限設定が未完了です。',usage_limits_unavailable:'サーバーの利用状況を確認できません。少し待って再試行してください。',daily_request_limit:'今日の利用上限に達しました。',request_rate_limit:'短時間の利用上限に達しました。少し待ってください。'};
async function api(path,body,signal){
  const token=credentials.ownerToken?.trim();if(!token||/\s/.test(token))throw new Error('設定からBackend認証トークンを入力してください。');
  const response=await fetch('/api/'+path,{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify(body),redirect:'error',cache:'no-store',signal});
  const json=await response.json().catch(()=>({}));
  if(!response.ok)throw new Error(errorMessages[json.error]||json.message||`サーバーとの通信に失敗しました（HTTP ${response.status}）`);
  return json;
}
function context(){return `Known devices:\n${devices.map(d=>`- ${d.name} | type=${d.type} | id=${d.deviceId}`).join('\n')||'- none'}\nCurrent local date and time: ${new Date().toString()}\nCurrent time zone: ${Intl.DateTimeFormat().resolvedOptions().timeZone}\nCurrent alarms:\n${alarms.map(a=>`- ${a.label} | ${a.time} | id=${a.id} | enabled=${a.enabled}`).join('\n')||'- none'}\nRecent conversation:\n${history.slice(-8).map(item=>`${item.role}: ${item.text}`).join('\n')||'- none'}`;}
function message(role,text,metadata=''){
  const item=document.createElement('div');item.className='message '+role;item.textContent=text;
  if(metadata){const small=document.createElement('small');small.textContent=metadata;item.append(small);}
  $('#conversation').append(item);while($('#conversation').children.length>30)$('#conversation').firstElementChild.remove();
  history.push({role,text});history=history.slice(-12);
}
function speak(text,language='ja',session=generation){
  if(!settings.readAloud||!window.speechSynthesis||!text)return;
  stopTts();const speech=new SpeechSynthesisUtterance(text);utterance=speech;
  speech.lang=({ja:'ja-JP',en:'en-US',de:'de-DE'})[language]||'ja-JP';speech.rate=.98;
  speech.voice=window.speechSynthesis.getVoices().find(voice=>voice.lang.toLowerCase().startsWith(speech.lang.slice(0,2)))||null;
  speech.onstart=()=>{if(session===generation&&utterance===speech){setPhase('SPEAKING');$('#stop-speaking').hidden=false;}};
  const end=()=>{if(session===generation&&utterance===speech){utterance=null;$('#stop-speaking').hidden=true;setPhase('IDLE');releaseWakeLock();}};
  speech.onend=end;speech.onerror=end;
  window.speechSynthesis.speak(speech);
}
async function ask(text,path='dispatch-jev',session=++generation){
  if(!text.trim())return;$('#voice-error').hidden=true;stopTts();setBusy(true);setPhase('THINKING');
  const controller=new AbortController();abort=controller;const timer=setTimeout(()=>controller.abort(),150_000);const started=performance.now();
  try{
    const result=await api(path,{text,context:context(),answerMode:settings.answerMode},controller.signal);
    if(session!==generation)return;
    message('user',text);$('#transcript').textContent='認識：'+text;
    const reply=await handleResult(result,session);if(session!==generation)return;
    message('assistant',reply);
    $('#diagnostics').textContent=JSON.stringify({version:__APP_VERSION__,commit:__APP_COMMIT__,route:result.route,model:result.answer?.model,timings:result.timings},null,2);
    setPhase('IDLE');speak(reply,result.route?.language==='other'?(settings.language==='auto'?'ja':settings.language):result.route?.language,session);
  }catch(error){if(session===generation){if(error.name==='AbortError')setError(new Error('応答が待機時間を超えました。もう一度ためしてください。'));else setError(error);}}
  finally{clearTimeout(timer);if(session===generation){abort=null;setBusy(false);if(phase==='THINKING'||phase==='EXECUTING')setPhase('IDLE');if(phase!=='SPEAKING')releaseWakeLock();}}
}
function confirmAction(text){$('#confirmation-text').textContent=text;setPhase('CONFIRMING');$('#confirmation').showModal();return new Promise(resolve=>{confirmResolve=resolve;});}
function finishConfirmation(value){$('#confirmation').close();const resolve=confirmResolve;confirmResolve=null;resolve?.(value);}
$('#confirmation-no').onclick=()=>finishConfirmation(false);$('#confirmation-yes').onclick=()=>finishConfirmation(true);$('#confirmation').addEventListener('cancel',event=>{event.preventDefault();finishConfirmation(false);});
async function handleResult(result,session){
  const route=result.route||{};
  if(result.answer?.text)return result.answer.text;
  if(route.route==='weather'){await refreshWeather();if(!weather)return '天気を取得できませんでした。';return ({de:`In ${settings.place} sind es ${Math.round(weather.temperature_2m)} Grad.`,en:`It is ${Math.round(weather.temperature_2m)} degrees in ${settings.place}.`})[route.language]||`${settings.place}は${Math.round(weather.temperature_2m)}度、${weatherLabel(weather.weather_code)}だよ。`;}
  if(route.route==='device_action'){
    if(!devices.length)return '家電タブから機器を同期してね。';
    const matches=resolveDeviceTargets(route.target,route.targetType,devices);
    if(matches.length>1&&route.action==='turn_off'){
      if(!(route.confidence>=.72))return '消す対象をもう一度教えてね。';
      setPhase('EXECUTING');const replies=[];
      for(const device of matches){
        if(session!==generation)return '';
        if(!device.profile?.off){replies.push(`${device.name}はOFF操作に対応していないよ。`);continue;}
        try{
          const receipt=await deviceCommand(device,{action:'off'});
          if(receipt.accepted!==true)throw new Error('not_accepted');
          replies.push(`${device.name}へOFF指示を送信したよ。`);
        }catch(error){
          if(session!==generation||error.name==='AbortError')return '';
          replies.push(`${device.name}へのOFF指示の送信に失敗したよ。`);
        }
      }
      return replies.join('\n');
    }
    if(matches.length!==1)return '操作する家電の名前や部屋を教えてね。';
    const device=matches[0],action=route.action;
    if(!['turn_on','turn_off','set_ac'].includes(action))return 'その操作にはまだ対応していないよ。家電画面で指定してね。';
    const ac=action==='set_ac',command=action==='turn_on'?'on':'off';
    if(!ac&&!device.profile?.[command])return 'この機器にはその操作がないよ。';
    const temperature=route.temperatureC??route.parameters?.temperatureC??route.parameters?.temperature;
    if(ac&&(!Number.isInteger(temperature)||temperature<16||temperature>30||!device.infrared||device.type.toLowerCase()!=='air conditioner'))return 'エアコンと16〜30度の温度を指定してね。';
    const text=ac?`${device.name}を${temperature}度・自動モード・自動風量でONにしますか？`:`${device.name}：${device.profile[command+'Label']}を実行しますか？`;
    if(!await confirmAction(text)||session!==generation)return '操作は取り消したよ。';
    setPhase('EXECUTING');await deviceCommand(device,ac?{action:'ac',temperature,mode:1,fanSpeed:1,power:true}:{action:command});
    return `${device.name}の操作をSwitchBotが受け付けたよ。`;
  }
  if(route.route==='alarm_action'){
    if(route.action==='alarm_create'){
      const time=route.timeLocal??route.parameters?.timeLocal??route.parameters?.time;
      const match=typeof time==='string'?/(?:^|T)(\d{2}:\d{2})(?::\d{2})?(?:$|[+Z-])/.exec(time):null;
      if(!match||!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(match[1]))return '時刻を指定してね。アラーム画面からも追加できるよ。';
      showTab('alarms');$('#alarm-time').value=match[1];return `${match[1]}をアラーム画面に入れたよ。「追加して音を有効にする」で保存してね。Web版は画面を開いている間だけ鳴るよ。`;
    }
    showTab('alarms');return 'アラーム画面で変更・削除してね。Web版のアラームはこのブラウザー内だけに保存されるよ。';
  }
  return route.replyText||'もう少し具体的に教えてね。';
}
$('#chat-form').onsubmit=event=>{event.preventDefault();if(busy)return;primeTts();const text=$('#query').value.trim();if(!text)return;$('#query').value='';void ask(text);};

async function acquireWakeLock(){try{if(navigator.wakeLock&&!document.hidden)wakeLock=await navigator.wakeLock.request('screen');}catch{/* iOS may not support it. */}}
function releaseWakeLock(){void wakeLock?.release();wakeLock=null;}
async function listen(path){
  if(busy)return;if(!credentials.ownerToken){setError(new Error('設定からBackend認証トークンを入力してください。'));return;}
  stopTts();primeTts();const session=++generation;setBusy(true);$('#voice-error').hidden=true;setPhase('LISTENING');$('#voice-detail').textContent='マイクを準備しています';
  await acquireWakeLock();
  try{
    const next=await startCapture({onLevel:level=>{if(session===generation)$('#voice-detail').textContent=level>.012?'声をきいています':'話してください · 自動（日・英・独）';},onStopped:(wav,reason)=>{
      if(session!==generation)return;capture=null;recording=false;$('#stop-recording').hidden=true;
      if(!wav){setBusy(false);releaseWakeLock();if(reason==='cancelled')setPhase('IDLE');else setError(new Error('声が聞こえなかったよ。もう一度話してね。'));return;}
      setPhase('THINKING');$('#voice-detail').textContent='音声を確認しています';
      const controller=new AbortController();abort=controller;const timer=setTimeout(()=>controller.abort(),60_000);
      void api('transcribe',{audioBase64:audioBase64(wav),...(settings.language==='auto'?{}:{language:settings.language})},controller.signal).then(result=>{
        if(session!==generation)return;
        $('#voice-detail').textContent='認識言語：'+(result.languages||[]).join('・');
        if(!result.text?.trim())throw new Error('音声を認識できませんでした。');
        return ask(result.text,path,session);
      }).catch(error=>{if(session===generation){setError(error.name==='AbortError'?new Error('音声認識が時間切れになりました。'):error);setBusy(false);releaseWakeLock();}}).finally(()=>clearTimeout(timer));
    }});
    if(session!==generation){next.cancel();return;}capture=next;recording=true;setBusy(true);$('#voice-detail').textContent='話してください · 自動（日・英・独）';
  }catch(error){if(session===generation){setError(error);setBusy(false);releaseWakeLock();}}
}
$('#listen').onclick=()=>void listen('dispatch-jev');

function openSettings(){const form=$('#settings-form');for(const [name,value] of Object.entries({...settings,...credentials})){const input=form.elements.namedItem(name);if(!input)continue;if(input.type==='checkbox')input.checked=value;else input.value=value;}$('#settings-error').hidden=true;$('#settings').showModal();}
$('#settings-open').onclick=openSettings;$('#settings-close').onclick=()=>$('#settings').close();
credentialChannel=typeof BroadcastChannel==='function'?new BroadcastChannel('tatsu-credentials'):null;
credentialChannel?.addEventListener('message',async()=>{
  try{credentials=await readCredentials()||emptyCredentials();}catch{credentials=emptyCredentials();}
  if($('#settings').open)for(const name of Object.keys(credentials))$('#settings-form').elements.namedItem(name).value=credentials[name];
});
$('#clear-credentials').onclick=async()=>{
  try{
    await clearCredentials();credentials=emptyCredentials();sessionStorage.removeItem('tatsu-web-credentials');
    credentialChannel?.postMessage('changed');
    for(const name of Object.keys(credentials))$('#settings-form').elements.namedItem(name).value='';toast('保存済みの認証情報を削除しました。');
  }catch{$('#settings-error').textContent='認証情報を削除できませんでした。もう一度試してください。';$('#settings-error').hidden=false;}
};
$('#settings-form').onsubmit=async event=>{
  event.preventDefault();const data=new FormData(event.target);const ownerToken=String(data.get('ownerToken')||'').trim();
  if(/\s/.test(ownerToken)){$('#settings-error').textContent='認証トークンに空白は入れられません。';$('#settings-error').hidden=false;return;}
  settings={answerMode:String(data.get('answerMode')),language:String(data.get('language')),place:String(data.get('place')).trim()||'元浅草',latitude:Number(data.get('latitude')),longitude:Number(data.get('longitude')),readAloud:data.has('readAloud')};
  credentials={ownerToken,switchbotToken:String(data.get('switchbotToken')||'').trim(),switchbotSecret:String(data.get('switchbotSecret')||'').trim()};
  try{credentials=await saveCredentials(credentials);sessionStorage.removeItem('tatsu-web-credentials');credentialChannel?.postMessage('changed');}
  catch{$('#settings-error').textContent='認証情報を保存できません。ブラウザーのサイト保存設定を確認してください。';$('#settings-error').hidden=false;return;}
  if(!save(localStorage,'tatsu-web-settings',settings))return;
  $('#settings').close();toast('保存しました。次回も入力せず使えます。');void refreshWeather();
};
function weatherLabel(code){if(code===0)return '快晴';if(code===1)return '晴れ';if(code===2)return '一部くもり';if(code===3)return 'くもり';if([45,48].includes(code))return '霧';if(code>=51&&code<=57)return '霧雨';if(code>=61&&code<=67)return '雨';if(code>=71&&code<=77)return '雪';if(code>=80&&code<=82)return 'にわか雨';if(code>=85&&code<=86)return 'にわか雪';if(code>=95)return '雷雨';return '天気情報';}
async function refreshWeather(){
  $('#weather-place').textContent=settings.place;const lat=settings.latitude,lon=settings.longitude;
  const weatherController=new AbortController();const weatherTimeout=setTimeout(()=>weatherController.abort(),12_000);
  try{
    const response=await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,weather_code&timezone=auto`,{signal:weatherController.signal});
    if(!response.ok)throw new Error('weather unavailable');const json=await response.json();if(lat!==settings.latitude||lon!==settings.longitude)return;
    weather=json.current;$('#temperature').textContent=Math.round(weather.temperature_2m)+'°';$('#weather-label').textContent=weatherLabel(weather.weather_code);
  }catch{$('#weather-label').textContent='天気を取得できません';}finally{clearTimeout(weatherTimeout);}
}
function updateClock(){const now=new Date();$('#clock').textContent=now.toLocaleTimeString('ja-JP',{hour:'2-digit',minute:'2-digit',hour12:false});$('#date').textContent=now.toLocaleDateString('ja-JP',{month:'long',day:'numeric',weekday:'long'});}
$('#refresh').onclick=()=>{void refreshWeather();void checkUpdate();if(tab==='devices')void syncDevices();};

function switchbotBody(){if(!credentials.switchbotToken||!credentials.switchbotSecret)throw new Error('設定でSwitchBotのTokenとSecretを入力してください。');return {token:credentials.switchbotToken,secret:credentials.switchbotSecret};}
async function syncDevices(){
  const button=$('#sync-devices');button.disabled=true;
  try{const result=await api('switchbot',{...switchbotBody(),operation:'list'});devices=result.devices;renderDevices();toast(`${devices.length}台を同期しました。`);}catch(error){toast(error.message);}finally{button.disabled=false;}
}
async function deviceCommand(device,command){return api('switchbot',{...switchbotBody(),operation:'command',deviceId:device.deviceId,...command},abort?.signal);}
function renderDevices(){
  const host=$('#devices');host.replaceChildren();
  if(!devices.length){const card=document.createElement('p');card.className='notice';card.textContent='設定にSwitchBotのTokenとSecretを入力して「同期」を押してください。';host.append(card);return;}
  for(const device of devices){
    const card=document.createElement('article');card.className='card';const title=document.createElement('h3');title.textContent=device.name;const type=document.createElement('p');type.className='muted';type.textContent=device.type;card.append(title,type);
    if(device.profile){const controls=document.createElement('div');controls.className='buttons';
      for(const action of ['on','off']){if(!device.profile[action])continue;const button=document.createElement('button');button.textContent=device.profile[action+'Label'];if(action==='on')button.className='primary';
        button.onclick=async()=>{if(busy){toast('会話が終わってから操作してください。');return;}
          if(device.profile[action].command==='press'&&!await confirmAction(`${device.name}のボタンを押しますか？`)){setPhase('IDLE');return;}
          button.disabled=true;try{await deviceCommand(device,{action});toast(`${device.name}：SwitchBotが操作を受け付けました。`);}catch(error){toast(error.message);}finally{button.disabled=false;if(phase==='CONFIRMING')setPhase('IDLE');}
        };controls.append(button);
      }card.append(controls);
    }else{const unsupported=document.createElement('p');unsupported.className='muted';unsupported.textContent='この機種の操作には対応していません';card.append(unsupported);}
    if(device.infrared&&device.type.toLowerCase()==='air conditioner'){
      const form=document.createElement('form');const label=document.createElement('label');label.textContent='温度（自動モード・自動風量）';const input=document.createElement('input');input.type='number';input.min='16';input.max='30';input.step='1';input.value='24';input.required=true;label.append(input);const button=document.createElement('button');button.textContent='温度を設定してON';form.append(label,button);
      form.onsubmit=async event=>{event.preventDefault();if(busy)return;button.disabled=true;try{await deviceCommand(device,{action:'ac',temperature:Number(input.value),mode:1,fanSpeed:1,power:true});toast('SwitchBotがエアコン設定を受け付けました。');}catch(error){toast(error.message);}finally{button.disabled=false;}};card.append(form);
    }host.append(card);
  }
}
$('#sync-devices').onclick=()=>void syncDevices();

function renderAlarms(){
  const host=$('#alarms');host.replaceChildren();for(const alarm of alarms){
    const card=document.createElement('article');card.className='card';const text=document.createElement('p');text.textContent=`${alarm.time} ${alarm.label} ${alarm.daily?'毎日':''}`;
    const controls=document.createElement('div');controls.className='buttons';const toggle=document.createElement('button');toggle.textContent=alarm.enabled?'ON':'OFF';toggle.onclick=()=>{alarm.enabled=!alarm.enabled;save(localStorage,'tatsu-web-alarms',alarms);renderAlarms();};
    const remove=document.createElement('button');remove.textContent='削除';remove.onclick=()=>{alarms=alarms.filter(a=>a.id!==alarm.id);save(localStorage,'tatsu-web-alarms',alarms);renderAlarms();};controls.append(toggle,remove);card.append(text,controls);host.append(card);
  }
}
async function enableAlarmAudio(){const Context=window.AudioContext||window.webkitAudioContext;if(!Context)throw new Error('このブラウザーではアラーム音を使えません。');alarmAudio??=new Context();await alarmAudio.resume();}
function beep(){if(!alarmAudio||alarmAudio.state!=='running')return;const oscillator=alarmAudio.createOscillator(),gain=alarmAudio.createGain();oscillator.frequency.value=880;gain.gain.setValueAtTime(.15,alarmAudio.currentTime);gain.gain.exponentialRampToValueAtTime(.001,alarmAudio.currentTime+.5);oscillator.connect(gain);gain.connect(alarmAudio.destination);oscillator.start();oscillator.stop(alarmAudio.currentTime+.5);}
$('#alarm-form').onsubmit=async event=>{event.preventDefault();try{await enableAlarmAudio();const time=$('#alarm-time').value;if(!time)return;alarms.push({id:crypto.randomUUID(),time,label:$('#alarm-name').value.trim()||'アラーム',enabled:true,daily:$('#alarm-daily').checked,lastFired:''});save(localStorage,'tatsu-web-alarms',alarms);renderAlarms();toast('追加しました。この画面を開いている間だけ鳴ります。');void acquireWakeLock();}catch(error){toast(error.message);}};
function stopAlarm(){clearInterval(alarmInterval);alarmInterval=null;$('#alarm-stop').hidden=true;}
$('#alarm-stop').onclick=stopAlarm;
function checkAlarms(){if(document.hidden)return;const now=new Date(),time=now.toTimeString().slice(0,5),day=`${now.getFullYear()}-${now.getMonth()}-${now.getDate()}`;
  for(const alarm of alarms){if(!alarm.enabled||alarm.time!==time||alarm.lastFired===day)continue;alarm.lastFired=day;if(!alarm.daily)alarm.enabled=false;save(localStorage,'tatsu-web-alarms',alarms);renderAlarms();showTab('alarms');toast(`${alarm.label}の時間です`);$('#alarm-stop').hidden=false;if(!alarmInterval){beep();alarmInterval=setInterval(beep,1200);setTimeout(stopAlarm,120_000);}}
}
$('#compare-form').onsubmit=async event=>{
  event.preventDefault();if(busy)return;const text=$('#compare-query').value.trim();if(!text)return;setBusy(true);const session=++generation,controller=new AbortController();abort=controller;const timer=setTimeout(()=>controller.abort(),150_000);
  $('#compare-results').replaceChildren();
  try{await Promise.all(['dispatch','dispatch-jev'].map(async(path,index)=>{
    const card=document.createElement('article');card.className='card';const title=document.createElement('h3');title.textContent=index?'Jev → GPT-6 / 6.1':'Luna → GPT-6 / 6.1';const body=document.createElement('p');body.className='message';body.textContent='回答を待っています';card.append(title,body);$('#compare-results').append(card);
    const start=performance.now();try{const result=await api(path,{text,context:context(),answerMode:settings.answerMode},controller.signal);if(session!==generation)return;body.textContent=result.answer?.text||result.route?.replyText||`判定：${result.route?.route}（比較では操作しません）`;const meta=document.createElement('p');meta.className='muted';meta.textContent=`${result.answer?.model||result.routerModel} · ${Math.round(performance.now()-start)}ms`;card.append(meta);}catch(error){if(session===generation){body.className='error';body.textContent=error.name==='AbortError'?'時間切れになりました':error.message;}}
  }));}finally{clearTimeout(timer);if(session===generation){abort=null;setBusy(false);}}
};

async function loadMascot(){mascot?.dispose();mascot=null;$('#mascot-fallback').hidden=false;$('#mascot-retry').hidden=true;
  try{const next=await createMascot($('#mascot-canvas'),()=>{$('#mascot-fallback').hidden=true;$('#mascot-retry').hidden=true;},()=>{$('#mascot-fallback').hidden=false;$('#mascot-retry').hidden=false;});mascot=next;next.setPhase(phase);next.setActive(tab==='home');window.tatsuMascotDiagnostics=()=>next.diagnostics();}
  catch{$('#mascot-retry').hidden=false;$('#diagnostics').textContent='3Dの読み込みに失敗しました。「3Dを再試行」でやり直せます。';}
}
$('#mascot-retry').onclick=event=>{event.stopPropagation();void loadMascot();};
$('#mascot').onclick=()=>mascot?.reactToTap();
$('#mascot').onkeydown=event=>{if(event.target===event.currentTarget&&['Enter',' '].includes(event.key)){event.preventDefault();mascot?.reactToTap();}};
async function checkUpdate(){try{const response=await fetch('/version.json',{cache:'no-store'});if(!response.ok)return;const current=await response.json();if(current.version!==__APP_VERSION__||current.commit!==__APP_COMMIT__){updateReady=true;$('#update').hidden=false;}}catch{/* Offline keeps the current UI. */}}
$('#update').onclick=async()=>{if(busy||recording||phase==='SPEAKING'){toast('会話が終わってから更新してください。');return;}if(registration?.waiting)registration.waiting.postMessage('activate');else location.reload();};
if('serviceWorker' in navigator){navigator.serviceWorker.register('/sw.js',{updateViaCache:'none'}).then(value=>{registration=value;value.addEventListener('updatefound',()=>{value.installing?.addEventListener('statechange',event=>{if(event.target.state==='installed'&&navigator.serviceWorker.controller){updateReady=true;$('#update').hidden=false;}});});}).catch(()=>{});navigator.serviceWorker.addEventListener('controllerchange',()=>{if(updateReady)location.reload();});}
document.addEventListener('visibilitychange',()=>{if(document.hidden){cancel();stopAlarm();}else{updateClock();void checkUpdate();checkAlarms();}});
window.addEventListener('pagehide',()=>{cancel();stopAlarm();});
$('#version').textContent=__APP_VERSION__;$('#diagnostics').textContent=`Web ${__APP_VERSION__} · ${__APP_COMMIT__.slice(0,7)}`;
renderDevices();renderAlarms();updateClock();setInterval(updateClock,1000);setInterval(checkAlarms,1000);setInterval(()=>void checkUpdate(),60_000);void refreshWeather();void loadMascot();

if(credentialNotice){$('#pairing-status').hidden=false;$('#pairing-status').textContent=credentialNotice;}
if(importedCredentials)credentialChannel?.postMessage('changed');
function acceptSharedLink(){
  const code=new URLSearchParams(location.hash.slice(1)).get('pair');if(!code)return;
  window.history.replaceState(null,'',location.pathname+location.search);void importPairing(code);
}
window.addEventListener('hashchange',acceptSharedLink);
acceptSharedLink();
