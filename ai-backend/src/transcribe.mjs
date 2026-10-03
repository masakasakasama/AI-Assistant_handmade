import { requestLimitFailure } from "./request-limits.mjs";
import { performance } from "node:perf_hooks";
import { validateTranscriptionLanguage } from "./transcription-language.mjs";

export const TRANSCRIPTION_MODEL = process.env.TRANSCRIPTION_MODEL || "gpt-transcribe";
export const MAX_AUDIO_BYTES = 960_044; // 30 seconds, 16 kHz mono PCM16 + WAV header
export const TRANSCRIPTION_TIMEOUT_MS = 45_000;

function invalid(message) {
  const error = new Error(message);
  error.code = "invalid_audio";
  return error;
}

export function decodeAudio(audioBase64) {
  if (typeof audioBase64 !== "string" || !audioBase64.length ||
      audioBase64.length > Math.ceil(MAX_AUDIO_BYTES / 3) * 4 ||
      !/^[A-Za-z0-9+/]+={0,2}$/.test(audioBase64)) throw invalid("有効なWAV音声が必要です");
  const audio = Buffer.from(audioBase64, "base64");
  if (audio.length < 46 || audio.length > MAX_AUDIO_BYTES ||
      audio.toString("ascii", 0, 4) !== "RIFF" || audio.toString("ascii", 8, 12) !== "WAVE" ||
      audio.toString("ascii", 12, 16) !== "fmt " || audio.readUInt32LE(16) !== 16 ||
      audio.readUInt16LE(20) !== 1 || audio.readUInt16LE(22) !== 1 ||
      audio.readUInt32LE(24) !== 16000 || audio.readUInt16LE(34) !== 16 ||
      audio.toString("ascii", 36, 40) !== "data" || audio.readUInt32LE(40) !== audio.length - 44 ||
      audio.readUInt32LE(4) !== audio.length - 8 || (audio.length - 44) % 2 !== 0) {
    throw invalid("30秒以内の16kHz・モノラルPCM16 WAV音声が必要です");
  }
  return audio;
}

export async function transcribe({ audioBase64 }, dependencies = {}) {
  const audio = decodeAudio(audioBase64);
  const key = process.env.OPENAI_API_KEY;
  if (!key) throw new Error("OPENAI_API_KEY is not configured");
  const form = new FormData();
  form.append("model", TRANSCRIPTION_MODEL);
  form.append("file", new Blob([audio], { type: "audio/wav" }), "utterance.wav");
  form.append("response_format", "json");
  // All three are candidate languages. Do not set a singular language or a Japanese prompt.
  for (const language of ["ja", "en", "de"]) form.append("languages[]", language);
  const started = performance.now();
  const signal = AbortSignal.timeout(TRANSCRIPTION_TIMEOUT_MS);
  let response;
  try {
    response = await (dependencies.fetch || fetch)("https://api.openai.com/v1/audio/transcriptions", {
      method: "POST", signal, headers: { Authorization: `Bearer ${key}` }, body: form
    });
    if (!response.ok) throw new Error(`音声認識サービスのHTTPエラー: ${response.status}`);
    const result = await response.json();
    if (typeof result.text !== "string" || !result.text.trim()) {
      const error = new Error("発話を認識できませんでした。もう一度話してください");
      error.code = "empty_transcript";
      throw error;
    }
    const languages = (Array.isArray(result.languages) ? result.languages : [])
      .map(value => value?.code).filter(code => ["ja", "en", "de"].includes(code));
    return { text: result.text.trim(), ...validateTranscriptionLanguage(result.text, languages), model: TRANSCRIPTION_MODEL,
      durationMs: Math.round((audio.length - 44) / 32), elapsedMs: Math.round(performance.now() - started) };
  } catch (error) {
    if (signal.aborted || error?.name === "TimeoutError") {
      const timeout = new Error("自動音声認識が45秒でタイムアウトしました");
      timeout.code = "transcription_timeout";
      throw timeout;
    }
    throw error;
  }
}

export function transcriptionError(error) {
  const limit = requestLimitFailure(error);
  if (limit) return limit;
  return { status: error?.code === "invalid_audio" ? 400 : error?.code === "empty_transcript" ? 422 :
    error?.code === "transcription_timeout" ? 504 : 502,
    body: { error: ["invalid_audio", "empty_transcript", "transcription_timeout"].includes(error?.code) ? error.code : "transcription_error", message: "音声認識に失敗しました" } };
}
