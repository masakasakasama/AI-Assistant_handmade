import test from "node:test";
import assert from "node:assert/strict";
import { dispatch } from "../src/dispatch.mjs";
import { MODEL_PROFILES, resolveModelProfile } from "../src/model-profiles.mjs";

test("model comparison profiles map only to the fixed Luna/Sol pairs", () => {
  assert.deepEqual(resolveModelProfile("gpt-5.6"), {
    routerModel: "gpt-5.6-luna", reasoningModel: "gpt-5.6-sol"
  });
  assert.deepEqual(resolveModelProfile("gpt-6"), {
    routerModel: "gpt-6-luna", reasoningModel: "gpt-6-sol"
  });
  assert.equal(resolveModelProfile("custom/arbitrary-model"), null);
  assert.equal(resolveModelProfile("__proto__"), null);
});

test("dispatch runs the selected fixed model profile for a deep-reasoning query", async () => {
  let routerModel;
  let reasoningModel;
  const result = await dispatch({ text: "compare", modelProfile: "gpt-5.6" }, {
    routeIntent: async input => {
      routerModel = input.model;
      return { route: "deep_reasoning", language: "en" };
    },
    reason: async input => {
      reasoningModel = input.model;
      return { model: input.model, text: "done", usage: null };
    }
  });
  assert.equal(routerModel, MODEL_PROFILES["gpt-5.6"].routerModel);
  assert.equal(reasoningModel, MODEL_PROFILES["gpt-5.6"].reasoningModel);
  assert.equal(result.routerModel, routerModel);
  assert.equal(result.answer.model, reasoningModel);
});

test("dispatch rejects an arbitrary model profile", async () => {
  await assert.rejects(dispatch({ modelProfile: "not-a-supported-profile" }), /Unsupported modelProfile/);
});
