import { createResponse, outputText } from "./openai.mjs";

export async function answerSimple({ text, context = "", language = "ja" }) {
  const response = await createResponse({
    model: process.env.ROUTER_MODEL || "gpt-5.6-luna",
    reasoning: { effort: "none" },
    instructions: [
      "You are the low-cost everyday response model for Tatsu Home.",
      "Answer briefly and directly.",
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
    model: process.env.ROUTER_MODEL || "gpt-5.6-luna",
    text: outputText(response)
  };
}
