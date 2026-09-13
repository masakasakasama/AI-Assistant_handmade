import test from "node:test";
import assert from "node:assert/strict";

test("backend environment defaults document the intended models", () => {
  assert.equal(process.env.ROUTER_MODEL || "gpt-5.6-luna", "gpt-5.6-luna");
  assert.equal(process.env.REASONING_MODEL || "gpt-5.6-sol", "gpt-5.6-sol");
});
