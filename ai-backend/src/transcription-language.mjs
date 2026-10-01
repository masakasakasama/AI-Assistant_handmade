import { francAll } from "franc-min";

/** Check an inconsistent audio label against the transcript; never rewrite the transcript. */
export function validateTranscriptionLanguage(text, audioLanguages) {
  const original = [...new Set(audioLanguages)];
  const unchanged = { languages: original, audioLanguages: original, languageSource: "audio" };
  // Keep mixed-language results and a supported Latin-language prediction from the audio model.
  if (original.length > 1 || original.some(code => code === "de" || code === "en")) return unchanged;
  // Japanese text (including product names in a Japanese utterance) is not relabeled by a Latin classifier.
  if (/[\p{Script=Hiragana}\p{Script=Katakana}\p{Script=Han}]/u.test(text)) return unchanged;
  const tokens = text.match(/\p{L}+/gu) || [];
  if (tokens.length < 3 || tokens.join("").length < 12) return unchanged;
  const ranked = francAll(text, { only: ["deu", "eng"], minLength: 12 });
  const [first, second] = ranked;
  const language = { deu: "de", eng: "en" }[first?.[0]];
  // franc scores are distance scores, not probabilities. Require a clear separation.
  if (!language || !second || first[1] - second[1] < 0.1) return unchanged;
  return { languages: [language], audioLanguages: original, languageSource: "transcript-check" };
}
