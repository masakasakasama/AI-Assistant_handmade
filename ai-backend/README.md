# Tatsu Home AI backend

AndroidにOpenAIの長期APIキーを入れないための小さなバックエンド

## ルーティング

1. Wake Wordは端末側でローカル検出し、常時OpenAIへ音声送信しない
2. Wake後だけSTTでテキスト化し、`gpt-5.6-luna` が低コストで intent / parameter を判定
3. SwitchBot / Alarm / Weatherの直接操作ならAndroidへ構造化結果だけ返す
4. simple_chatはLuna自身が短く回答
5. deep_reasoningだけ `gpt-5.6-sol` に昇格
6. GPT-Live-1は常時フロントに置かず、自然な全二重会話が必要なConversation Modeだけ任意で起動

## 起動

```bash
cd ai-backend
cp .env.example .env
export OPENAI_API_KEY="..."
npm start
```

## API

`GET /health`

`POST /api/dispatch`

```json
{
  "text": "エアコンを26度にして",
  "context": ""
}
```

想定route

```json
{
  "route": {
    "language": "ja",
    "route": "device_action",
    "confidence": 0.99,
    "action": "set_ac",
    "target": "エアコン",
    "temperatureC": 26,
    "timeLocal": null,
    "shortReason": "direct home-device command"
  },
  "answer": null
}
```

simple_chatではLuna、deep_reasoningではSolの回答が`answer`に入る

## Security

`OPENAI_API_KEY`はAndroidアプリ、GitHub、APKに入れない
サーバー環境変数だけに置く
