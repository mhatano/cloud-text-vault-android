# Cloud Text Vault for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/Jetpack%20Compose-Material%203-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](./LICENSE)

**Cloud Text Vault for Android** は、PC版（Java 25 Swing版: [`mhatano/cloud-text-vault`](https://github.com/mhatano/cloud-text-vault)）と **100% 互換の暗号化フォーマット・クラウド保存先** を備えた Android アプリケーション（Kotlin + Jetpack Compose / Material 3）です。

PC（Windows / macOS / Linux）で登録した暗号化テキストを Android スマートフォン・タブレットから復号・参照したり、Android から登録したテキストを PC 側で参照したりできます。

---

## 主な特徴

- **PC版（Java 25 Swing）との完全なデータ互換性**
  - 同一の **PBKDF2-HMAC-SHA256（310,000イテレーション）+ AES-256-GCM** 暗号化仕様および JSON エンベロープ構造（`cloud-text-vault.enc.json`）を使用します。
  - **Google Drive API (v3)** および **Dropbox API (v2)** を介して、PCとAndroid間でシームレスに暗号化データを共有できます。
- **同じ操作体系（クリア・登録・ドロップダウン参照）**
  - **タイトル入力あり**: `yyyy-MM-dd HH:mm:ss | タイトル` でドロップダウンに表示
  - **タイトル未入力（空欄）**: `yyyy-MM-dd HH:mm:ss | #<本文のSHA-256ハッシュ先頭12文字>` で自動表示
  - ドロップダウンから選択するとタイトル欄・テキストエリアに内容が即座に表示され、ワンタップでクリップボードへコピーできます。
- **Android ならではの便利機能**
  - **他アプリからの「共有 (Share)」連携 (`Intent.ACTION_SEND`)**: ブラウザやメモアプリ等でテキストを選択し「共有」→「Cloud Text Vault」を選ぶと、テキストエリアに自動入力され、そのまま暗号化してクラウドへ登録できます。
  - **認証情報の端末内暗号化保存**: OAuth トークン等の設定は Android Keystore ベースの `EncryptedSharedPreferences` に暗号化して保存されます。

---

## APK のダウンロード・ビルド方法

### 1. GitHub Actions から APK をダウンロード（推奨）
本リポジトリには GitHub Actions ワークフロー（[`.github/workflows/android-build.yml`](./.github/workflows/android-build.yml)）が設定されています。
1. GitHub リポジトリの **Actions** タブを開きます。
2. 最新の **Android CI & APK Build** ワークフロー実行結果を開きます。
3. 下部の **Artifacts** セクションから **`cloud-text-vault-debug-apk`** をダウンロードし、展開された `app-debug.apk` を Android 端末にインストールします。

### 2. Android Studio / ローカル環境でビルドする場合
- **必要環境**: Android Studio Ladybug 以降（JDK 17+、Android SDK 35）
- リポジトリをクローンして Android Studio で開くか、Gradle でビルドします：

```bash
gradle testDebugUnitTest assembleDebug
```

生成される APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## PC版（`cloud-text-vault`）とデータを共有する手順

1. **クラウド認証情報の設定**
   - アプリ右上の **歯車アイコン（設定）** をタップします。
   - **Google Drive** または **Dropbox** タブを選択します。
   - PC版の「設定...」画面で取得した（または PC の `~/.cloud-text-vault/config.json` に保存されている）以下の値を設定します：
     - **Google Drive の場合**: `OAuth Client ID`, `OAuth Client Secret`, `OAuth Refresh Token`（または一時利用の `Access Token`）、および同じ `保存ファイル名`（デフォルト: `cloud-text-vault.enc.json`）
     - **Dropbox の場合**: `Dropbox App Key`, `Dropbox App Secret`, `Dropbox Refresh Token`（または `Access Token`）、および同じ `Dropbox内パス`（デフォルト: `/cloud-text-vault.enc.json`）
   - **「設定を保存」** をタップします。
2. **マスターパスワードの入力と同期**
   - PC版と同じ **マスターパスワード** を入力し、**「読込」** ボタン（または上部のクラウドアイコン）をタップすると、PCで登録したデータ一覧がドロップダウンに読み込まれます。
   - 新しいテキストを入力して **「登録 (暗号化して保存)」** をタップすると、クラウド上の既存データと安全にマージされた上で AES-256-GCM で暗号化保存されます。

---

## ライセンス (License)

本プロジェクトは **[MIT License](./LICENSE)** のもとで公開されています。
