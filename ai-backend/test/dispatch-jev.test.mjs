import test from "node:test";
import assert from "node:assert/strict";
import { dispatchJev } from "../src/dispatch-jev.mjs";

function routed(route, extras = {}) {
  return {
    model: "jev-test",
    language: "ja",
    route,
    confidence: 0.9,
    action: null,
    target: null,
    temperatureC: null,
    timeLocal: null,
    referenceTimeLocal: null,
    usage: { input_tokens: 10, output_tokens: 0 },
    ...extras
  };
}

test("Jev simple_chat routes to Luna after Jev", async () => {
  const result = await dispatchJev({ text: "ドイツの首都は？" }, {
    routeIntentJev: async () => routed("simple_chat"),
    answerSimple: async input => {
      assert.equal(input.language, "ja");
      return { model: "gpt-5.6-luna", text: "ベルリンです", usage: { output_tokens: 3 } };
    }
  });
  assert.equal(result.answer.model, "gpt-5.6-luna");
  assert.equal(result.calls.length, 2);
  assert.ok(result.timings.totalMs >= result.timings.routerMs);
  assert.ok(result.timings.unaccountedMs >= 0);
  assert.ok(result.timings.timestamps.server_t3_routing_complete != null);
  assert.equal(result.timings.timingError, null);
});

test("Jev deep_reasoning routes to Sol high", async () => {
  const result = await dispatchJev({ text: "比較して" }, {
    routeIntentJev: async () => routed("deep_reasoning"),
    reason: async input => {
      assert.equal(input.effort, "high");
      return { model: "gpt-5.6-sol", text: "比較結果", usage: { output_tokens: 8 } };
    }
  });
  assert.equal(result.answer.model, "gpt-5.6-sol");
  assert.equal(result.calls.length, 2);
});

test("Jev physical routes stay structured for Android and do not call an LLM", async () => {
  const result = await dispatchJev({ text: "エアコンを26度にして" }, {
    routeIntentJev: async () => routed("device_action", {
      action: "set_ac", target: "寝室", temperatureC: 26
    }),
    answerSimple: async () => { throw new Error("Unexpected Luna call"); },
    reason: async () => { throw new Error("Unexpected Sol call"); }
  });
  assert.equal(result.answer, null);
  assert.equal(result.route.action, "set_ac");
  assert.equal(result.route.temperatureC, 26);
  assert.equal(result.calls.length, 1);
});

test("Jev clarify uses a local fixed prompt", async () => {
  const result = await dispatchJev({}, {
    routeIntentJev: async () => routed("clarify")
  });
  assert.equal(result.answer.model, "local");
  assert.match(result.answer.text, /具体的/);
});
