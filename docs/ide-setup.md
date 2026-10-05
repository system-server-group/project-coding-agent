# IDE セットアップ手順

このドキュメントは、エディタ上での自動整形・Lint 結果が CI と一致するようにするための IDE 設定をまとめたものである。
**この設定を行わないと、ローカルでの保存時整形・違反検出と CI の結果がずれる可能性がある。**

## 整形・検査の役割分担

| 観点                              | エディタ                             | Gradle (手動 / 将来 CI)         |
| --------------------------------- | ------------------------------------ | ------------------------------- |
| Java 整形 (Eclipse JDT formatter) | VSCode Java extension が保存時に実行 | `./gradlew spotlessCheck/Apply` |
| Checkstyle                        | VSCode Checkstyle 拡張が常時赤線表示 | `./gradlew checkstyleMain`      |
| 末尾空白・最終改行 (Java 以外)    | VSCode の `files.*` 設定で保存時実行 | `./gradlew spotlessCheck`       |

エディタ側は **Eclipse JDT formatter を VSCode が自前 JVM で呼ぶ** ため、Gradle/JVM 起動コストはかからず保存時に瞬時に整形される。Spotless は Gradle 経由でしか走らないので、エディタ操作中には介在しない。

## 共有する設定ファイル

| 設定ファイル                           | 役割                                                                 | 参照する側                           |
| -------------------------------------- | -------------------------------------------------------------------- | ------------------------------------ |
| `config/eclipse/eclipse-formatter.xml` | Java フォーマッター (Google Java Style / 4-space)                    | Spotless `eclipse()` / IDE 全般      |
| `config/checkstyle/checkstyle.xml`     | Checkstyle ルール (Google Checks ベース + プロジェクト固有 override) | Gradle `checkstyle` / IDE プラグイン |
| `.editorconfig`                        | 文字コード・改行・インデントの基本設定                               | IDE 全般                             |
| `.gitattributes`                       | 改行コードを LF に強制                                               | `git checkout` 時                    |
| `.vscode/settings.json` (commit 済)    | VSCode の formatter / Checkstyle 連携設定                            | VSCode                               |
| `.vscode/extensions.json` (commit 済)  | 推奨拡張のリスト                                                     | VSCode                               |

### Checkstyle ルールの方針

`config/checkstyle/checkstyle.xml` は [google_checks.xml](https://github.com/checkstyle/checkstyle/blob/master/src/main/resources/google_checks.xml) (Google 公式) をベースとし、以下を本プロジェクト方針に合わせて override している:

- `Indentation` を `basicOffset=4` / `lineWrappingIndentation=8` に変更 (Eclipse formatter の AOSP インデントと整合)
- `PackageName` をアンダースコア許可に変更 (`com.system_server.ai_demo` を許容)
- Javadoc 系チェック (`JavadocMethod` / `MissingJavadoc*` / `SummaryJavadoc` / `AtclauseOrder` ほか) を `SuppressionSingleFilter` で一括抑止 (段階的 enable 想定)
- severity の default を warning から error に変更

import 順序は Google Checks の `CustomImportOrder` (`STATIC###THIRD_PARTY_PACKAGE`) に合わせるため、Spotless / VSCode 双方で **「static 先頭 + 残り全部を 1 グループ・アルファベット順」** となるように指定している。

## VSCode (推奨)

`.vscode/settings.json` と `.vscode/extensions.json` は **リポジトリで管理されている**ため、VSCode でこのプロジェクトを開けば自動的に設定が適用される。あとは推奨拡張をインストールするだけでよい。

### 手順
1. VSCode でプロジェクトを開く
2. 右下に「推奨拡張をインストールしますか？」のダイアログが出るので承諾
   - 含まれる拡張: `vscjava.vscode-java-pack`, `shengchen.vscode-checkstyle`, `redhat.vscode-xml`, `redhat.vscode-yaml`, `editorconfig.editorconfig`
3. Java ファイルを開いて Ctrl+S で保存 → 自動整形・import 整理が走ることを確認
4. Java ファイルに故意の規約違反 (例: 未使用 import) を書き、赤線が出ることを確認

### 動作内容 (settings.json による)
- `editor.formatOnSave` で保存時整形
- `java.format.settings.url` → `config/eclipse/eclipse-formatter.xml` (`GoogleStyle` プロファイル)
- `java.completion.importOrder`: `["#", ""]` (static 先頭 → 残り 1 グループ・アルファベット順、Spotless 側および Google Checks の `CustomImportOrder` と整合)
- `source.organizeImports` で未使用 import 削除
- `java.checkstyle.configuration` → `config/checkstyle/checkstyle.xml`
- `java.checkstyle.version` → `10.20.2` (Gradle 側の `toolVersion` と一致)

### ユーザー設定で確認する項目
以下は VSCode の application スコープ設定のため `.vscode/settings.json` には書けない。
ユーザー設定 (`Ctrl+,` → 検索) で念のため確認する。

- `java.checkstyle.autocheck`: `true` (デフォルト true、ファイル保存時に自動検査が走る)

## IntelliJ IDEA / Android Studio (任意)

VSCode を使わない場合は手動設定する。

### Java フォーマッター
1. `Settings` → `Editor` → `Code Style` → `Java`
2. 歯車アイコン → `Import Scheme` → `Eclipse XML Profile`
3. `config/eclipse/eclipse-formatter.xml` を選択
4. Scheme を **Project** にして保存
5. `Settings` → `Tools` → `Actions on Save` で以下を有効化
   - ✅ Reformat code
   - ✅ Optimize imports

### Checkstyle
1. プラグイン `CheckStyle-IDEA` をインストール
2. `Settings` → `Tools` → `Checkstyle`
   - Checkstyle version: `10.20.2` (`build.gradle.kts` の `toolVersion` と一致させる)
   - Configuration File: `+` → `config/checkstyle/checkstyle.xml` を追加し、active 化

## 動作確認 (Gradle 経由)

エディタ設定が CI と一致しているかを Gradle 側でも検証できる (時間がかかるので頻繁には実行しない)。

```sh
./gradlew spotlessCheck checkstyleMain checkstyleTest
```

差分が出た場合は、

```sh
./gradlew spotlessApply
```

で Spotless が修正可能な差分を自動修正する。Checkstyle 違反は自動修正されないので、エディタの表示に従って手で直すこと。

## 将来 CI を導入する際

CI 上では Gradle で同じことが回せる。以下を CI ジョブに追加すればよい:

```sh
./gradlew spotlessCheck checkstyleMain checkstyleTest build
```

`spotlessCheck` は formatter.xml に基づいた整形違反を検出する。エディタ側で保存時整形が効いていればここで違反は出ないはず。
