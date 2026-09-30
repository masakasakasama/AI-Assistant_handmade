import test from "node:test";
import assert from "node:assert/strict";
import { createResponse, outputText } from "../src/openai.mjs";

test("Responses streaming records actual first output token and completion", async () => {
  process.env.OPENAI_API_KEY = "test-key";
  const previousFetch = globalThis.fetch;
  let requestBody;
  const events = [
    { type: "response.created", response: { id: "resp_test" } },
    { type: "response.output_text.delta", delta: "Hello" },
    { type: "response.completed", response: {
      id: "resp_test", status: "completed", usage: { output_tokens: 1 },
      output: [{ type: "message", content: [{ type: "output_text", text: "Hello" }] }]
    } }
  ];
  const encoder = new TextEncoder();
  globalThis.fetch = async (_url, options) => {
    requestBody = JSON.parse(options.body);
    const stream = new ReadableStream({
      start(controller) {
        for (const event of events) controller.enqueue(encoder.encode(`data: ${JSON.stringify(event)}\n\n`));
        controller.close();
      }
    });
    return new Response(stream, { status: 200, headers: { "Content-Type": "text/event-stream" } });
  };
  try {
    const response = await createResponse({ model: "test", input: "hello" });
    assert.equal(requestBody.stream, true);
    assert.equal(outputText(response), "Hello");
    assert.equal(response.id, "resp_test");
    assert.ok(response._timings.ttftMs >= 0);
    assert.ok(response._timings.generationMs >= response._timings.ttftMs);
  } finally {
    globalThis.fetch = previousFetch;
    delete process.env.OPENAI_API_KEY;
  }
});

test("token-limit termination is reported as incomplete instead of a broken stream", async () => {
  const previousFetch = globalThis.fetch;
  const previousKey = process.env.OPENAI_API_KEY;
  process.env.OPENAI_API_KEY = "test-key";
  const encoder = new TextEncoder();
  globalThis.fetch = async () => new Response(new ReadableStream({
    start(controller) {
      const event = { type: "response.incomplete", response: {
        status: "incomplete", incomplete_details: { reason: "max_output_tokens" }, output: []
      } };
      controller.enqueue(encoder.encode(`data: ${JSON.stringify(event)}\n\n`));
      controller.close();
    }
  }), { status: 200 });
  try {
    const response = await createResponse({ model: "test" });
    assert.equal(response.status, "incomplete");
    assert.throws(() => outputText(response), /incomplete: max_output_tokens/);
  } finally {
    globalThis.fetch = previousFetch;
    if (previousKey === undefined) delete process.env.OPENAI_API_KEY;
    else process.env.OPENAI_API_KEY = previousKey;
  }
});
