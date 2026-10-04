import test from 'node:test';
import assert from 'node:assert/strict';
import {explicitCommands,alarmDate} from '../src/command-context.mjs';
import {dispatchJev} from '../src/dispatch-jev.mjs';
import {dispatch} from '../src/dispatch.mjs';
const context=`Known devices:
- 寝室のエアコン | type=Air Conditioner | id=bed
- リビングのエアコン | type=Air Conditioner | id=living
- デスク照明 | type=Color Bulb | id=lamp
Current local date and time: 2026-10-04T06:00:00+09:00
Current time zone: Asia/Tokyo
Current alarms:
- none`;
for(const execute of [dispatch,dispatchJev])test(execute.name+' preserves all compound commands in order without extra model calls',async()=>{
 const fail=async()=>{throw Error('Unexpected provider call');};
 const r=await execute({text:'寝室のエアコン消して、デスク照明も消して',context},{routeIntent:fail,routeIntentJev:fail});
 assert.equal(r.route.route,'device_batch');assert.deepEqual(r.route.steps.map(s=>[s.target,s.action]),[['bed','turn_off'],['lamp','turn_off']]);assert.equal(r.answer,null);
});
for(const text of ['Turn off the air conditioners and switch the lights on','Schalte die Klimaanlagen aus und schalte das Licht ein','寝室のエアコンを26度にして、電気つけて'])test('compound power/temperature, languages: '+text,()=>{
 const r=explicitCommands({text,context});assert.equal(r.route,'device_batch');assert.equal(r.steps.length,2);
});
test('unknown or incomplete compound step stops the complete batch with a specific clarification',()=>{
 for(const text of ['寝室のエアコン消して、書斎照明も消して','寝室のエアコン消して、照明どうかな']){
  const r=explicitCommands({text,context});assert.equal(r.route,'clarify');assert.equal(r.action,null);assert.ok(!r.steps);assert.match(r.replyText,/まだどの操作も実行していない/);
 }
});
test('quoted/conditional/negated compounds do not become direct executable batches',()=>{
 for(const text of ['「エアコン消して、照明消して」を説明して','暑くなったらエアコンつけて、照明消して','エアコン消して、照明つけないで'])assert.equal(explicitCommands({text,context}),null);
});
const alarm={route:'alarm_action',action:'alarm_create',timeLocal:'07:00'};
for(const [text,date] of [['明日の朝7時にアラームを設定して','2026-10-05'],['明後日7時に起こして','2026-10-06'],['今日7時にアラーム','2026-10-04'],['Set an alarm tomorrow at 7','2026-10-05'],['Stelle den Wecker morgen um sieben','2026-10-05'],['2026-10-12の7時に起こして','2026-10-12']])test('alarm preserves requested calendar date: '+text,()=>{assert.equal(alarmDate(alarm,{text,context}).dateLocal,date);});
test('explicit date must exist; missing reference must not silently choose today',()=>{
 assert.equal(alarmDate(alarm,{text:'2026-02-30の7時に起こして',context}).route,'clarify');
 assert.equal(alarmDate(alarm,{text:'明日7時に起こして',context:''}).route,'clarify');
});
test('browser local reference respects the user timezone at UTC date boundaries',()=>{
 const c='Current local date and time: Sat Oct 03 2026 22:00:00 GMT-0400 (Eastern Daylight Time)\nCurrent time zone: America/New_York';
 assert.equal(alarmDate(alarm,{text:'tomorrow at 7',context:c}).dateLocal,'2026-10-04');
});
test('time-only alarms retain next occurrence semantics',()=>assert.equal(alarmDate(alarm,{text:'7時にアラーム',context}).dateLocal,undefined));
