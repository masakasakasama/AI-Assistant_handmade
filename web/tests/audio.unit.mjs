import test from 'node:test';
import assert from 'node:assert/strict';
import { pcmWav,audioBase64 } from '../audio.js';
import { decodeAudio } from '../../ai-backend/src/transcribe.mjs';
test('44.1kHz and 48kHz browser audio becomes backend-compatible 16kHz mono PCM16',()=>{
  for(const sampleRate of [44100,48000,16000]){
    const input=Float32Array.from({length:sampleRate},(_,i)=>.5*Math.sin(i*2*Math.PI*440/sampleRate));
    const bytes=pcmWav([input.subarray(0,10000),input.subarray(10000)],sampleRate);
    assert.equal(bytes.length,32044);const decoded=decodeAudio(audioBase64(bytes));assert.equal(decoded.length,bytes.length);
    const view=new DataView(bytes.buffer);let squared=0;for(let i=44;i<bytes.length;i+=2)squared+=view.getInt16(i,true)**2;
    const rms=Math.sqrt(squared/16000)/32768;assert.ok(rms>.33&&rms<.37,`rms=${rms}`);
  }
});
test('recorded audio is clipped to the provider input bound and never wraps PCM amplitude',()=>{
  const bytes=pcmWav([new Float32Array(16000*31).fill(2)],16000);
  assert.equal(bytes.length,44+16000*29*2);assert.equal(new DataView(bytes.buffer).getInt16(44,true),32767);assert.equal(decodeAudio(audioBase64(bytes)).length,bytes.length);
});
