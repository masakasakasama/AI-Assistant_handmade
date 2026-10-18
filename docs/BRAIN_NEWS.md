# Brainニュースを既存のTatsu Homeで読む

## 構成

Windows PCのBrain（ニュース収集 → GPT-6 Luna → GPT-6.1 Sol） → 既存のTatsu Home Vercel Backend（認証・Neon保存） → Web版の「ニュース」タブ・Android版の「ニュース」タブ

Telegram、LINE、Gmail、別のBot、別のバックエンドは不要

## 必要な既存環境

- Vercelで既存 `AI_BACKEND_TOKEN` と `AI_LIMIT_DATABASE_URL`（Neon）が正常に設定・デプロイされていること
- Home backendの `/api/news` がGET/POSTで利用できること（既存owner tokenで認証）
- データベーステーブルは初回利用時に自動生成、30日経過したニュースは次の投稿時に削除
- Androidアプリをこの実装を含むAPKに更新し、通知権限を許可すること

## PC側での追加設定

Windowsの「ユーザー環境変数」に `TATSU_HOME_AUTH_TOKEN` を追加し、値に既存の `AI_BACKEND_TOKEN` と同じ文字列を入力する。ChatGPT/OpenAIのAPIキーとは別で、Brain側Gitや設定ファイルへは書き込まない

`TATSU_HOME_AUTH_TOKEN` 未設定の場合、BrainはニュースをローカルSQLiteへ保持し、送信しない。設定後は1時間ごとの次回実行で未送信分を再試行する

Home Backendのアドレスは `https://ai-assistant-handmade.vercel.app` を既定値として使用。変更が必要な場合のみBrainの `config.json` の `home_backend_url` を変更する

## 操作

- Home Web（https://ai-assistant-handmade.vercel.app/）の「ニュース」タブで一覧・元記事を閲覧
- Home Android版も「ニュース」タブで一覧を閲覧
- Android版はWorkManagerで1時間ごとを目安にバックグラウンド確認し、新しい重要ニュースがあれば端末の標準通知へ表示。実行時刻はAndroidの省電力制御に左右される
- 初回のAndroid確認では過去ニュースを大量通知しない
- Home画面での閲覧・通知はOpenAI APIを追加呼び出ししない（Neon通信・データ転送は発生）
- 通常のChatGPTチャットには外部のAPIから勝手にメッセージを流し込むことはできない

## 制限

- ユーザーのPCでの実行、本番Neon保存、Vercelの本番デプロイ、Galaxy実機での通知は別途検証が必要
- 速報見出しは未確認情報の可能性がある。アプリの通知は元記事の確認リンクを伴う
- Androidの強制停止・電源断・未起動時はOSが定期ジョブを実行しない場合がある
- Home Backend認証・Neon URLの設定ミスは失敗として扱い、外部通知サービスへ自動フォールバックしない
