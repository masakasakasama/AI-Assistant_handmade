import {test,expect} from '@playwright/test';

async function fixture(page){
  await page.route('https://api.open-meteo.com/**',route=>route.fulfill({json:{current:{temperature_2m:23,weather_code:0}}}));
  await page.addInitScript(()=>{
    if(!localStorage.getItem('tatsu-web-settings'))localStorage.setItem('tatsu-web-settings',JSON.stringify({readAloud:false}));
    if(!sessionStorage.getItem('tatsu-web-credentials'))sessionStorage.setItem('tatsu-web-credentials',JSON.stringify({ownerToken:'test-owner-token',switchbotToken:'test-switchbot-token',switchbotSecret:'test-switchbot-secret'}));
  });
}
test('mobile home renders and the actual GLB moves in idle and speaking',async({page},testInfo)=>{
  await fixture(page);await page.goto('/');await expect(page.locator('#temperature')).toHaveText('23°');
  await expect.poll(()=>page.evaluate(()=>window.tatsuMascotDiagnostics?.()?.frames||0),{timeout:25_000}).toBeGreaterThan(4);
  await expect(page.locator('#mascot-fallback')).toBeHidden();
  await expect(page.locator('.mascot-name')).toHaveText('luluちゃん');
  const hero=await page.locator('#mascot').boundingBox();
  expect(hero.width).toBeGreaterThan(Math.min(page.viewportSize().width*.75,470));
  expect(hero.y).toBeLessThan(page.viewportSize().width>=800?170:150);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
  const first=await page.locator('#mascot').screenshot();await page.waitForTimeout(950);const second=await page.locator('#mascot').screenshot();expect(first.equals(second)).toBe(false);
  await page.screenshot({path:testInfo.outputPath('web-home.png'),fullPage:true});
  await page.route('**/api/dispatch-jev',route=>route.fulfill({json:{route:{route:'simple_chat',language:'de'},answer:{model:'fixture',text:'Guten Morgen!'},timings:{totalMs:10}}}));
  await page.evaluate(()=>{window.speechSynthesis.speak=speech=>{speech.onstart?.();};});
  await page.getByRole('button',{name:'設定',exact:true}).click();await page.locator('[name="readAloud"]').check();await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.locator('#settings')).toBeHidden();
  await page.locator('#query').fill('Guten Morgen');await page.getByRole('button',{name:'送信',exact:true}).click();
  await expect(page.locator('#conversation')).toContainText('Guten Morgen!');await expect.poll(()=>page.evaluate(()=>window.tatsuMascotDiagnostics?.()?.phase)).toBe('SPEAKING');
  await expect.poll(()=>page.evaluate(()=>window.tatsuMascotDiagnostics().mouthOpening),{intervals:[50]}).toBeGreaterThan(.6);
  await expect.poll(()=>page.evaluate(()=>window.tatsuMascotDiagnostics().mouthOpening),{intervals:[50]}).toBeLessThan(.05);
  const speakingFirst=await page.locator('#mascot').screenshot();await page.waitForTimeout(650);const speakingSecond=await page.locator('#mascot').screenshot();expect(speakingFirst.equals(speakingSecond)).toBe(false);
  await page.getByRole('button',{name:'読み上げを止める'}).click();await expect(page.locator('#voice-state')).toHaveText('待機');
  await expect.poll(()=>page.evaluate(()=>window.tatsuMascotDiagnostics().mouthOpening)).toBe(0);
});

test('settings persist across reload without putting plaintext credentials into localStorage',async({page})=>{
  await fixture(page);await page.goto('/');await page.getByRole('button',{name:'設定',exact:true}).click();
  await page.locator('[name="language"]').selectOption('de');await page.locator('[name="place"]').fill('Berlin');await page.locator('[name="ownerToken"]').fill('session-only-secret');await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.locator('#settings')).toBeHidden();
  expect(await page.evaluate(()=>JSON.stringify({...localStorage}))).not.toContain('session-only-secret');
  await page.reload();await page.getByRole('button',{name:'設定',exact:true}).click();
  await expect(page.locator('[name="ownerToken"]')).toHaveValue('session-only-secret');
  await expect(page.locator('[name="language"]')).toHaveValue('de');
  await expect(page.locator('[name="place"]')).toHaveValue('Berlin');
  await page.getByRole('button',{name:'認証情報を消す'}).click();expect(await page.evaluate(()=>sessionStorage.getItem('tatsu-web-credentials'))).toBeNull();
  await expect(page.locator('[name="ownerToken"]')).toHaveValue('');
});

