# AI Backend設定

## 環境変数

| 名前 | 用途 |
|---|---|
| OPENAI_API_KEY | Backendのみに保存。APK・Git・チャットへ貼り付けない |
| ROUTER_MODEL | 既定gpt-5.6-luna |
| REASONING_MODEL | 既定gpt-5.6-sol |
| REASONING_EFFORT | 既定high。品質を落とさず、mediumはbenchmarkで比較する |

## 実装状況

Luna 1回→簡単な回答、深い質問だけSol。STT/TTSとGPT-Liveはまだ接続されていない。
通常モードも将来は音声の途中停止を実装。Live限定機能にしない。

## Vercel

HTTP Functions用の設定あり。GET /api/health、POST /api/dispatch。
Androidの設定にプロジェクトのベースURLだけを入力する。末尾へ/apiを付けない。
以前のAndroidの/health呼び出しは/api/healthへ修正済み。
AI設定の保存にSwitchBotのToken/Secretは不要。

**公開前の必須作業：端末認証・失効・上限・入力制限・秘匿ログ。現実装では未完了。**
この変更は自動で本番デプロイしない。公開環境へキーを追加する前に上記を完成させる。

## 試験順序

1. npm test / npm run benchmark:plan（API不要）。
2. 安全なローカル環境へAPIキーを設定して実dispatch。healthだけで合格にしない。
3. 同じケースで単一Luna／旧2回経路／Sol medium／highを比較。
4. 認証済みHTTP経路でAndroid往復時間、cold/warmの差を測る。
5. Phase 0Bで音声応答開始、停止、Liveの費用を追加比較。

[Phase 0計画](docs/PHASE_0.md) / [性能比較](docs/PERFORMANCE.md) / [アーキテクチャ](docs/ARCHITECTURE.md)
