import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';

export async function createMascot(canvas, onReady, onError) {
  const renderer = new THREE.WebGLRenderer({canvas,alpha:true,antialias:true,powerPreference:'low-power'});
  renderer.setPixelRatio(Math.min(devicePixelRatio,1.5));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.08;
  const scene = new THREE.Scene();
  const camera = new THREE.OrthographicCamera(-2.175,2.175,2.175,-2.175,.1,30);
  camera.position.set(0,2.25,7.6);camera.lookAt(0,1.42,0);
  scene.add(new THREE.HemisphereLight(0xf0f8ff,0xe9ddd4,2.1));
  const key = new THREE.DirectionalLight(0xfff4e8,3);key.position.set(-3,5,6);scene.add(key);
  const fill = new THREE.DirectionalLight(0xcde7ff,.9);fill.position.set(3,3,4);scene.add(fill);
  let phase='IDLE',active=true,disposed=false,raf=0,previous=0,t=0,clipTime=0,frames=0,tapStarted=-10,tapCount=0,tapLift=0;
  const gltf=await new GLTFLoader().loadAsync('/mascot.glb');
  const model=gltf.scene;const pivot=new THREE.Group();pivot.add(model);scene.add(pivot);
  const mixer=new THREE.AnimationMixer(model);
  const actions=new Map(gltf.animations.map(clip=>[clip.name,mixer.clipAction(clip)]));
  actions.get(phase)?.play();
  const joints=['Head','EarL','EarR'].map(name=>{
    const node=model.getObjectByName(name);if(!node)throw new Error('3D joint missing');
    return {node,rotation:node.quaternion.clone()};
  });
  const mouth=model.getObjectByName('Opening');if(!mouth)throw new Error('3D mouth missing');
  const yAxis=new THREE.Vector3(0,1,0),xAxis=new THREE.Vector3(1,0,0),zAxis=new THREE.Vector3(0,0,1),q=new THREE.Quaternion();
  const size=new ResizeObserver(()=>{const w=canvas.clientWidth,h=canvas.clientHeight;if(w&&h){renderer.setSize(w,h,false);camera.left=-2.175*w/h;camera.right=2.175*w/h;camera.updateProjectionMatrix();}});size.observe(canvas);
  const failed=()=>{active=false;cancelAnimationFrame(raf);onError();};
  const lost=event=>{event.preventDefault();failed();};canvas.addEventListener('webglcontextlost',lost);
  function render(now){
    if(disposed||!active||document.hidden){raf=0;return;}
    raf=requestAnimationFrame(render);
    if(previous&&now-previous<1000/24)return;
    const dt=previous?Math.min((now-previous)/1000,.25):0;previous=now;t+=dt;clipTime+=dt;
    mixer.update(dt);const speaking=phase==='SPEAKING';
    const progress=Math.max(0,Math.min(1,(t-tapStarted)/1.2));
    const envelope=Math.sin(Math.PI*progress),flutter=envelope*Math.sin(progress*Math.PI*8);
    tapLift=.42*Math.abs(Math.sin(Math.PI*2*progress))*envelope;
    pivot.position.y=.10*Math.sin(t*1.8)+tapLift;
    pivot.scale.set(1+.06*envelope,1-.04*envelope,1);
    pivot.rotation.set(0,THREE.MathUtils.degToRad((speaking?6:4)*Math.sin(t*1.5)),THREE.MathUtils.degToRad((speaking?8:5)*Math.sin(t*(speaking?3.2:1.5))));
    pivot.rotation.z+=THREE.MathUtils.degToRad(10*flutter);
    joints.forEach(({node,rotation},index)=>{
      node.quaternion.copy(rotation);
      if(index===0){node.quaternion.multiply(q.setFromAxisAngle(zAxis,THREE.MathUtils.degToRad(12*envelope)));node.quaternion.multiply(q.setFromAxisAngle(yAxis,THREE.MathUtils.degToRad(8*Math.sin(t*1.3))));node.quaternion.multiply(q.setFromAxisAngle(xAxis,THREE.MathUtils.degToRad((speaking?9:5)*Math.sin(t*(speaking?4:1.6)))));}
      else {node.quaternion.multiply(q.setFromAxisAngle(zAxis,THREE.MathUtils.degToRad((speaking?16:12)*Math.sin(t*2.4+index*1.5))));node.quaternion.multiply(q.setFromAxisAngle(zAxis,THREE.MathUtils.degToRad((index===1?28:-28)*flutter)));}
    });
    mouth.scale.set(.055,speaking?.035+.15*(1+Math.sin(t*9))/2:.001,.016);
    try{renderer.render(scene,camera);frames++;if(frames===2)onReady();}catch{failed();}
  }
  function resume(){previous=0;if(!disposed&&active&&!document.hidden&&!raf)raf=requestAnimationFrame(render);}
  const visibility=()=>{if(document.hidden){cancelAnimationFrame(raf);raf=0;}else resume();};document.addEventListener('visibilitychange',visibility);
  resume();
  return {
    reactToTap(){if(disposed)return;tapCount++;tapStarted=t;resume();},
    setPhase(next){if(phase===next)return;actions.get(phase)?.fadeOut(.28);phase=next;clipTime=0;actions.get(next)?.reset().fadeIn(.28).play();},
    setActive(value){active=value;if(!value){cancelAnimationFrame(raf);raf=0;}else resume();},
    diagnostics:()=>({phase,frames,active,ready:frames>=2,seconds:t,tapCount,tapLift}),
    dispose(){disposed=true;cancelAnimationFrame(raf);size.disconnect();canvas.removeEventListener('webglcontextlost',lost);document.removeEventListener('visibilitychange',visibility);mixer.stopAllAction();scene.traverse(node=>{node.geometry?.dispose();for(const material of [node.material].flat().filter(Boolean))material.dispose();});renderer.dispose();}
  };
}
