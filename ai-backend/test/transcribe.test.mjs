process.env.AI_BACKEND_TOKEN = "endpoint-fixture-token";
import test from "node:test";
import assert from "node:assert/strict";
import { decodeAudio, transcribe, MAX_AUDIO_BYTES } from "../src/transcribe.mjs";
import handler from "../../api/transcribe.mjs";

function wav(size = 3200) {
  const b = Buffer.alloc(44 + size);
  b.write("RIFF"); b.writeUInt32LE(36 + size, 4); b.write("WAVEfmt ", 8);
  b.writeUInt32LE(16, 16); b.writeUInt16LE(1, 20); b.writeUInt16LE(1, 22);
  b.writeUInt32LE(16000, 24); b.writeUInt32LE(32000, 28);
  b.writeUInt16LE(2, 32); b.writeUInt16LE(16, 34); b.write("data", 36); b.writeUInt32LE(size, 40);
  return b;
}

test("audio is bounded and malformed WAV is rejected before a provider call", () => {
  assert.equal(decodeAudio(wav().toString("base64")).length, 3244);
  for (const audio of ["", "!!not_base64", Buffer.from("not a wav").toString("base64"), wav(960002).toString("base64")]) {
    assert.throws(() => decodeAudio(audio), { code: "invalid_audio" });
  }
  const wrong = wav(); wrong.writeUInt32LE(48000, 24);
  assert.throws(() => decodeAudio(wrong.toString("base64")), { code: "invalid_audio" });
  assert.equal(MAX_AUDIO_BYTES, 960044);
});

test("automatic transcription includes German without a fixed Japanese language", async () => {
  const previous = process.env.OPENAI_API_KEY;
  process.env.OPENAI_API_KEY = "test-key";
  try {
    const result = await transcribe({ audioBase64: wav().toString("base64") }, { fetch: async (url, options) => {
      assert.equal(url, "https://api.openai.com/v1/audio/transcriptions");
      assert.deepEqual(options.body.getAll("languages[]"), ["ja", "en", "de"]);
      assert.equal(options.body.get("language"), null);
      assert.equal(options.body.get("prompt"), null);
      assert.equal(options.body.get("file").type, "audio/wav");
      return Response.json({ text: "Guten Morgen!", languages: [{ code: "de" }] });
    }});
    assert.equal(result.text, "Guten Morgen!");
    assert.deepEqual(result.languages, ["de"]);
  } finally {
    if (previous === undefined) delete process.env.OPENAI_API_KEY;
    else process.env.OPENAI_API_KEY = previous;
  }
});

test("missing language detection is not silently reported as Japanese and empty transcripts fail", async () => {
  const previous = process.env.OPENAI_API_KEY;
  process.env.OPENAI_API_KEY = "test-key";
  try {
    const audio = { audioBase64: wav().toString("base64") };
    const result = await transcribe(audio, { fetch: async () => Response.json({ text: "Hallo", languages: [] }) });
    assert.deepEqual(result.languages, []);
    await assert.rejects(transcribe(audio, { fetch: async () => Response.json({ text: "", languages: [] }) }), { code: "empty_transcript" });
  } finally {
    if (previous === undefined) delete process.env.OPENAI_API_KEY;
    else process.env.OPENAI_API_KEY = previous;
  }
});

test("endpoint rejects non-POST and invalid audio", async () => {
  const response = { setHeader() {}, status(code) { this.code = code; return this; }, json(body) { this.body = body; } };
  await handler({ method: "GET" }, response);
  assert.equal(response.code, 405);
  await handler({ method: "POST", headers: { authorization: "Bearer endpoint-fixture-token" }, body: { audioBase64: "bad" } }, response);
  assert.equal(response.code, 400);
});


test("fixed German input sends only the selected language; unsupported hints never call a provider", async () => {
  const previous=process.env.OPENAI_API_KEY;process.env.OPENAI_API_KEY="fixture";
  try {
    await transcribe({audioBase64:wav().toString("base64"),language:"de"},{fetch:async(url,options)=>{
      assert.equal(options.body.get("language"),"de");assert.deepEqual(options.body.getAll("languages[]"),[]);
      return Response.json({text:"Guten Morgen!",languages:[{code:"de"}]});
    }});
    await assert.rejects(transcribe({audioBase64:wav().toString("base64"),language:"invented"},{fetch:()=>assert.fail("provider called")}),{code:"invalid_audio"});
  } finally { if(previous===undefined)delete process.env.OPENAI_API_KEY;else process.env.OPENAI_API_KEY=previous; }
});
