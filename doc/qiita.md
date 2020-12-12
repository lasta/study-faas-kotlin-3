# Kotlin/Native on AWS Lambda その2 - カスタムランタイム

## TL;DR
* Kotlin/Native で書いたコードを AmazonLinux2 上でビルド
* Kotlin/Native でカスタムランタイムを構築
* SAM Local 上で動作確認

## はじめに
こんにちは。 [lasta][github-lasta] です。

本記事は [Kotlin Advent Calendar 2020](https://qiita.com/advent-calendar/2020/kotlin) 13日目の記事です。
昨日は [ReyADayer](https://qiita.com/ReyADayer) さんでした。

また、本記事は「Kotlin/Native on AWS Lambda」3部作の2作目になります。
Kotlin/Native を初めて触る方は [Kotlin/Native on AWS Lambda その1 - 開発環境構築][study-faas-kotlin1] を先にお読みいただくことを推奨します。

* [Kotlin/Native on AWS Lambda その1 - 開発環境構築][study-faas-kotlin1]
* Kotlin/Native on AWS Lambda その2 - カスタムランタイム (本記事)
* Kotlin/Native on AWS Lambda その3 - 外部ライブラリ (Sentry) の導入 (執筆中)

## Kotlin/Native とサーバレスの現状
Kotlin 1.4 にて、 Kotlin/Native 含め Kotlin 及びその周辺に大幅なアップデートがありました。
詳細は公式ページ [What's New in Kotlin 1.4.0][kotlin-1.4] をご確認ください。

加えて [kotlinx.serialization 1.0 がリリースされた][kotlinx.serialization GA] (GA した) ことにより、 Kotlin/Native をプロダクション利用しやすくなりました。

一方で Kotlin/Native はまだまだ発展途上にあります。
JetBrains 公式のサーバレスフレームワークとして [Kotless][Kotless] がありますが、 本記事執筆時点 (2020/12/12) では Kotlin-JVM しか対応していません。
GraalVM 向けはベータリリース段階であり、 Multiplatform 向け (JVM/JS/Native) は開発中です。

そのため、 AWS Lambda の [Custom Runtime][Custom Runtime] 上で Kotlin/Native を動かすことをゴールとして進めていきます。

### 周辺環境
* MacBook Pro 2019
  * macOS Big Sur 11.0.1
* :wrench: IntelliJ IDEA Ultimate 2020.3
  * Community 版でもおそらく可能 ([機能比較](https://www.jetbrains.com/idea/features/editions_comparison_matrix.html))
* :wreanch: docker desktop 3.0.1
* Kotlin 1.4.20
  * [IntelliJ IDEA](https://plugins.jetbrains.com/plugin/6954-kotlin) および [Gradle](https://kotlinlang.org/docs/reference/using-gradle.html) が自動的に環境構築してくれるため、手動でのインストールは不要

:wrench: : 事前に同一またはそれ以降のバージョンのインストールが必要

プロジェクト構築等については、 [前回の記事][study-faas-kotlin1] に詳細の記載があります。

## 実装
[ソースコード](https://github.com/lasta/study-faas-kotlin-2)

### 実装の流れ
1. プロジェクトの作成
2. 開発環境の構築
3. [kotlinx.serialization][kotlinx.serialization] の導入
4. [ktor client][ktor-client] の導入
5. [template.yaml][aws lambda template] の作成
6. [AWS Lambda カスタムランタイム][AWS Lambda custom runtime] の実装
7. 関数本体の実装

### 1. プロジェクトの作成
IntelliJ IDEA を用いて Kotlin の Native Application プロジェクトを作成します。
作成手順は [前回の記事](https://qiita.com/lasta/items/9169727d89829cf007c3#%E3%83%97%E3%83%AD%E3%82%B8%E3%82%A7%E3%82%AF%E3%83%88%E4%BD%9C%E6%88%90) にて詳細に解説しているため、そちらを参照してください。

### 2. 開発環境の構築
AWS Lambda は Amazon Linux 2 で動作しているため、 Amazon Linux 2 向けのバイナリを作成する必要があります。
こちらも作成手順は [前回の記事](https://qiita.com/lasta/items/9169727d89829cf007c3#%E3%83%93%E3%83%AB%E3%83%89%E7%92%B0%E5%A2%83%E3%81%AE%E6%BA%96%E5%82%99) に詳細を記載しております。

```Dockerfile:Dockerfile
FROM amazonlinux:2
RUN yum -y install tar gcc gcc-c++ make ncurses-compat-libs
# for curl
RUN yum -y install libcurl-devel openssl-devel
RUN amazon-linux-extras enable corretto8
RUN yum clean metadata
# for gradle
RUN yum -y install java-1.8.0-amazon-corretto-devel
RUN yum -y install install which zip unzip
RUN curl -s http://get.sdkman.io | bash && \
    bash ${HOME}/.sdkman/bin/sdkman-init.sh && \
    source ${HOME}/.bashrc && \
    sdk install gradle
```

このあと作成する Lambda Function にて SSL 通信を行うため、 OpenSSL と libcurl もインストールしています。
[Lambda の動作環境](https://hub.docker.com/r/amazon/aws-sam-cli-emulation-image-provided.al2) には OpenSSL と libcurl が予め導入されています。

```console
sh-4.2# echo $LD_LIBRARY_PATH
/var/lang/lib:/lib64:/usr/lib64:/var/runtime:/var/runtime/lib:/var/task:/var/task/lib:/opt/lib

sh-4.2# ls /usr/lib64
# 一部抜粋
/usr/lib64/libcurl.so.4
/usr/lib64/libcurl.so.4.5.0
/usr/lib64/openssl
```

1 で作成したプロジェクトをビルドし実行できることを確認できたら OK です。

```console:host
$ docker build -t gradle-on-amazonlinux2:1.0 .
$ docker run --memory=3g -v "$(pwd)":/root/work -itd gradle-on-amazonlinux2:1.0
$ docker exec -it $(docker ps | grep 'gradle-on-amazonlinux' | awk '{print $1}') /root/work/gradlew -p /root/work/ clean build
# 動作確認
# 作成したプロジェクトによって実行可能ファイルの名前が変わります
$ docker exec -it $(docker ps | grep 'gradle-on-amazonlinux' | awk '{print $1}') /root/work/build/bin/native/releaseExecutable/study-faas-kotlin-3.kexe
Hello, Kotlin/Native!
```

### 3. [kotlinx.serialization][kotlinx.serialization] の導入
「6. AWS Lambda カスタムランタイム」で必要となるため、 kotlinx.serialization を導入します。
kotlinx.serialization は Gradle plugin として配布されています。

```kotlin:build.gradle.kts
plugins {
    kotlin("multiplatform") version "1.4.20"
    kotlin("plugin.serialization") version "1.4.20" // 追加
}
```

また、 [プラグインの追加の後に、 Json シリアライザも導入する必要があります](https://github.com/Kotlin/kotlinx.serialization#dependency-on-the-json-library)。

```kotlin:build.gradle.kts
kotlin {
    sourceSets {
        @kotlin.Suppress("UNUSED_VARIABLE")
        val nativeMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
            }
        }

        @kotlin.Suppress("UNUSED_VARIABLE")
        val nativeTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
            }
        }
    }
}
```

#### (余談) kotlinx.serialization の使用方法と動作確認
```kotlin:src/nativeTest/kotlin/me/lasta/sample/serialization/SerializationSample.kt
package me.lasta.sample.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class Project(val name: String, val language: String)

class SerializationSample {

    @Test
    fun serialize() {
        val actual = Json.encodeToString(
            Project("kotlinx.serialization", "Kotlin")
        )
        assertEquals(
            expected = """{"name":"kotlinx.serialization","language":"Kotlin"}""",
            actual = actual
        )
    }

    @Test
    fun deserialize() {
        val actual = Json.decodeFromString<Project>(
            """{"name":"kotlinx.serialization","language":"Kotlin"}"""
        )
        assertEquals(
            expected = Project("kotlinx.serialization", "Kotlin"),
            actual = actual
        )
    }
}
```

```console:build
$ docker exec -it $(docker ps | grep 'gradle-on-amazonlinux' | awk '{print $1}') /root/work/gradlew -p /root/work/ clean build
```

```console:run_test
$ docker exec -it $(docker ps | grep 'gradle-on-amazonlinux' | awk '{print $1}') /root/work/build/bin/native/debugTest/test.kexe
[==========] Running 2 tests from 1 test cases.
[----------] Global test environment set-up.
[----------] 2 tests from me.lasta.sample.serialization.SerializationSample
[ RUN      ] me.lasta.sample.serialization.SerializationSample.serialize
[       OK ] me.lasta.sample.serialization.SerializationSample.serialize (1 ms)
[ RUN      ] me.lasta.sample.serialization.SerializationSample.deserialize
[       OK ] me.lasta.sample.serialization.SerializationSample.deserialize (0 ms)
[----------] 2 tests from me.lasta.sample.serialization.SerializationSample (1 ms total)

[----------] Global test environment tear-down
[==========] 2 tests from 1 test cases ran. (2 ms total)
[  PASSED  ] 2 tests.
```

![gradle-test.png](gradle-test.png)

### 4. [ktor client][ktor-client] の導入
### 5. [template.yaml][aws lambda template] の作成
### 6. [AWS Lambda カスタムランタイム][AWS Lambda custom runtime] の実装
### 7. 関数本体の実装

## 動作確認

## 今後

## 参考文献


[github-lasta]: https://github.com/lasta
[study-faas-kotlin1]: https://qiita.com/lasta/items/9169727d89829cf007c3
[kotlin-1.4]: https://kotlinlang.org/docs/reference/whatsnew14.html
[kotlinx.serialization GA]: https://blog.jetbrains.com/kotlin/2020/10/kotlinx-serialization-1-0-released/
[Kotless]: https://github.com/JetBrains/kotless
[Custom Runtime]: https://docs.aws.amazon.com/ja_jp/lambda/latest/dg/runtimes-custom.html
[idea]: https://www.jetbrains.com/idea/
[kotlinx.serialization]: https://github.com/Kotlin/kotlinx.serialization
[ktor-client]: https://ktor.io/docs/clients-index.html
[aws lambda template]: https://docs.aws.amazon.com/ja_jp/AWSCloudFormation/latest/UserGuide/quickref-lambda.html
[AWS Lambda custom runtime]: https://docs.aws.amazon.com/ja_jp/lambda/latest/dg/runtimes-custom.html

<!-- TODO: delete below if needless -->
[AWSLambda]: https://aws.amazon.com/jp/lambda/
[kotlin-event-14]: https://kotlinlang.org/lp/event-14/
[kotlinlang]: https://kotlinlang.org/
[jetbrains]: https://www.jetbrains.com/

