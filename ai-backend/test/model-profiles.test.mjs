import test from "node:test";
import assert from "node:assert/strict";
import { dispatchJev } from "../src/dispatch-jev.mjs";
import { dispatch } from "../src/dispatch.mjs";
import { MODEL_PROFILES, resolveModelProfile } from "../src/model-profiles.mjs";

test("model comparison profiles map only to the fixed Luna/Sol pairs", () => {
  assert.deepEqual(resolveModelProfile("gpt-5.6"), {
    routerModel: "gpt-5.6-luna", reasoningModel: "gpt-5.6-sol"
  });
  assert.deepEqual(resolveModelProfile("gpt-6"), {
    routerModel: "gpt-6-luna", reasoningModel: "gpt-6-sol"
  });
  assert.deepEqual(resolveModelProfile("gpt-6.1"), {
    routerModel: "gpt-6-luna", reasoningModel: "gpt-6.1-sol"
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

for (const profile of ["current", "gpt-6.1"]) {
  test(`${profile} keeps Luna 6 for simple chat and uses Sol 6.1 for deep reasoning`, async () => {
    const simple = await dispatch({ text: "hello", modelProfile: profile }, {
      routeIntent: async input => {
        assert.equal(input.model, "gpt-6-luna");
        return { route: "simple_chat", language: "en", replyText: "Hello" };
      },
      reason: async () => { throw new Error("Simple chat must not call Sol"); }
    });
    assert.equal(simple.answer.model, "gpt-6-luna");
    const deep = await dispatch({ text: "compare", modelProfile: profile }, {
      routeIntent: async input => {
        assert.equal(input.model, "gpt-6-luna");
        return { route: "deep_reasoning", language: "en" };
      },
      reason: async input => {
        assert.equal(input.model, "gpt-6.1-sol");
        return { model: input.model, text: "done" };
      }
    });
    assert.equal(deep.answer.model, "gpt-6.1-sol");
    for (const route of ["simple_chat", "deep_reasoning"]) {
      const answer = async input => {
        assert.equal(input.model, route === "simple_chat" ? "gpt-6-luna" : "gpt-6.1-sol");
        return { model: input.model, text: "done" };
      };
      const jev = await dispatchJev({ text: "compare", modelProfile: profile }, {
        routeIntentJev: async () => ({ model: "jev-test", route, language: "en" }),
        answerSimple: answer,
        reason: answer
      });
      assert.equal(jev.answer.model, route === "simple_chat" ? "gpt-6-luna" : "gpt-6.1-sol");
    }
  });
}
