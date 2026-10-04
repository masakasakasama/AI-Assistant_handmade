import test from 'node:test';
import assert from 'node:assert/strict';
import {explicitDeviceCommand} from '../src/device-command.mjs';
import {dispatchJev} from '../src/dispatch-jev.mjs';
import {dispatch} from '../src/dispatch.mjs';
import {parseJevRouteResponse} from '../src/jev.mjs';
const context=`Known devices:
- 寝室のエアコン | type=Air Conditioner | id=bed
- リビングのエアコン | type=Air Conditioner | id=living
- デスク照明 | type=Color Bulb | id=lamp
- 机のプラグ | type=Plug Mini | id=plug
- 扇風機 | type=Fan | id=fan
Current alarms:
- none`;
const commands=[
 ['エアコン消して。','turn_off','エアコン'],['エアコンつけて','turn_on','エアコン'],
 ['寝室のエアコンを切ってください','turn_off','bed'],
 ['電気つけて','turn_on','照明'],['デスク照明を消して','turn_off','lamp'],
 ['プラグをオフにして','turn_off','プラグ'],['扇風機つけて','turn_on','fan'],
 ['Turn off the air conditioners','turn_off','エアコン'],['Switch the lights on','turn_on','照明'],
 ['Please turn off the plug','turn_off','plug'],['Schalte die Klimaanlage aus','turn_off','エアコン'],
 ['Bitte schalte das Licht ein','turn_on','照明'],['エアコンを26度にして','set_ac','エアコン']
];
for(const [text,action,target] of commands)test('known imperative: '+text,()=>{
 const result=explicitDeviceCommand({text,context});assert.ok(result);assert.equal(result.action,action);assert.equal(result.target,target);assert.equal(result.confidence,1);
});
for(const text of ['エアコンつけないで','エアコン消していい？','エアコンを消したらどうなる','もし暑ければエアコンつけて','「エアコン消して」と言った','エアコンつけて、照明消して','書斎のエアコン消して','架空照明消して','Turn off the air conditioner if it gets hot','Do not turn off the lights','Can you turn off the lights?','Schalte das Licht nicht aus'])test('does not guess: '+text,()=>assert.equal(explicitDeviceCommand({text,context}),null));
for(const execute of [dispatchJev,dispatch])test('complete known commands bypass classification, prose generation and provider failure: '+execute.name,async()=>{
 const result=await execute({text:'エアコン消して',context},{routeIntentJev:async()=>{throw Error('must not be called');},routeIntent:async()=>{throw Error('must not be called');},reason:async()=>{throw Error('must not be called');}});
 assert.equal(result.route.action,'turn_off');assert.equal(result.route.confidence,1);assert.equal(result.answer,null);assert.equal(result.calls[0].model,'local-command');
});
function payload(routeScore=.1,operationScore=.91,targetScore=.92,route='device_action'){
 const answers=Object.fromEntries(['language','device_action','device_goal','device_target','temperature_c','alarm_action','alarm_target','new_hour','new_minute','reference_hour','reference_minute'].map(k=>[k,{choice:'none',confidence:0}]));
 Object.assign(answers,{route:{choice:route,confidence:routeScore},language:{choice:'ja'},device_action:{choice:'turn_off',confidence:operationScore},device_target:{choice:'item_0',confidence:targetScore}});
 return {answers};
}
const metadata={deviceMap:new Map([['item_0',{name:'寝室のエアコン',targetType:'air_conditioner'}]]),allowRecovery:true};
test('device confidence uses target and operation, independently of the category score',()=>{
 assert.equal(parseJevRouteResponse(payload(),metadata).confidence,.91);
 assert.equal(parseJevRouteResponse(payload(.99,.2),metadata).confidence,.2);
 assert.equal(parseJevRouteResponse(payload(.99,NaN),metadata).confidence,0);
});
test('imperative extraction recovers classification; uncertain fields stay uncertain for client confirmation and questions do not recover',()=>{
 assert.equal(parseJevRouteResponse(payload(.1,.91,.92,'clarify'),metadata).route,'device_action');
 assert.equal(parseJevRouteResponse(payload(.99,.2,.92,'clarify'),metadata).route,'device_action');
 assert.equal(parseJevRouteResponse(payload(.99,.2,.92,'clarify'),metadata).confidence,.2);
 assert.equal(parseJevRouteResponse(payload(.1,.91,.92,'clarify'),{...metadata,allowRecovery:false}).route,'clarify');
});
for(const execute of [dispatchJev,dispatch])for(const text of ['エアコン消さないで','Do not turn off the lights','Schalte das Licht nicht aus','もし暑くなったらエアコンつけて','「エアコン消して」を説明して'])test('confident model cannot execute negation, condition or quoted instructions: '+execute.name+'/'+text,async()=>{
 const fake=async()=>({route:'device_action',target:'エアコン',targetType:'air_conditioner',action:'turn_off',confidence:.99,language:'ja'});
 const result=await execute({text,context},{routeIntent:fake,routeIntentJev:fake});
 assert.equal(result.route.route,'clarify');assert.equal(result.route.action,null);assert.ok(result.answer.text);
});
