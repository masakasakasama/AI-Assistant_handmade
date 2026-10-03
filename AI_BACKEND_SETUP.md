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

**公開前の必須条件：端末認証・失効・上限・入力制限・秘匿ログの実環境受入。コードと隔離fixtureは実装済みだが、本番/実端末受入は未完了。**
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


## 共通の利用件数・レート制限

全4 POST経路で、owner認証と入力検査後、provider呼出し前に共有Redis RESTへ
atomic EVALを送る。Redis REST互換endpointと以下をDeployment環境へ設定する。

- `AI_LIMIT_REDIS_URL`: HTTPSのRedis REST endpoint
- `AI_LIMIT_REDIS_TOKEN`: Redis REST用secret（Androidへ入れない）
- `AI_DAILY_REQUEST_LIMIT`: UTC日単位の正整数の受付上限
- `AI_RATE_REQUEST_LIMIT`: UTC固定1分窓の正整数の受付上限

値はOwnerの許容使用量に合わせて決める。process memoryやVercelの一時fileへの
fallbackは設けない。設定欠落/不正は503、共有storeの障害・不明応答も503で呼出しを
止める。日/分の上限到達は429と`retryAfterSeconds`を返す。拒否時は件数を増やさない。
受付したリクエストはprovider失敗でも減算しない。auth tokenのrotation・process再起動・
別Functionでカウンタを初期化しない。日キーは2日、分キーは120秒でTTL削除する。
health GETは制限対象外。設定変更後はredeployし、実Redisの共有・期限境界・障害を検証する。

これはrequest件数の上限で、tokens/円の請求上限ではない。dispatchや比較は複数の
provider呼出しを含む。固定1分窓の境界では隣接する2窓分のburstが可能。
Provider側の課金制限も設定し、実際の費用は利用明細で確認する。

例外・cause・provider本文・入力・headerをログ出力しない。運用ログは固定eventだけ。
予期しないエラーと比較経路のprovider失敗は固定の公開文言を返す。既存のtimeoutでは
model/stage/待機時間を維持する。成功時のtranscript/回答は機能上レスポンスへ返すが、
ログへは保存しない。fixtureは模擬共有storeであり、本番Redis EVALの合格を意味しない。

## 無料Neonでの利用回数制限

Redis RESTの代わりにNeonの無料プランを使える。専用DBで `ai-backend/sql/request-limits.sql` を実行し、接続文字列を本番の `AI_LIMIT_DATABASE_URL` に保存する。`AI_DAILY_REQUEST_LIMIT` / `AI_RATE_REQUEST_LIMIT` は共通。Redis設定がある場合はRedisを優先する。PostgreSQL関数内のトランザクションロックで、日次と分次をまとめて判定・加算する。拒否した要求ではカウンターを増やさない。

本番用Neonで12件の同時要求に対し分次上限2件だけ受け付けること、日次上限、拒否時の非加算を実測した。実端末やprovider課金上限の検証とは区別する。認証キーやDB接続文字列は公開素材へ入れない。
