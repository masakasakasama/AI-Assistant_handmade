import test from "node:test";
import assert from "node:assert/strict";
import { dispatch } from "../src/dispatch.mjs";
import { outputText } from "../src/openai.mjs";
import { DEFAULT_REASONING_EFFORT } from "../src/reasoner.mjs";
test("simple chat answers with exactly one model call", async () => {
  let calls = 0;
  const result = await dispatch({text: "Capital of Germany?"}, {
    routeIntent: async () => { calls++; return {route: "simple_chat", replyText: "Berlin."}; },
    reason: async () => { throw new Error("Unexpected call"); }
  });
  assert.equal(calls, 1); assert.equal(result.answer.text, "Berlin.");
  assert.equal(result.calls.length, 1); assert.equal(result.timings.answerMs, 0);
});
test("clarification does not escalate", async () => {
  const result = await dispatch({}, {
    routeIntent: async () => ({route: "clarify", replyText: "Which device?"}),
    reason: async () => { throw new Error("Unexpected call"); }
  });
  assert.equal(result.answer.text, "Which device?");
});
test("physical proposals never fabricate success replies", async () => {
  const result = await dispatch({}, {
    routeIntent: async () => ({route: "device_action", _usage: {input_tokens: 30}})
  });
  assert.equal(result.answer, null); assert.equal(result.route._usage, undefined);
  assert.equal(result.calls[0].usage.input_tokens, 30);
});
test("escalation preserves context and language", async () => {
  const result = await dispatch({text: "Compare", context: "Three alternatives"}, {
    routeIntent: async () => ({route: "deep_reasoning", language: "en"}),
    reason: async input => {
      assert.equal(input.context, "Three alternatives"); assert.equal(input.language, "en");
      return {model: "test-sol", text: "Comparison", usage: {output_tokens: 5}};
    }
  });
  assert.equal(result.calls.length, 2); assert.equal(result.answer.model, "test-sol");
});
test("incomplete and missing answers fail visibly", async () => {
  assert.throws(() => outputText({status: "incomplete"}), /incomplete/);
  await assert.rejects(dispatch({}, {routeIntent: async () => ({route: "simple_chat"})}), /Missing/);
});

test("deep reasoning does not silently downgrade quality", () => {
  assert.equal(DEFAULT_REASONING_EFFORT, "high");
});
