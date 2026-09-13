# Tatsu Home アーキテクチャ

更新: 2026-09-13。実装済み・予定・実測を混同しない。

## 通常経路

```text
Galaxyマイク（Phase 1ではXVF3800 → ESP32 → Wi-Fi）
→ 音声セッション管理 / STT
→ 少数のローカル処理（停止など）
→ 認証付きTatsu Backend
→ Luna 1回：操作案 / 確認 / 簡単な回答 / 深い質問の判定
→ 深い質問だけSol（本番既定high。mediumは速度・費用比較用）
→ Androidの操作検証・実行層
→ 実行結果または回答 → 定型文 / TTS → 再生
```

STT、TTS、認証付き実行連携はまだ未完成。現在のdispatchは提案を返すだけ。
simple_chatとclarifyはLunaのreplyTextを使い、回答用の追加LLM呼び出しをしない。
深い会話の継続・明示的な「詳しく考えて」は将来Solへ直接送る。初版では未実装。

## Conversation Mode

GPT-Liveを任意起動。通常のSTT/LLM/TTSと並行して二重にマイクを消費しない。
Tatsu Backendでセッションを作成し、対応するWebRTC経路を検証する。
Liveの委譲は同じ操作実行層へ。家電状態・アラーム・確認依頼・操作履歴を別管理にしない。
明示終了、無操作終了、セッション時間上限を設け、音声とバックエンド課金を別集計。
Liveを使わなくても通常の読み上げ停止は実装する。

## 操作の権限境界

- LLMは操作案のみ。confidenceは未校正であり実行の根拠にしない。
- AndroidがdeviceId/alarmId、対応機能、範囲、現在状態、要求の有効期限を確認。
- 候補ゼロ／複数なら確認。確認への「はい」は有効な確認IDと対象revisionに紐付ける。
- 発話の途中結果で操作しない。「26度、いや24度」の確定を待つ。
- 最初は一発話一変更。複合指示を黙って一部実行しない。
- operationIdを永続化。再送で重複実行しない。外部APIの厳密なexactly-onceは保証しない。
- HTTPタイムアウトは失敗確定ではない。SwitchBot受付／状態確認済み／成否不明を分ける。
- 停止後や会話切替後に届く古い結果はgenerationIdで破棄。

## Androidとローカルアラーム

RoomとAlarmManagerは単一トランザクションではない。予約状態との照合・修復を設ける。
alarmId維持、繰り返し全体と一回の例外、複数候補、Direct Boot、Exact権限失効を検証。
現在の実装は時分とrepeatMask中心であり、日付付き単発指定や例外管理の完成を主張しない。
現在のExact権限なしの不正確な予約へのフォールバックは、UIで精度低下を知らせるまで本番合格にしない。
Asia/Tokyo / Europe/Berlin、DSTの欠落／重複時刻、現地追従／固定タイムゾーンを仕様化する。
アラームはAndroid内蔵音で鳴動可能にし、Wi-Fiスピーカーのみへ依存しない。
手持ちGalaxyでの成功は中古タブレットの保証ではない。最終端末でも72時間と再起動試験を行う。

## Phase 1音響

XVF3800とXIAO ESP32-S3はI2S、AndroidとはWi-Fi。USB直結は今回の基本案にしない。
WakeNetは16kHz入力に合わせる。Hey Tatsuは学習モデル作成を要する別課題。既存語で先に検証。
Wakeは待機から会話への入口。会話中はWakeなしの発話検出、終了後はローカル待機へ戻る。
再生経路：Android → Wi-Fi → ESP32 → XVF3800の再生入力／AEC参照経路 → 有線スピーカー。
ファームウェア・チャンネル・クロック・参照経路・同時発話時の性能を実測する。
前方バッファ、番号付き音声フレーム、上限付きjitterバッファ、再生位置報告、即時flush、認証を実装。
AECはテレビ音声除去の保証ではなく、Full Duplexや割り込み制御の代替でもない。

## Backend / 障害 / セキュリティ

Vercelは短いdispatch・セッション作成の候補。長時間音声の必須中継点にしない。
Function最大時間・cold start・再接続・保存状態を考慮。プロセスメモリだけへ状態を置かない。
OpenAIキーはBackendのみ。端末認証・失効、日月予算、モデルと出力量制限、Live接続数制限が公開前の必須条件。
現HTTPエンドポイントの認証・レート制限は未実装。APIキーを設定した無防備な公開デプロイは禁止。
SwitchBot秘密情報はKeystore鍵で暗号化。トークンと未編集の音声・プロンプトを公開ログへ残さない。
API停止→手動操作、ネット断→ローカルアラーム、音声端末断→Android発音。
ネット復帰時に古い物理操作を自動再実行しない。天気キャッシュは取得時刻と古さを示す。

## 公式仕様

- https://developers.openai.com/api/docs/models/gpt-5.6-luna
- https://developers.openai.com/api/docs/models/gpt-5.6-sol
- https://developers.openai.com/api/docs/guides/live
- https://developer.android.com/reference/android/speech/RecognizerIntent
- https://developer.android.com/privacy-and-security/direct-boot
- https://vercel.com/docs/functions/websockets
- https://wiki.seeedstudio.com/respeaker_xvf3800_introduction/
