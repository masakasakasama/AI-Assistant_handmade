# AI Backend設定

## 環境変数

| 名前 | 用途 |
|---|---|
| OPENAI_API_KEY | Backendのみに保存。APK・Git・チャットへ貼り付けない |
| ROUTER_MODEL | 既定gpt-6-luna |
| REASONING_MODEL | 既定gpt-6-sol |
| REASONING_EFFORT | 既定high。品質を落とさず、mediumはbenchmarkで比較する |
| JEV_OPENROUTER_API_KEY | Jev用。Backendのみに保存し、APKへ入れない |
| JEV_MODEL | 既定typesafe/jev-1.13。比較再現性のため固定版を使用 |
| JEV_ENDPOINT | 既定https://openrouter.ai/api/alpha/decisions |

## 実装状況

通常のPoC既定はGPT-6 Luna（ルーティング・簡単な会話）とGPT-6 Sol high（複雑な質問）。`ROUTER_MODEL` / `REASONING_MODEL`環境変数が設定されている場合はそちらが優先される。GET /api/healthで実際に有効なモデルIDを確認する。
Luna-firstはGPT-6 Luna 1回で分類とsimple_chat回答を兼ね、深い質問だけGPT-6 Sol。Jev-firstはJev 1回でrouteと閉じた操作パラメータを判断し、simple_chatだけGPT-6 Luna、deep_reasoningだけGPT-6 Sol highへ送る。家電・アラーム・天気はAndroid側で処理する。
通常モードも将来は音声の途中停止を実装。Live限定機能にしない。

## Vercel

HTTP Functions用の設定あり。GET /api/health、POST /api/dispatch、POST /api/dispatch-jev、POST /api/router-compare。router-compareはLunaとJevを並列に呼び、物理操作を実行しない。
Androidの設定にプロジェクトのベースURLだけを入力する。末尾へ/apiを付けない。
環境変数を追加・変更した後は新しいDeploymentを作成する。既存Deploymentには新しい値は反映されない。
以前のAndroidの/health呼び出しは/api/healthへ修正済み。
AI設定の保存にSwitchBotのToken/Secretは不要。

**公開前の必須作業：端末認証・失効・上限・入力制限・秘匿ログ。現実装では未完了。**
この変更は自動で本番デプロイしない。公開環境へキーを追加する前に上記を完成させる。

## 試験順序

1. npm test / npm run benchmark:plan（API不要）。
2. 安全なローカル環境へAPIキーを設定して実dispatch。healthだけで合格にしない。
3. 同じケースでGPT-6 Luna／旧2回経路／GPT-6 Sol medium／highを比較。
4. 認証済みHTTP経路でAndroid往復時間、cold/warmの差を測る。
5. Phase 0Bで音声応答開始、停止、Liveの費用を追加比較。

[Phase 0計画](docs/PHASE_0.md) / [性能比較](docs/PERFORMANCE.md) / [アーキテクチャ](docs/ARCHITECTURE.md)
