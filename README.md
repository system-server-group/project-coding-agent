# ai-project-integrations

案件情報管理システム（アプリケーション名: `sims`）。
ログイン認証のうえで、案件情報の登録・検索・更新と、各種マスタ（部署・ユーザ）の管理を行う Web アプリケーションです。
機能仕様は [docs/specs/基本設計/](docs/specs/基本設計/) を参照してください。

## 技術スタック

依存関係の正本は [build.gradle.kts](build.gradle.kts) です。

| 分類              | 内容                                                             |
| ----------------- | ---------------------------------------------------------------- |
| 言語 / ランタイム | Java 25（Amazon Corretto）                                       |
| フレームワーク    | Spring Boot 4.0.x（Spring MVC / Spring Security / Thymeleaf）    |
| DB                | PostgreSQL 18（マイグレーション: Flyway、O/R マッパー: MyBatis） |
| メール            | SMTP（開発環境では MailHog で受信を確認）                        |
| ビルド            | Gradle（Wrapper 同梱）                                           |
| E2E テスト        | Playwright + Testcontainers                                      |

## 開発環境のセットアップ

アプリケーションの起動には **JDK 25** と **Docker 互換のコンテナランタイム（Rancher Desktop を推奨）** が必要です。  
E2E エビデンスの受け入れ用 index.html 生成まで行う場合は **Python + openpyxl** も必要です。

### 1. JDK 25（Amazon Corretto）

[build.gradle.kts](build.gradle.kts) の `java.toolchain` が Java 25 を要求します。本プロジェクトでは JDK のディストリビューションとして **Amazon Corretto 25** を使用します。

- Windows:

  ```powershell
  winget install Amazon.Corretto.25.JDK
  ```