test('shared Android pairing needs no input, persists after tab closure, and deletion clears future tabs',async({page,context})=>{
  const code='A'.repeat(43),credentials={ownerToken:'paired-owner',switchbotToken:'paired-switch-token',switchbotSecret:'paired-secret'};
  let redemptions=0;
  await context.route('https://api.open-meteo.com/**',route=>route.fulfill({json:{current:{temperature_2m:23,weather_code:0}}}));
  await context.route('**/api/web-pairing?operation=redeem',route=>{
    expect(route.request().postDataJSON()).toEqual({code});redemptions++;
    if(redemptions>1)return route.fulfill({status:410,json:{error:'pairing_expired'}});
    return route.fulfill({json:credentials});
  });
  await page.goto('/#pair='+code);await expect(page.locator('#pairing-status')).toContainText('引き継ぎました');
  expect(new URL(page.url()).hash).toBe('');
  expect(await page.evaluate(()=>JSON.stringify({...localStorage}))).not.toContain('paired-owner');
  const vault=await page.evaluate(()=>new Promise(resolve=>{
    const opening=indexedDB.open('tatsu-credentials',1);
    opening.onsuccess=()=>{const db=opening.result,request=db.transaction('vault').objectStore('vault').get('credentials');request.onsuccess=()=>{resolve({extractable:request.result.key.extractable,raw:new TextDecoder().decode(request.result.data)});db.close();};};
  }));
  expect(vault.extractable).toBe(false);expect(vault.raw).not.toContain('paired-owner');
  await page.close();const reopened=await context.newPage();await reopened.goto('/');
  await reopened.goto('/#pair='+code);await expect(reopened.locator('#pairing-status')).toContainText('保存済みの設定');
  await reopened.getByRole('button',{name:'設定',exact:true}).click();
  await expect(reopened.locator('[name="ownerToken"]')).toHaveValue(credentials.ownerToken);
  await reopened.getByText('SwitchBot',{exact:true}).click();
  await expect(reopened.locator('[name="switchbotToken"]')).toHaveValue(credentials.switchbotToken);
  await expect(reopened.locator('[name="switchbotSecret"]')).toHaveValue(credentials.switchbotSecret);
  await reopened.getByRole('button',{name:'閉じる',exact:true}).click();
  let synced=false;
  await context.route('**/api/switchbot',route=>{
    expect(route.request().headers().authorization).toBe('Bearer '+credentials.ownerToken);
    const body=route.request().postDataJSON();expect(body.token).toBe(credentials.switchbotToken);expect(body.secret).toBe(credentials.switchbotSecret);synced=true;
    return route.fulfill({json:{devices:[]}});
  });
  await reopened.locator('[data-tab="devices"]').click();await reopened.getByRole('button',{name:'同期',exact:true}).click();await expect.poll(()=>synced).toBe(true);
  await reopened.getByRole('button',{name:'設定',exact:true}).click();await reopened.getByRole('button',{name:'認証情報を消す'}).click();await expect(reopened.locator('[name="ownerToken"]')).toHaveValue('');
  const another=await context.newPage();await another.goto('/');await another.getByRole('button',{name:'設定',exact:true}).click();await expect(another.locator('[name="ownerToken"]')).toHaveValue('');
  expect(redemptions).toBe(2);
});

test('expired shared links remove the capability from the URL and explain how to retry',async({page})=>{
  await page.route('**/api/web-pairing?operation=redeem',route=>route.fulfill({status:410,json:{error:'pairing_expired'}}));
  await page.goto('/#pair='+'B'.repeat(43));await expect(page.locator('#pairing-status')).toContainText('もう一度Web版を開いて');
  expect(new URL(page.url()).hash).toBe('');
});

