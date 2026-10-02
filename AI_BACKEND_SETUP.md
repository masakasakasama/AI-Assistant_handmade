# AI Backend設定

## 環境変数

| 名前 | 用途 |
|---|---|
| OPENAI_API_KEY | Backendのみに保存。APK・Git・チャットへ貼り付けない |
| ROUTER_MODEL | 既定gpt-6-luna |
| REASONING_MODEL | 既定gpt-6.1-sol |
| ANSWER_MODE | API既定balanced。quick=low、balanced=medium、deep=high。端末のanswerMode指定が優先 |
| REASONING_EFFORT | reasoner直接呼び出し・既存CLI向け。既定high。APIはANSWER_MODEで選択 |
| JEV_OPENROUTER_API_KEY | Jev用。Backendのみに保存し、APKへ入れない |
| JEV_MODEL | 既定typesafe/jev-1.13。比較再現性のため固定版を使用 |
| JEV_ENDPOINT | 既定https://openrouter.ai/api/alpha/decisions |

## モデルの世代

v0.4.19の現行設定は `gpt-6-luna` ＋ `gpt-6.1-sol`。GPT-6.1 Lunaは公式モデル一覧にないため、Lunaは6を継続する。AIラボの現行モデル比較は `modelProfile=gpt-6.1` でこの固定ペアを使う。旧 `gpt-6` プロファイル（6 Luna＋6 Sol）は互換性のため残す。

既存デプロイで `REASONING_MODEL=gpt-6-sol` を設定している場合は `gpt-6.1-sol` へ変更して再デプロイする。APK更新だけではBackendの環境変数は変わらない。

## 実装状況

通常のPoC既定はGPT-6 Luna（ルーティング・簡単な会話）とGPT-6.1 Sol（複雑な質問、標準medium／詳細high）。`ROUTER_MODEL` / `REASONING_MODEL`環境変数が設定されている場合はそちらが優先される。GET /api/healthで実際に有効なモデルIDを確認する。
Luna-firstはGPT-6 Luna 1回で分類とsimple_chat回答を兼ね、深い質問だけGPT-6.1 Sol。Jev-firstはJev 1回でrouteと閉じた操作パラメータを判断し、simple_chatだけGPT-6 Luna、deep_reasoningだけGPT-6.1 Solの選択モードへ送る。家電・アラーム・天気はAndroid側で処理する。
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
3. 同じケースでGPT-6 Luna／旧2回経路／GPT-6.1 Sol medium／highを比較。
4. 認証済みHTTP経路でAndroid往復時間、cold/warmの差を測る。
5. Phase 0Bで音声応答開始、停止、Liveの費用を追加比較。

[Phase 0計画](docs/PHASE_0.md) / [性能比較](docs/PERFORMANCE.md) / [アーキテクチャ](docs/ARCHITECTURE.md)

## v0.4.22

Androidの「回答の考え方」は標準balancedが既定。速く答えるquick、じっくり考えるdeepも選べる。標準は短い読み上げ向け返答で、詳しい回答はdeepで取得できる。モデルIDだけでなく `/api/health` の `answerMode` と結果の `answer.effort` も確認する。測定条件は[性能資料](docs/PERFORMANCE.md)参照。

## Backend access authentication (v0.4.31)

Set `AI_BACKEND_TOKEN` in the local service environment or Vercel server secrets,
then restart/redeploy the Backend. Generate a random access credential separately
from OpenAI/OpenRouter provider keys. Never put provider keys or this credential
in APK build variables. Missing server configuration returns 503; missing, wrong
or revoked Bearer credentials return 401 before request parsing/provider calls.
Health remains readable without a credential.

On Android, enter the same access token in Settings → Backend認証トークン.
It uses the existing Keystore-backed AES/GCM storage. Blank input preserves the
saved value; the delete action removes it. After device restore, a credential
whose Keystore key is unavailable must be entered again. Authenticated POSTs
require HTTPS, except local loopback/Android emulator host addresses; redirects
are not followed with credentials. 401/403 asks the owner to update the token.

To revoke access, replace the server token and restart/redeploy. Old tokens then
fail; update each authorized device. This is one shared owner token, not a user
account/session service or per-device revocation. `AI_BACKEND_TOKEN` is also used
by the HTTP smoke script. Local/Vercel rejection and rotation tests make no provider
calls. Actual deployed configuration, Keystore persistence and Galaxy acceptance
remain unverified. Rate limits and usage caps are separate unfinished work.
