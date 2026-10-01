import test from "node:test";
import assert from "node:assert/strict";
import { validateTranscriptionLanguage } from "../src/transcription-language.mjs";

test("clearly German transcript corrects an inconsistent Japanese audio label", () => {
  const result = validateTranscriptionLanguage("Ich möchte gerne meinen Kontostand erfahren.", ["ja"]);
  assert.deepEqual(result.languages, ["de"]);
  assert.deepEqual(result.audioLanguages, ["ja"]);
  assert.equal(result.languageSource, "transcript-check");
});

test("Japanese with Latin product names keeps its audio label", () => {
  assert.deepEqual(validateTranscriptionLanguage("AnkerのUSBケーブルを使っているよ", ["ja"]).languages, ["ja"]);
  assert.deepEqual(validateTranscriptionLanguage("明日の東京の天気は？", ["ja"]).languages, ["ja"]);
});

test("short ambiguous fragments and mixed languages are not forced to German", () => {
  assert.deepEqual(validateTranscriptionLanguage("Ja", []).languages, []);
  assert.deepEqual(validateTranscriptionLanguage("USB C", ["ja"]).languages, ["ja"]);
  assert.deepEqual(validateTranscriptionLanguage("Hallo, please turn the light on", ["de", "en"]).languages, ["de", "en"]);
});

test("clear English can recover an unknown label without a Japanese fallback", () => {
  assert.deepEqual(validateTranscriptionLanguage("I would like to check my account balance", []).languages, ["en"]);
  assert.deepEqual(validateTranscriptionLanguage("I would like to check my account balance", ["en"]).languages, ["en"]);
});
