import { createResponse, outputText } from "./openai.mjs";

export const REASONING_MODEL = process.env.REASONING_MODEL || "gpt-5.6-sol";

export async function reason({ text, context = "", language = "ja" }) {
  const instructions = `
You are the high-capability reasoning backend for Tatsu Home.
Answer only when the low-cost router has escalated the request.
Be accurate, concise, and practical.
Do not claim a physical action happened unless the Android app confirms the tool result.
Reply in the user's language. Requested language code: ${language}.
`.trim();

  const response = await createResponse({
    model: REASONING_MODEL,
    reasoning: { effort: "high" },
    instructions,
    input: [
      {
        role: "user",
        content: [
          {
            type: "input_text",
            text: context ? `Context:\n${context}\n\nUser:\n${text}` : text
          }
        ]
      }
    ],
    max_output_tokens: 1800
  });

  return {
    model: REASONING_MODEL,
    text: outputText(response)
  };
}