test('authentication failures are actionable and never say an operation succeeded',async({page})=>{
  await fixture(page);await page.route('**/api/dispatch-jev',route=>route.fulfill({status:503,json:{error:'backend_auth_not_configured'}}));await page.goto('/');
  await page.locator('#query').fill('こんにちは');await page.getByRole('button',{name:'送信',exact:true}).click();await expect(page.locator('#voice-error')).toContainText('サーバーのBackend認証設定が未完了');await expect(page.locator('#listen')).toBeEnabled();
});

test('manual vacuum controls use safe logical actions and comparison never executes devices',async({page})=>{
  await fixture(page);const operations=[];
  await page.route('**/api/switchbot',async route=>{const body=route.request().postDataJSON();operations.push(body);await route.fulfill({json:body.operation==='list'?{devices:[{deviceId:'cleaner',name:'K10+ Pro',type:'K10+ Pro',profile:{on:{command:'start'},off:{command:'stop'},onLabel:'掃除開始',offLabel:'停止'}},{deviceId:'hub',name:'Hub 2',type:'Hub 2',profile:null}]}:{accepted:true,deviceId:'cleaner',command:'start'}});});
  await page.goto('/');await page.locator('[data-tab="devices"]').click();await page.getByRole('button',{name:'同期',exact:true}).click();await expect(page.locator('#devices')).toContainText('この機種の操作には対応していません');await page.getByRole('button',{name:'掃除開始',exact:true}).click();
  expect(operations.at(-1).action).toBe('on');expect(operations.at(-1).command).toBeUndefined();
  await page.route('**/api/dispatch*',route=>route.fulfill({json:{route:{route:'device_action',target:'cleaner',action:'turn_on'},routerModel:'fixture'}}));
  await page.locator('[data-tab="ai"]').click();await page.locator('#compare-query').fill('掃除して');await page.getByRole('button',{name:'回答を比較',exact:true}).click();await expect(page.locator('#compare-results')).toContainText('比較では操作しません');expect(operations.filter(op=>op.operation==='command')).toHaveLength(1);
});

test('voice action requires confirmation and cancellation does not send a command',async({page})=>{
  await fixture(page);let commands=0;
  await page.route('**/api/switchbot',route=>{const body=route.request().postDataJSON();if(body.operation==='command')commands++;return route.fulfill({json:{devices:[{deviceId:'lamp',name:'照明',type:'Color Bulb',profile:{on:{command:'turnOn'},off:{command:'turnOff'},onLabel:'ON',offLabel:'OFF'}}]}});});
  await page.route('**/api/dispatch-jev',route=>route.fulfill({json:{route:{route:'device_action',target:'照明',action:'turn_on',language:'ja'},routerModel:'fixture'}}));
  await page.goto('/');await page.locator('[data-tab="devices"]').click();await page.getByRole('button',{name:'同期',exact:true}).click();await expect(page.locator('#devices')).toContainText('照明');await page.locator('[data-tab="home"]').click();
  await page.locator('#query').fill('照明つけて');await page.getByRole('button',{name:'送信',exact:true}).click();await expect(page.locator('#confirmation')).toBeVisible();await page.getByRole('button',{name:'やめる',exact:true}).click();await expect(page.locator('#conversation')).toContainText('取り消した');expect(commands).toBe(0);
});

