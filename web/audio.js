export function pcmWav(chunks,sampleRate) {
  const sourceLength=chunks.reduce((sum,chunk)=>sum+chunk.length,0);
  const source=new Float32Array(sourceLength);let offset=0;
  for(const chunk of chunks){source.set(chunk,offset);offset+=chunk.length;}
  const length=Math.min(16000*29,Math.floor(source.length*16000/sampleRate));
  const buffer=new ArrayBuffer(44+length*2),view=new DataView(buffer);
  function text(offset,value){for(let i=0;i<value.length;i++)view.setUint8(offset+i,value.charCodeAt(i));}
  text(0,'RIFF');view.setUint32(4,36+length*2,true);text(8,'WAVE');text(12,'fmt ');view.setUint32(16,16,true);view.setUint16(20,1,true);view.setUint16(22,1,true);view.setUint32(24,16000,true);view.setUint32(28,32000,true);view.setUint16(32,2,true);view.setUint16(34,16,true);text(36,'data');view.setUint32(40,length*2,true);
  // Average each source-rate interval; avoids the aliasing of selecting every third sample.
  for(let i=0;i<length;i++){
    const start=i*sampleRate/16000,end=(i+1)*sampleRate/16000;let sum=0,weight=0;
    for(let j=Math.floor(start);j<Math.ceil(end)&&j<source.length;j++){const fraction=Math.min(end,j+1)-Math.max(start,j);if(fraction>0){sum+=source[j]*fraction;weight+=fraction;}}
    const value=Math.max(-1,Math.min(1,weight?sum/weight:0));view.setInt16(44+i*2,value*(value<0?32768:32767),true);
  }
  return new Uint8Array(buffer);
}
export function audioBase64(bytes){let raw='';for(let i=0;i<bytes.length;i+=8192)raw+=String.fromCharCode(...bytes.subarray(i,i+8192));return btoa(raw);}

export async function startCapture({onLevel,onStopped}) {
  if(!navigator.mediaDevices?.getUserMedia)throw new Error('このブラウザーではマイク入力を使えません。SafariのHTTPSページで開いてください。');
  const Context=window.AudioContext||window.webkitAudioContext;
  const context=new Context();await context.resume();
  let stream;
  try{stream=await navigator.mediaDevices.getUserMedia({audio:{channelCount:1,echoCancellation:true,noiseSuppression:true,autoGainControl:true}});}catch(error){await context.close();throw error;}
  const source=context.createMediaStreamSource(stream),processor=context.createScriptProcessor(4096,1,1),silent=context.createGain();silent.gain.value=0;
  const chunks=[];let ended=false,speech=false,speechMs=0,silenceMs=0,totalMs=0;
  const tracksEnded=()=>finish(true);
  stream.getAudioTracks().forEach(track=>track.addEventListener('ended',tracksEnded));
  function finish(cancelled=false) {
    if(ended)return;ended=true;clearTimeout(timeout);processor.onaudioprocess=null;source.disconnect();processor.disconnect();silent.disconnect();
    stream.getTracks().forEach(track=>{track.removeEventListener('ended',tracksEnded);track.stop();});
    const rate=context.sampleRate;void context.close();
    onStopped(cancelled?null:speech?pcmWav(chunks,rate):null,cancelled?'cancelled':speech?'complete':'silent');
  }
  processor.onaudioprocess=event=>{
    if(ended)return;
    const frame=event.inputBuffer.getChannelData(0).slice(),dt=frame.length/context.sampleRate*1000;
    chunks.push(frame);totalMs+=dt;let sum=0;for(const sample of frame)sum+=sample*sample;const rms=Math.sqrt(sum/frame.length);
    if(rms>.012){speechMs+=dt;silenceMs=0;if(speechMs>=160)speech=true;}else silenceMs+=dt;
    onLevel?.(rms);
    if(totalMs>=29_000||(speech&&silenceMs>=1100&&totalMs>1500)||(!speech&&totalMs>9000))finish();
  };
  source.connect(processor);processor.connect(silent);silent.connect(context.destination);
  const timeout=setTimeout(()=>finish(),29_000);
  return {stop:()=>finish(),cancel:()=>finish(true)};
}
