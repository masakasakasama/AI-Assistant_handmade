import test from 'node:test';
import assert from 'node:assert/strict';
import {routeIntentJev} from '../src/jev.mjs';

const context=`Known devices:
- 寝室のエアコン | type=Air Conditioner | id=bed-a
- 寝室のエアコン | type=Air Conditioner | id=bed-b
- リビングのエアコン | type=Air Conditioner | id=living
- 照明 | type=Color Bulb | id=lamp
Current alarms:
- none
Recent conversation:
- none`;

for(const [name,action,expected] of [
  ['エアコン','turn_off','エアコン'],
  ['寝室のエアコン','turn_off','寝室のエアコン'],
  ['エアコン','turn_on',null],
  ['エアコン','set_ac',null]
])test(`Jev supplies scoped multi-device OFF choices: ${name}/${action}`,async()=>{
  const result=await routeIntentJev({text:`${name}を消して`,context},{apiKey:'fixture',fetchImpl:async(_url,options)=>{
    const request=JSON.parse(options.body),criteria=request.questions.device_target.criteria;
    const key=Object.keys(criteria).find(key=>key.startsWith('off_group_')&&criteria[key].includes(`"${name}"`));
    assert.ok(key,'Named generic/room group missing');
    assert.equal(Object.values(criteria).filter(value=>value.startsWith('All known')).length,2);
    const answers=Object.fromEntries(Object.keys(request.questions).map(name=>[name,{choice:'none',confidence:.99}]));
    Object.assign(answers,{route:{choice:'device_action',confidence:.99},language:{choice:'ja'},device_action:{choice:action},device_target:{choice:key}});
    return {ok:true,json:async()=>({answers})};
  }});
  assert.equal(result.target,expected);
  assert.equal(result.targetType,expected?'air_conditioner':null);
});