- 上記以外・手動インストール: [Amazon Corretto 25 のダウンロードページ](https://aws.amazon.com/corretto/) から取得してインストールし、`JAVA_HOME` を設定

インストール確認:

```sh
java -version   # OpenJDK Runtime Environment Corretto-25.x.x.x のように表示されること
```

### 2. コンテナランタイム（Rancher Desktop）

開発用の PostgreSQL / MailHog コンテナ（[docker-compose.yml](docker-compose.yml)）の起動と、E2E テストの Testcontainers 実行（[build.gradle.kts](build.gradle.kts) の `e2eTest` タスク）に使用します。

本プロジェクトでは **Rancher Desktop**（無償・オープンソース）を前提とします。
docker CLI / docker compose は Rancher Desktop に同梱されるため、別途インストールは不要です。

- Windows / macOS: [Rancher Desktop](https://rancherdesktop.io/) をインストール

  ```powershell
  winget install SUSE.RancherDesktop
  ```

  （macOS は `brew install --cask rancher` または公式サイトの dmg から）

- Linux: [Docker Engine](https://docs.docker.com/engine/install/) と Compose プラグインをインストール（Rancher Desktop は不要）

#### Rancher Desktop の初期設定（重要）

既定値のままでは本プロジェクトの用途で動作しません。初回起動時のウィザードまたは Preferences で以下を設定してください。

1. **Container Engine に `dockerd (moby)` を選択する**（Preferences > Container Engine。既定は `containerd`）。  
   `containerd` のままだと Docker API が提供されず、`docker` / `docker compose` コマンド、`bootRun` 時のコンテナ自動起動（spring-boot-docker-compose）、`e2eTest` の Testcontainers がすべて失敗します。
2. **Kubernetes を無効化する**（Preferences > Kubernetes > Enable Kubernetes のチェックを外す）。  
   本プロジェクトでは使用しません。有効のままでも動作はしますが、メモリ消費と起動時間が大きく増えます。
3. Windows では **WSL2 が必要**です（Rancher Desktop のバックエンドとして動作）。`wsl --status` で確認し、未導入なら `wsl --install` を実行後に OS を再起動してください。
4. Docker Desktop からの乗り換え時は、Docker Desktop を終了（自動起動も無効化）してから Rancher Desktop を起動してください。両方が同時に起動していると docker CLI の接続先が競合します。`docker context ls` で接続先を確認できます（Rancher Desktop 利用時は `default` に `rancher-desktop` 由来のソケットが表示されます）。
5. macOS で Preferences > Application > Administrative Access を許可しない場合、ソケットが `/var/run/docker.sock` に作成されず `~/.rd/docker.sock` になります。docker CLI はそのまま動きますが、Testcontainers が検出に失敗する場合は `DOCKER_HOST=unix://$HOME/.rd/docker.sock` を設定してください。

インストール確認（Rancher Desktop を起動した状態で）:

```sh
docker --version
docker compose version
docker run --rm hello-world   # dockerd (moby) 選択が正しく反映されていることの確認
```

以下のポートを使用するため、空いていることを確認してください（使用箇所は各リンク先を参照）。

| ポート      | 用途                                                                                                        |
| ----------- | ----------------------------------------------------------------------------------------------------------- |
| 8080        | アプリケーション本体（Spring Boot 既定ポート）                                                              |
| 5433        | 開発用 PostgreSQL（[docker-compose.yml](docker-compose.yml)。ホスト側 5432 の競合を避けるため 5433 を公開） |
| 1025 / 8025 | MailHog の SMTP / Web UI（[docker-compose.yml](docker-compose.yml)）                                        |

### 3. Python + openpyxl

E2E エビデンスの受け入れ用 index.html を生成する `e2eEvidenceIndex` タスク（[build.gradle.kts](build.gradle.kts)）が使用します。未導入でも証跡本体の生成は妨げられません。

- Windows:

  ```powershell
  winget install Python.Python.3.13
  ```

- macOS / Linux: [python.org](https://www.python.org/downloads/) またはパッケージマネージャからインストール

続けて openpyxl をインストールします。

```sh
pip install openpyxl
```

### 4. IDE

保存時整形・Checkstyle 連携の設定手順は [docs/ide-setup.md](docs/ide-setup.md) を参照してください。  
VSCode の場合はリポジトリを開いて推奨拡張をインストールするだけで設定が適用されます。

## アプリケーションの起動手順（開発環境）

### 1. リポジトリの取得

```sh
git clone <このリポジトリのURL>
cd ai-project-integrations
```

### 2. アプリケーションの起動

```sh
./gradlew bootRun        # Windows (PowerShell) では .\gradlew.bat bootRun
```

起動時の挙動は以下の通りです。

- 既定のプロファイルは `dev` です（[src/main/resources/application.yml](src/main/resources/application.yml) の `spring.profiles.active`）。dev 用の接続設定は [src/main/resources/application-dev.yml](src/main/resources/application-dev.yml) を参照してください。
- `spring-boot-docker-compose`（[build.gradle.kts](build.gradle.kts)）により、[docker-compose.yml](docker-compose.yml) の PostgreSQL / MailHog コンテナが自動で立ち上がります。手動で先に起動したい場合は `docker compose up -d` を実行してください。
- Flyway が [src/main/resources/db/migration/](src/main/resources/db/migration/) のマイグレーションでスキーマを作成します。
- dev プロファイル限定の初期データ投入（[DevDataInitializer.java](src/main/java/com/system_server/ai_demo/config/DevDataInitializer.java)）で、ログイン確認用のユーザ・部署が自動投入されます。

### 3. 動作確認

1. ブラウザで http://localhost:8080 にアクセスするとログイン画面が表示されます。
2. 以下の開発用アカウントでログインできます（[DevDataInitializer.java](src/main/java/com/system_server/ai_demo/config/DevDataInitializer.java) が投入する dev プロファイル専用の値です）。

   | ユーザID | パスワード | 権限           |
   | -------- | ---------- | -------------- |
   | `admin`  | `password` | システム管理者 |

3. アプリケーションから送信されるメールは実際には送信されず、MailHog の Web UI（http://localhost:8025 ）で内容を確認できます。

### 補足: 開発用 DB について

- dev プロファイルでは DB は使い捨て扱いです。スキーマ変更時は自動で clean → 再マイグレートされ（[FlywayDevConfig.java](src/main/java/com/system_server/ai_demo/config/FlywayDevConfig.java)）、起動のたびに初期データが冪等に再投入されます。
- 接続情報（URL・ユーザ・パスワード）の正本は [src/main/resources/application-dev.yml](src/main/resources/application-dev.yml) です。DB クライアントから直接接続する場合もここを参照してください。

## テスト

タスク定義の正本は [build.gradle.kts](build.gradle.kts)、テストケース仕様は [docs/tests/](docs/tests/) を参照してください。

```sh
# 単体・結合テスト（E2E タグは除外される）
./gradlew test

# E2E テスト（Playwright + Testcontainers。Docker が必要。ブラウザは初回実行時に自動ダウンロード）
./gradlew e2eTest

# E2E をエビデンス生成モード（slowMo 有効）で実行し、受け入れ用の証跡を生成
./gradlew e2eEvidence
./gradlew e2eEvidence -Pe2e.slow-mo-ms=0   # slowMo を切って証跡のみ取得（flaky 調査等）

# テスト実施単位の指定（未指定なら docs/tests/テストケース仕様/ 配下の最新を使用）
./gradlew e2eTest -Pe2e.target=<実施単位>
```

## コード整形・静的検査

整形・検査ルールの正本は [config/eclipse/eclipse-formatter.xml](config/eclipse/eclipse-formatter.xml) と [config/checkstyle/checkstyle.xml](config/checkstyle/checkstyle.xml) です。役割分担の詳細は [docs/ide-setup.md](docs/ide-setup.md) を参照してください。

```sh
# 整形・規約チェック
./gradlew spotlessCheck checkstyleMain checkstyleTest

# Spotless による自動修正
./gradlew spotlessApply
```

## ドキュメント

ドキュメントは [docs/](docs/) 配下にあります。

- [docs/specs/基本設計/](docs/specs/基本設計/) — 基本設計（ER 図、画面レイアウト・機能仕様 等）
- [docs/specs/詳細設計/](docs/specs/詳細設計/) — 詳細設計（UML、各種定義書）
- [docs/specs/コーディング規約/](docs/specs/コーディング規約/) — コーディング規約
- [docs/tests/](docs/tests/) — テストケース仕様・テスト観点
- [docs/ide-setup.md](docs/ide-setup.md) — IDE セットアップ手順
