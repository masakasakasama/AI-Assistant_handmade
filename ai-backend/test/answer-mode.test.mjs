import test from "node:test";
import assert from "node:assert/strict";
import { dispatch } from "../src/dispatch.mjs";
import { dispatchJev } from "../src/dispatch-jev.mjs";
import { apiError } from "../src/openai.mjs";
import { resolveAnswerMode } from "../src/answer-mode.mjs";

for (const [mode, effort] of [["quick", "low"], ["balanced", "medium"], ["deep", "high"]]) {
  for (const [label, execute, routeKey] of [["Luna", dispatch, "routeIntent"], ["Jev", dispatchJev, "routeIntentJev"]]) {
    test(`${label} ${mode} preserves the selected effort, language and context`, async () => {
      const result = await execute({ text: "compare", context: "known facts", answerMode: mode }, {
        [routeKey]: async () => ({ route: "deep_reasoning", language: "de" }),
        reason: async input => {
          assert.equal(input.effort, effort);
          assert.equal(input.concise, mode !== "deep");
          assert.equal(input.language, "de");
          assert.equal(input.context, "known facts");
          return { model: input.model, text: "answer" };
        }
      });
      assert.equal(result.answerMode, mode);
      assert.equal(result.answer.effort, effort);
    });
  }
}

test("response mode never turns a physical proposal into a model answer or execution", async () => {
  for (const mode of ["quick", "balanced", "deep"]) {
    const result = await dispatch({ text: "turn off", answerMode: mode }, {
      routeIntent: async () => ({ route: "device_action", action: "turn_off", target: "bedroom" }),
      reason: async () => { throw new Error("No Sol for physical actions"); }
    });
    assert.equal(result.answer, null);
    assert.equal(result.route.action, "turn_off");
    assert.equal(result.calls.length, 1);
  }
});

test("invalid modes fail before any provider call", async () => {
  for (const mode of ["arbitrary", "__proto__", 3, null]) {
    await assert.rejects(dispatch({ answerMode: mode }, {
      routeIntent: async () => { throw new Error("Unexpected provider call"); }
    }), error => {
      assert.equal(apiError(error).status, 400);
      return true;
    });
  }
  assert.equal(resolveAnswerMode("deep").effort, "high");
});

test("spoken responses request low verbosity without reducing the output budget", async () => {
  const { reason } = await import("../src/reasoner.mjs");
  const previousFetch = globalThis.fetch;
  const previousKey = process.env.OPENAI_API_KEY;
  process.env.OPENAI_API_KEY = "test-key";
  globalThis.fetch = async (_url, options) => {
    const body = JSON.parse(options.body);
    assert.equal(body.text.verbosity, "low");
    assert.equal(body.reasoning.effort, "medium");
    assert.equal(body.max_output_tokens, 8192);
    assert.match(body.instructions, /Preserve correctness and uncertainty/);
    throw new Error("test stop before provider");
  };
  try {
    await assert.rejects(reason({ text: "compare", effort: "medium", concise: true }), /test stop/);
  } finally {
    globalThis.fetch = previousFetch;
    if (previousKey === undefined) delete process.env.OPENAI_API_KEY;
    else process.env.OPENAI_API_KEY = previousKey;
  }
});

test("standard requests default to medium and record the selected mode", async () => {
  for (const [execute, routeKey] of [[dispatch, "routeIntent"], [dispatchJev, "routeIntentJev"]]) {
    const result = await execute({ text: "compare" }, {
      [routeKey]: async () => ({ route: "deep_reasoning", language: "ja" }),
      reason: async input => {
        assert.equal(input.effort, "medium");
        assert.equal(input.concise, true);
        return { model: input.model, text: "answer" };
      }
    });
    assert.equal(result.answerMode, "balanced");
    assert.equal(result.answer.effort, "medium");
  }
});
