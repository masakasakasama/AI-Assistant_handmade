import test from "node:test";
import assert from "node:assert/strict";
import { createResponse, apiError, ModelTimeoutError } from "../src/openai.mjs";
import { reason } from "../src/reasoner.mjs";
import handler from "../../api/dispatch.mjs";

for (const phase of ["headers", "stream"]) {
  test(`timeout during ${phase} identifies the model and stage without retrying`, async () => {
    const previousFetch = globalThis.fetch;
    const previousKey = process.env.OPENAI_API_KEY;
    process.env.OPENAI_API_KEY = "test-key";
    let calls = 0;
    // Keep the event loop alive for AbortSignal.timeout's unref'ed timer.
    const keepAlive = setTimeout(() => {}, 1000);
    globalThis.fetch = async (_url, { signal }) => {
      calls++;
      if (phase === "headers") {
        return new Promise((_, reject) => signal.addEventListener("abort", () => reject(signal.reason), { once: true }));
      }
      return new Response(new ReadableStream({
        start(controller) {
          signal.addEventListener("abort", () => controller.error(signal.reason), { once: true });
        }
      }), { status: 200 });
    };
    try {
      await assert.rejects(createResponse({ model: "gpt-6.1-sol" }, { timeoutMs: 10, stage: "reasoning" }), error => {
        assert.ok(error instanceof ModelTimeoutError);
        assert.equal(error.model, "gpt-6.1-sol");
        assert.equal(error.stage, "reasoning");
        assert.equal(error.timeoutMs, 10);
        assert.ok(error.elapsedMs >= 0);
        const failure = apiError(error);
        assert.equal(failure.status, 504);
        assert.equal(failure.body.error, "model_timeout");
        assert.equal(failure.body.model, "gpt-6.1-sol");
        return true;
      });
      assert.equal(calls, 1);
    } finally {
      clearTimeout(keepAlive);
      globalThis.fetch = previousFetch;
      if (previousKey === undefined) delete process.env.OPENAI_API_KEY;
      else process.env.OPENAI_API_KEY = previousKey;
    }
  });
}

test("reasoning gets 90 seconds while preserving high effort and the selected model", async () => {
  const previousFetch = globalThis.fetch;
  const previousTimeout = AbortSignal.timeout;
  const previousKey = process.env.OPENAI_API_KEY;
  process.env.OPENAI_API_KEY = "test-key";
  let timeoutMs;
  AbortSignal.timeout = ms => { timeoutMs = ms; return new AbortController().signal; };
  globalThis.fetch = async (_url, options) => {
    const body = JSON.parse(options.body);
    assert.equal(body.model, "gpt-6.1-sol");
    assert.equal(body.reasoning.effort, "high");
    assert.equal(body.max_output_tokens, 8192);
    throw new DOMException("test timeout", "TimeoutError");
  };
  try {
    await assert.rejects(reason({ text: "compare" }), error => {
      assert.equal(error.stage, "reasoning");
      assert.equal(error.timeoutMs, 90_000);
      return true;
    });
    assert.equal(timeoutMs, 90_000);
  } finally {
    globalThis.fetch = previousFetch;
    AbortSignal.timeout = previousTimeout;
    if (previousKey === undefined) delete process.env.OPENAI_API_KEY;
    else process.env.OPENAI_API_KEY = previousKey;
  }
});

test("dispatch endpoint returns actionable 504 diagnostics for a router timeout", async () => {
  const previousFetch = globalThis.fetch;
  const previousKey = process.env.OPENAI_API_KEY;
  const previousLog = console.error;
  process.env.OPENAI_API_KEY = "test-key";
  globalThis.fetch = async () => { throw new DOMException("test timeout", "TimeoutError"); };
  console.error = () => {};
  const res = { status(code) { this.code = code; return this; }, json(body) { this.body = body; } };
  try {
    await handler({ method: "POST", body: { text: "compare", modelProfile: "gpt-6.1" } }, res);
    assert.equal(res.code, 504);
    assert.equal(res.body.model, "gpt-6-luna");
    assert.equal(res.body.stage, "routing");
    assert.equal(res.body.timeoutMs, 45_000);
    assert.equal(apiError(new Error("network error")).status, 500);
  } finally {
    globalThis.fetch = previousFetch;
    console.error = previousLog;
    if (previousKey === undefined) delete process.env.OPENAI_API_KEY;
    else process.env.OPENAI_API_KEY = previousKey;
  }
});
