# AI Backend setup

Tatsu HomeはOpenAI APIキーをAPKへ埋め込まない

## 構成

GPT-Live-1
→ GPT-5.6 Lunaでルーティング
→ device / alarm / weather は構造化結果をAndroidへ返す
→ deep_reasoningだけGPT-5.6 Solへ昇格

## Vercel

このリポジトリはそのままVercelにImport可能

Environment Variables:

- OPENAI_API_KEY
- ROUTER_MODEL=gpt-5.6-luna
- REASONING_MODEL=gpt-5.6-sol

Deploy後

- GET https://YOUR_PROJECT.vercel.app/api/health
- POST https://YOUR_PROJECT.vercel.app/api/dispatch

Androidの Tatsu Home → 設定 → AI Backend URL には

https://YOUR_PROJECT.vercel.app

を登録する

## 確認用

POST /api/dispatch

{
  "text": "エアコンを26度にして"
}

Lunaがdevice_actionを返すことを確認する

{
  "text": "自作AIスピーカーの構成をQCDで比較して"
}

Lunaがdeep_reasoningを返し、Solのanswerが返ることを確認する
