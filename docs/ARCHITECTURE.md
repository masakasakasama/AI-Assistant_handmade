# Tatsu Home アーキテクチャ

更新: 2026-09-13。実装済み・予定・実測を混同しない。

## 比較する通常経路

```text
A: Luna-first
音声 → STT → Luna
  ├ 家電 / アラーム / 天気 → Android
  ├ simple_chat → Lunaの同一応答
  ├ deep_reasoning → Sol high
  └ clarify → Lunaの同一応答

B: Jev-first
音声 → STT → Jev
  ├ 家電 / アラーム / 天気 → Android
  ├ simple_chat → Luna
  ├ deep_reasoning → Sol high
  └ clarify → ローカル定型確認
```

Jevは自由文を生成しない。1回のSystem One呼び出しでrouteに加え、家電action・既知対象・16〜30℃・アラームaction・既知対象・新旧の時分を閉じたChoiceとして並列判定する。
Androidは最終的な対象ID、範囲、重複、現在状態を再検証してから物理操作する。
simple_chatはLuna-firstが1回、Jev-firstがJev＋Lunaの2回になるため、Jev-firstが必ず速いわけではない。deep_reasoningと物理系は初段ルーター差がそのまま短縮候補になる。

## 回答比較

Luna-firstとJev-firstへ同じ確定済みSTT文脈を並列送信し、回答本文・route・モデル・端末往復時間・requestIdを個別に表示する。両結果は別々に保持し、一方の失敗や遅延で他方の回答を消さない。比較要求はキャンセル可能で、各経路は60秒で終了する。
比較モードではSwitchBot／AlarmManager実行層へ入らない。家電・アラームrouteは実行前の提案として表示するだけ。weatherもAndroidにある同一キャッシュを参照し、比較だけのために追加取得しない。速度差だけから回答品質の優劣を決めない。
比較完了後の通常Luna／Jev音声セッションは、新しい世代IDを発行して前の比較要求・TTSを破棄する。古い応答・TTS callbackが新しいセッションを更新しない。

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