test('home uses only the fast route without model names and generic OFF reaches both matching devices',async({page})=>{
  await fixture(page);const commands=[];
  const devices=[
    {deviceId:'bed',name:'寝室のエアコン',type:'Air Conditioner',infrared:true,profile:{off:{command:'turnOff'}}},
    {deviceId:'living',name:'リビングのエアコン',type:'Air Conditioner',infrared:true,profile:{off:{command:'turnOff'}}},
    {deviceId:'lamp',name:'照明',type:'Color Bulb',profile:{off:{command:'turnOff'}}}
  ];
  await page.route('**/api/switchbot',route=>{
    const body=route.request().postDataJSON();
    if(body.operation==='command')commands.push(body);
    return route.fulfill({json:body.operation==='list'?{devices:[...devices,devices[0]]}:{accepted:true}});
  });
  await page.route('**/api/dispatch-jev',route=>route.fulfill({json:{route:{route:'device_action',target:'エアコン',targetType:'air_conditioner',action:'turn_off',confidence:.99},routerModel:'typesafe/jev-1.13'}}));
  await page.route('**/api/dispatch',()=>{throw new Error('Home must not call the Luna route');});
  await page.goto('/');await expect(page.locator('#listen')).toHaveText('🎙 話す');await expect(page.locator('#page-home')).not.toContainText(/Jev|Luna|typesafe/);
  await page.locator('[data-tab="devices"]').click();await page.getByRole('button',{name:'同期',exact:true}).click();await expect(page.locator('#devices')).toContainText('寝室のエアコン');await page.locator('[data-tab="home"]').click();
  await page.locator('#query').fill('エアコン消して');await page.getByRole('button',{name:'送信',exact:true}).click();
  await expect(page.locator('#conversation')).toContainText('リビングのエアコンへOFF指示を送信');
  expect(commands.map(body=>body.deviceId)).toEqual(['bed','living']);expect(commands.every(body=>body.action==='off')).toBe(true);
  await expect(page.locator('#confirmation')).toBeHidden();await expect(page.locator('#page-home')).not.toContainText(/Jev|Luna|typesafe/);
});

test('batch OFF retains scope, reports partial failure, and does not guess unknown names or low confidence',async({page})=>{
  await fixture(page);const commands=[];
  const devices=[
    {deviceId:'bed-a',name:'寝室のエアコン',type:'Air Conditioner',infrared:true,profile:{off:{command:'turnOff'}}},
    {deviceId:'bed-b',name:'寝室のエアコン',type:'Air Conditioner',infrared:true,profile:{off:{command:'turnOff'}}},
    {deviceId:'living',name:'リビングのエアコン',type:'Air Conditioner',infrared:true,profile:{off:{command:'turnOff'}}}
  ];
  await page.route('**/api/switchbot',route=>{
    const body=route.request().postDataJSON();if(body.operation==='command')commands.push(body);
    if(body.deviceId==='bed-a')return route.fulfill({status:502,json:{error:'switchbot_unavailable'}});
    return route.fulfill({json:body.operation==='list'?{devices}:{accepted:true}});
  });
  await page.route('**/api/dispatch-jev',route=>{
    const text=route.request().postDataJSON().text;
    return route.fulfill({json:{route:{route:'device_action',target:text==='unknown'?'書斎のエアコン':'寝室のエアコン',targetType:'air_conditioner',action:'turn_off',confidence:text==='uncertain'?.2:.99}}});
  });
  await page.goto('/');await page.locator('[data-tab="devices"]').click();await page.getByRole('button',{name:'同期',exact:true}).click();await expect(page.locator('#devices')).toContainText('リビング');await page.locator('[data-tab="home"]').click();
  async function ask(text){await page.locator('#query').fill(text);await page.getByRole('button',{name:'送信',exact:true}).click();await expect(page.getByRole('button',{name:'送信',exact:true})).toBeEnabled();}
  await ask('寝室のエアコン消して');await expect(page.locator('#conversation')).toContainText('OFF指示の送信に失敗');await expect(page.locator('#conversation')).toContainText('OFF指示を送信した');
  expect(commands.map(body=>body.deviceId)).toEqual(['bed-a','bed-b']);
  await ask('unknown');await ask('uncertain');expect(commands).toHaveLength(2);
});

test('alarm storage and screen-only limitation are visible',async({page})=>{
  await fixture(page);await page.goto('/');await page.locator('[data-tab="alarms"]').click();await expect(page.locator('#page-alarms')).toContainText('この画面を開いている間だけ');await page.locator('#alarm-time').fill('07:30');await page.locator('#alarm-name').fill('おはよう');await page.getByRole('button',{name:'追加して音を有効にする'}).click();await expect(page.locator('#alarms')).toContainText('07:30');expect(await page.evaluate(()=>JSON.parse(localStorage.getItem('tatsu-web-alarms'))[0].time)).toBe('07:30');await page.getByRole('button',{name:'削除',exact:true}).click();await expect(page.locator('#alarms')).toBeEmpty();
});

