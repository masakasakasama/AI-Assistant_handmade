# 音声入力修正計画 v0.1

対象: Galaxy の「話す」ボタンで `音声認識エラー (12)` が発生し、音声入力が止まる問題

## 実装範囲

- APKの識別
  - app version / versionCode / Git commit を音声診断情報へ出力
- 診断
  - sessionId / attemptId / 言語 / 認識方式 / エラー / フォールバック / 主要時間を記録
  - 生音声、APIキー、SwitchBot認証情報は記録しない
- 言語
  - `ja-JP` / `en-US` / `de-DE` を明示指定
  - 端末言語を初期値にし、AI画面から変更可能
  - フォールバック時も同じ言語を維持
- 認識方式
  - Android 13+ では `checkRecognitionSupport()` を利用
  - 対応確認は1.5秒で打ち切り、確認不能だけを理由に認識を禁止しない
  - システム認識サービスの存在を確認
  - Manifest に RecognitionService の `queries` を追加
- エラー処理
  - オンデバイスのエラー12/13はシステム認識へ最大1回だけ切替
  - Recognizer生成/開始失敗も、オンデバイスからシステムへ最大1回だけ切替
  - システム側失敗は再フォールバックしない
  - キャンセル時は現在試行を先に無効化
  - 準備8秒、認識30秒のタイムアウトを追加
- セッション安全性
  - sessionId / attemptId を各コールバックへ付与
  - 古い試行、古いセッション、重複final resultを破棄
  - stale result がAIや家電・アラーム操作へ到達しないようにする
- UI
  - 待機 / 準備中 / 受付中 / 切替中 / 処理中 / 発話中 / 失敗を区別
  - 言語選択と認識方式を表示
  - 診断情報を画面からコピー可能
  - AI Backend の `未確認` と `接続失敗` を分離
- 性能計測
  - button→ready
  - fallback→ready
  - speech end→final
  - AI dispatch duration
  - TTS request/start/done
- 自動試験
  - エラー12/13の1回フォールバック
  - システム認識からの再フォールバック禁止
  - stale attempt / stale session破棄
  - final resultの重複受理防止
  - cancel後の遅延callback破棄

## 配布版

- versionName: 0.4.2
- versionCode: 8
- 計画識別名: 音声入力修正計画 v0.1

## 実機確認

CIで検証できるのはコード・ユニットテスト・Lint・ビルドまで

Galaxy実機では次を確認する

1. 日本語 `ja-JP`
2. 英語 `en-US`
3. ドイツ語 `de-DE`
4. オンデバイス成功経路
5. システム認識経路
6. エラー12/13が発生した場合の1回フォールバック
7. 無言 / キャンセル / 連打
8. TTS再生中の割り込み
9. 音声入力 → AI → TTS
10. 家電・アラーム操作は認識安定後に1件ずつ確認

実機で再現できなかったエラー分岐は「実機確認済み」とは扱わず、自動試験結果と分けて記録する
