export const ANSWER_MODES = Object.freeze({
  quick: Object.freeze({ effort: "low", concise: true }),
  balanced: Object.freeze({ effort: "medium", concise: true }),
  deep: Object.freeze({ effort: "high", concise: false })
});

// The measured standard is medium effort with a concise spoken response.
export const DEFAULT_ANSWER_MODE = process.env.ANSWER_MODE || "balanced";

export function resolveAnswerMode(mode = DEFAULT_ANSWER_MODE) {
  if (!Object.hasOwn(ANSWER_MODES, mode)) {
    const error = new Error("Unsupported answerMode");
    error.code = "invalid_answer_mode";
    throw error;
  }
  return { name: mode, ...ANSWER_MODES[mode] };
}