test('a newer deployed version prompts an update',async({page})=>{
  await fixture(page);await page.route('**/version.json',route=>route.fulfill({json:{version:'9.9.9',commit:'future'}}));await page.goto('/');await page.getByRole('button',{name:'更新',exact:true}).click();await expect(page.locator('#update')).toBeVisible();
});


test('German automatic voice input sends PCM WAV and dispatches the original German text',async({page})=>{
  await fixture(page);
  await page.addInitScript(()=>{
    const Audio=window.AudioContext||window.webkitAudioContext;
    Object.defineProperty(MediaDevices.prototype,'getUserMedia',{configurable:true,value:async()=>{
      const context=new Audio();await context.resume();const oscillator=context.createOscillator(),gain=context.createGain(),destination=context.createMediaStreamDestination();
      oscillator.frequency.value=440;gain.gain.value=.2;oscillator.connect(gain);gain.connect(destination);oscillator.start();
      destination.stream.getAudioTracks()[0].addEventListener('ended',()=>{oscillator.stop();void context.close();});return destination.stream;
    }});
  });
  let heard=false,dispatched=false;
  await page.route('**/api/transcribe',route=>{
    const body=route.request().postDataJSON();expect(body.language).toBeUndefined();const audio=Buffer.from(body.audioBase64,'base64');expect(audio.toString('ascii',0,4)).toBe('RIFF');expect(audio.readUInt32LE(24)).toBe(16000);expect(audio.length).toBeGreaterThan(3000);heard=true;
    return route.fulfill({json:{text:'Wie ist das Wetter?',languages:['de']}});
  });
  await page.route('**/api/dispatch-jev',route=>{expect(route.request().postDataJSON().text).toBe('Wie ist das Wetter?');dispatched=true;return route.fulfill({json:{route:{route:'simple_chat',language:'de'},answer:{model:'fixture',text:'Heute ist es sonnig.'}}});});
  await page.goto('/');await page.locator('#listen').click();await expect(page.locator('#stop-recording')).toBeVisible();await page.waitForTimeout(1500);await page.locator('#stop-recording').click();await expect(page.locator('#conversation')).toContainText('Heute ist es sonnig.');expect(heard&&dispatched).toBe(true);await expect(page.locator('#listen')).toBeEnabled();
});

test('microphone refusal leaves text chat usable',async({page})=>{
  await fixture(page);await page.addInitScript(()=>{Object.defineProperty(MediaDevices.prototype,'getUserMedia',{configurable:true,value:async()=>{throw new DOMException('Denied','NotAllowedError');}});});
  await page.goto('/');await page.locator('#listen').click();await expect(page.locator('#voice-error')).toContainText('マイクの使用を許可');await expect(page.locator('#listen')).toBeEnabled();await expect(page.getByRole('button',{name:'送信',exact:true})).toBeEnabled();
});


test('petting works without credentials and preserves the voice phase',async({page},testInfo)=>{
  await page.goto('/');
  await expect.poll(()=>page.evaluate(()=>window.tatsuMascotDiagnostics?.()?.frames||0),{timeout:25_000}).toBeGreaterThan(4);
  const mascot=page.getByRole('button',{name:'luluちゃんをなでる'});
  const before=await page.locator('#mascot-canvas').screenshot();
  await mascot.click();
  await expect.poll(()=>page.evaluate(()=>window.tatsuMascotDiagnostics().tapCount)).toBe(1);
  await expect.poll(()=>page.evaluate(()=>window.tatsuMascotDiagnostics().tapLift),{intervals:[30]}).toBeGreaterThan(.15);
  const jumped=await page.locator('#mascot-canvas').screenshot();expect(before.equals(jumped)).toBe(false);
  await page.screenshot({path:testInfo.outputPath('web-tap.png')});
  await mascot.focus();await page.keyboard.press('Enter');
  expect(await page.evaluate(()=>window.tatsuMascotDiagnostics().tapCount)).toBe(2);
  expect(await page.evaluate(()=>window.tatsuMascotDiagnostics().phase)).toBe('IDLE');
  await expect(page.locator('#voice-error')).toBeHidden();
  await expect.poll(()=>page.evaluate(()=>window.tatsuMascotDiagnostics().tapLift),{timeout:3000}).toBeLessThan(.001);
});
