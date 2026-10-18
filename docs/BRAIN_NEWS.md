# Brain公開ニュースの転送用API（個人用Home専用）

このリポジトリのTatsu Home（家族共有）画面・Android通知からNews機能を撤去。ニュースは個人用 [Home](https://github.com/masakasakasama/Home) アプリだけで閲覧する。

既存のVercel/Neonを再利用し、認証付き `/api/news` (GET/POST) を公開ニュースの転送路として残す。Brainからの保存に必要。Tatsu Homeの通常UIには表示せず、ニュース以外の情報（Gmail、差出人、本文、個人通知）は送信も保存もしない。共有トークンを持つクライアントから公開ニュース転送内容へはアクセス可能なため、機微情報を絶対に保存しない。

Homeはニュースを認証済みで読む。HomeのGmail新着通知件数は端末上のNotificationListener（明示許可）でのみ集計し、このVercel Backendへ送信しない。

## 稼働前確認
- Vercelの production がこの変更を含む main をデプロイ済み
- Brain PC側の `TATSU_HOME_AUTH_TOKEN` はBackendの既存 `AI_BACKEND_TOKEN` と同じ
- HomeアプリでBackend認証トークンをKeystore暗号化保存している
- Brainは初回RSS取得を静かなベースラインとして処理。新着がないと分析バッチは届かない
- Homeの個人用ニュース画面・Gmail通知はGalaxyへのAPK更新と明示的な通知アクセス許可が必要

長期的に完全分離する場合のみ、既存APIを専用個人用Backendへ移行する。本変更では新しいサーバーを契約しない
