import { ROUTER_MODEL } from "./router.mjs";
import { REASONING_MODEL } from "./reasoner.mjs";

export const DEFAULT_MODEL_PROFILE = "current";

export const MODEL_PROFILES = Object.freeze({
  current: Object.freeze({ routerModel: ROUTER_MODEL, reasoningModel: REASONING_MODEL }),
  "gpt-5.6": Object.freeze({ routerModel: "gpt-5.6-luna", reasoningModel: "gpt-5.6-sol" }),
  "gpt-6": Object.freeze({ routerModel: "gpt-6-luna", reasoningModel: "gpt-6-sol" }),
  "gpt-6.1": Object.freeze({ routerModel: "gpt-6-luna", reasoningModel: "gpt-6.1-sol" })
});

export function resolveModelProfile(profile = DEFAULT_MODEL_PROFILE) {
  return Object.hasOwn(MODEL_PROFILES, profile) ? MODEL_PROFILES[profile] : null;
}
