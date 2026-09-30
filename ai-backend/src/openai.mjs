import { performance } from "node:perf_hooks";

const OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses";

function apiKey() {
  const value = process.env.OPENAI_API_KEY;
  if (!value) throw new Error("OPENAI_API_KEY is not configured");
  return value;
}

export const MODEL_TIMEOUT_MS = 45_000;
export const REASONING_TIMEOUT_MS = 90_000;

export class ModelTimeoutError extends Error {
  constructor({ model, stage, timeoutMs, elapsedMs }, cause) {
    const label = stage === "reasoning" ? "回答生成" : stage === "routing" ? "分類" : "回答";
    super(`${label}（${model}）が${timeoutMs / 1000}秒の待機上限に達しました。`, { cause });
    this.name = "ModelTimeoutError";
    Object.assign(this, { model, stage, timeoutMs, elapsedMs });
  }
}

export function apiError(error) {
  if (error instanceof ModelTimeoutError) {
    return { status: 504, body: { error: "model_timeout", message: error.message,
      model: error.model, stage: error.stage, timeoutMs: error.timeoutMs, elapsedMs: error.elapsedMs } };
  }
  return { status: 500, body: { error: "internal_error", message: error instanceof Error ? error.message : String(error) } };
}

export async function createResponse(body, { timeoutMs = MODEL_TIMEOUT_MS, stage = "answer" } = {}) {
  const started = performance.now();
  const signal = AbortSignal.timeout(timeoutMs);
  try {
    return await readResponse(body, signal, started);
  } catch (error) {
    if (signal.aborted || error?.name === "TimeoutError") {
      throw new ModelTimeoutError({ model: body.model, stage, timeoutMs,
        elapsedMs: Math.round(performance.now() - started) }, error);
    }
    throw error;
  }
}

async function readResponse(body, signal, started) {
  const response = await fetch(OPENAI_RESPONSES_URL, {
    method: "POST",
    signal,
    headers: {
      "Authorization": `Bearer ${apiKey()}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ ...body, stream: true })
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`OpenAI HTTP ${response.status}: ${text}`);
  }
  if (!response.body) throw new Error("OpenAI response stream was empty");
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let completed = null;
  let ttftMs = null;
  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value ?? new Uint8Array(), { stream: !done });
    buffer = buffer.replaceAll("\r\n", "\n");
    let boundary;
    while ((boundary = buffer.indexOf("\n\n")) >= 0) {
      const block = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      const data = block.split(/\r?\n/).filter(line => line.startsWith("data:")).map(line => line.slice(5).trim()).join("\n");
      if (!data || data === "[DONE]") continue;
      let event;
      try { event = JSON.parse(data); } catch { continue; }
      if (event.type === "response.output_text.delta" && ttftMs == null) {
        ttftMs = Math.max(0, performance.now() - started);
      }
      if (event.type === "response.completed" || event.type === "response.incomplete") completed = event.response;
      if (event.type === "response.failed" || event.type === "error") {
        throw new Error(event.response?.error?.message || event.error?.message || "OpenAI stream failed");
      }
    }
    if (done) break;
  }
  if (!completed) throw new Error("OpenAI response stream ended before completion");
  return {
    ...completed,
    _timings: {
      ttftMs: ttftMs == null ? null : Math.round(ttftMs),
      generationMs: Math.round(performance.now() - started),
      firstTokenAtMs: ttftMs == null ? null : Math.round(started + ttftMs),
      completedAtMs: Math.round(performance.now())
    }
  };
}

export function outputText(response) {
  if (response.status === "incomplete") throw new Error(`OpenAI response incomplete: ${response.incomplete_details?.reason || "unknown reason"}`);
  for (const item of response.output ?? []) {
    if (item.type !== "message") continue;
    for (const content of item.content ?? []) {
      if (content.type === "output_text" && typeof content.text === "string") {
        return content.text;
      }
    }
  }
  throw new Error("OpenAI response did not contain output_text");
}
