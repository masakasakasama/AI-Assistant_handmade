# Tatsu Home AI backend

AndroidにOpenAIの長期APIキーを入れないための小さなバックエンド

## ルーティング

1. `gpt-5.6-luna` が低コストで intent / parameter を判定
2. SwitchBot / Alarm / Weatherの直接操作ならAndroidへ構造化結果だけ返す
3. 深い思考が必要な依頼だけ `gpt-5.6-sol` に昇格
4. GPT-Live-1導入後は音声フロントエンドからこのバックエンドへ委任する

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

複雑な質問だけ`answer`にSolの回答が入る

## Security

`OPENAI_API_KEY`はAndroidアプリ、GitHub、APKに入れない
サーバー環境変数だけに置く
