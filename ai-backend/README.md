# Tatsu Home AI Backend

AndroidへOpenAI長期キーを埋め込まず、文字入力から回答または操作案を返す。

## 現在の処理

- GPT-6 Luna 1回で分類・抽出・簡単な回答／確認文を生成。
- 深い質問だけGPT-6 Sol。既定highを維持し、REASONING_EFFORTでmedium/highを比較。
- `ROUTER_MODEL` / `REASONING_MODEL` で既定を上書きできる。PoCの`/api/health`が現在有効なモデルIDを返す。
- 物理操作は実行しない。Androidの検証・実行層は別途必要。
- 応答にtimings.routerMs、timings.answerMs、latencyMsとcalls[].usageを含める。
- simple.mjsは旧2段構成のベンチマーク用。通常dispatchからは呼ばない。
- STT/TTS/Liveの音声経路は未実装。

既定モデルはGPT-6 Luna / GPT-6 Sol。デプロイ環境の`ROUTER_MODEL` / `REASONING_MODEL`が設定されていればそちらが優先される。実際に有効なモデルIDはGET /api/healthで確認する。

AIラボの「4経路比較」は、Luna-first/Jev-firstそれぞれに`modelProfile: "gpt-5.6"`と`modelProfile: "gpt-6"`を付け、同じ入力で並列実行する。Jev自体は同じ分類モデルを使い、分類後のsimple_chatは該当版Luna、deep_reasoningは該当版Sol highへ送る。選べる値は固定モデルペアのみで、実機アクションは実行しない。比較1回でAPIを4回呼び出す。

## 起動・比較

Node 20以上。OPENAI_API_KEYを環境変数へ設定し `npm start`。
`.env`を置くだけでは自動読込しない。対応Nodeでは `node --env-file=.env src/server.mjs` も利用可能。
GET /api/health（ローカルは/healthも互換対応）、POST /api/dispatch。

```json
{"text":"エアコンを26度にして","context":""}
```

`route.replyText`はsimple_chat/clarifyで回答文、それ以外はnull。`answer`は会話回答のみ。
confidenceは診断値であり実行許可に使わない。

`npm test`はAPIを呼ばないテスト。`npm run benchmark:plan`も無課金。
`npm run benchmark`はAPIを実際に呼ぶため課金される。結果はGit管理外のbenchmark-resultsへ保存。

## 公開前の制約

現在、端末認証・予算制限・レート制限は未実装。キーを設定した無防備な公開デプロイはしない。
health成功はOpenAI疎通や音声品質合格を意味しない。
詳しくは[セットアップ](../AI_BACKEND_SETUP.md)、[性能計画](../docs/PERFORMANCE.md)。
