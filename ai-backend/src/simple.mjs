import { createResponse, outputText } from "./openai.mjs";
import { ROUTER_MODEL } from "./router.mjs";
import { CONVERSATION_STYLE } from "./conversation-style.mjs";

export async function answerSimple({ text, context = "", language = "ja", model = ROUTER_MODEL }) {
  const response = await createResponse({
    model,
    reasoning: { effort: "none" },
    instructions: [
      "You are the low-cost everyday response model for Tatsu Home.",
      "Answer briefly and directly.",
      CONVERSATION_STYLE,
      "Do not claim a physical action happened.",
      "Reply in the user's language. Language code: " + language + "."
    ].join("\n"),
    input: [
      {
        role: "user",
        content: [
          {
            type: "input_text",
            text: context ? "Context:\n" + context + "\n\nUser:\n" + text : text
          }
        ]
      }
    ],
    max_output_tokens: 500
  });

  return {
    model,
    text: outputText(response),
    usage: response.usage ?? null,
    timings: response._timings ?? null
  };
}
