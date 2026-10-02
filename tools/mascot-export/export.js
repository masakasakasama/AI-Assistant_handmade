import * as T from './node_modules/three/build/three.module.js';
import {GLTFExporter} from './node_modules/three/examples/jsm/exporters/GLTFExporter.js';
const model=window.exportModel;
const normal=model.white.normalMap.image;
// Bake small directional fiber relief, avoiding the isotropic foam appearance.
const ctx=normal.getContext('2d'),image=ctx.getImageData(0,0,128,128);
for(let y=0;y<128;y++)for(let x=0;x<128;x++){const i=(y*128+x)*4;image.data[i]=128+(image.data[i]-128)*.65;image.data[i+1]=128+(image.data[i+1]-128)*.90;}
ctx.putImageData(image,0,0);model.white.normalMap.needsUpdate=true;
const clips=[];
for(const phase of ['IDLE','PREPARING','LISTENING','THINKING','ANSWER_READY','SPEAKING','ERROR']){
 const times=[],quats=[],headY=[],breath=[],blink=[],earL=[],earR=[],mouth=[];
 const duration=phase==='SPEAKING'?2.8:4.8;
 for(let i=0;i<=96;i++){
  const t=i/96*duration;times.push(t);const speaking=phase==='SPEAKING',thinking=phase==='THINKING',listening=phase==='LISTENING';
  const roll=(thinking?.06:listening?-.045:phase==='ERROR'?.045:phase==='PREPARING'?-.025:0)+(speaking?Math.sin(t*Math.PI*4/duration)*.023:0);
  const yaw=thinking?Math.sin(t*Math.PI*2/duration)*.055:Math.sin(t*Math.PI*2/duration)*.012;
  const q=new T.Quaternion().setFromEuler(new T.Euler(0,yaw,roll));quats.push(q.x,q.y,q.z,q.w);
  headY.push(0,1.86+(speaking?Math.sin(t*Math.PI*4/duration)*.012:0),.05);
  breath.push(.59,.65*(1+.006*Math.sin(t*Math.PI*2/duration)),.47);
  const blinkStart=phase==='SPEAKING'?2.2:3.9;const closed=t>blinkStart&&t<blinkStart+.15?Math.sin(Math.PI*(t-blinkStart)/.15):0;blink.push(1,1-.94*closed,1);
  for(const [side,arr] of [[-1,earL],[1,earR]]){const r=side*(listening?-.025:0)+.025*Math.sin(t*Math.PI*(speaking?6:2)/duration-.45);const q=new T.Quaternion().setFromEuler(new T.Euler(0,0,r));arr.push(q.x,q.y,q.z,q.w);}
  mouth.push(.055,speaking?.012+Math.max(0,Math.sin(t*Math.PI*16/duration))*.048:.001,.016);
 }
 clips.push(new T.AnimationClip(phase,duration,[new T.QuaternionKeyframeTrack('Head.quaternion',times,quats),new T.VectorKeyframeTrack('Head.position',times,headY),new T.VectorKeyframeTrack('Body.scale',times,breath),new T.VectorKeyframeTrack('EyeL.scale',times,blink),new T.VectorKeyframeTrack('EyeR.scale',times,blink),new T.QuaternionKeyframeTrack('EarL.quaternion',times,earL),new T.QuaternionKeyframeTrack('EarR.quaternion',times,earR),new T.VectorKeyframeTrack('Opening.scale',times,mouth)]));
}
window.finishExport=async()=>{
 const exporter=new GLTFExporter();
 const buffer=await exporter.parseAsync(model.root,{binary:true,animations:clips,onlyVisible:false});
 const bytes=new Uint8Array(buffer);let str='';for(let i=0;i<bytes.length;i+=32768)str+=String.fromCharCode(...bytes.subarray(i,i+32768));
 window.glbBase64=btoa(str);return {bytes:bytes.length,clips:clips.map(c=>c.name)};
};
