# beta 这一支

## 现在两条线怎么摆

- **main**：公开仓 origin 那一条，只有 stable 一个 flavor，`app/src/beta/` 整个目录不在这一支。
- **beta**：= main 的全部 + `app/src/beta/java/` 那几份源码。**这条也推 origin**（10-24 定）。
  origin 是公开仓，推上去就等于公开 —— 推前把这四份文件现扫过一遍：手机号、身份证形状、
  8–20 位纯数字串、邮箱、`token/password/authorization` 字样，全部 0 命中，里面只有本仓自己写的界面和假 GPS。

**要守的不是"不推"，是"不并进 main"**：main 那一支编出来的正式版要从源码层面就没有这些类，
所以合并方向只有 main → beta；把 beta 合回 main、或者在 main 上动 `app/src/beta/`，都会让正式版带上实现。

`app/build.gradle.kts` 里 flavor 是按"工作区里有没有 `app/src/beta/java`"来定的，
两条分支那一份文件写得一模一样，以后 main 合过来不会在这一支打架。

## 编哪一条

    git checkout beta
    export JAVA_HOME=/d/app/jdk-21
    ./gradlew --offline -q assembleBetaRelease
    # 出来是 app/build/outputs/apk/beta/release/app-beta-release.apk，版本名带 -beta

在 main 上跑 `assembleBetaRelease` 会直接报"没有这个 task"，不会悄悄编出一个
挂着 beta 名字、实现却是空的壳子。

装机照旧：push 到 `/data/local/tmp` 再 `su -c 'pm install -r'`（MIUI 不认 `adb install` 这条）。

## 往后的流程

新功能一律写在 main：提交、推 origin、发 release、抬 versionCode。要出 beta 包时回到这条分支，把 main 合进来：

    git checkout beta && git merge --no-ff main

编包与装机：`./gradlew --offline -q assembleBetaRelease` → push 到 `/data/local/tmp` → `su -c 'pm install -r'`。

**合进来以后 `app/src/beta/` 会不会被 main 那笔删除带回来** —— 会，而且 git 不会报冲突：
`cd55fb6`（两条分支的共同祖先）里那几份文件在、`main` 上 `6b05bae` 把它们删了、这一支没动过它们，
三方合并判"一边删、一边没动 = 删除赢"，合完 `app/src/beta` 就没了，`assembleBetaRelease` 静默变回不存在的 task。
所以每次合完**第一件事是数那几份文件还在不在**：

    git ls-files app/src/beta | wc -l      # 应该是 4
    git checkout HEAD~1 -- app/src/beta    # 少了就用这条抢回来（HEAD~1 = 合之前的这一支）
    git commit -am "把 main 那笔删除合掉：这一支要有这几份"

（`app/build.gradle.kts` 不用手工救：它是"看目录在不在"定 flavor 的，文件抢回来 flavor 就自己回来了。）

## 关于包的 md5（2026-10-24 定下来的）

release 包的 md5 **不能**当"重编一致"的证据 —— 同一份源码连编两次 md5 就不一样，
实测过。差在三处，都在包体内容之外：

1. APK Signing Block 每一轮签出来都不一样 —— 实证：同一份源码两次构建，380 个 zip 条目
   连 CRC 带时间戳全都相同，整个文件 md5 却不同，差只能落在签名块上（它不是 zip 条目）；
2. R8 每一轮打一个 map-id，进了 classes.dex（那 146 个字节）和 assets/dexopt/baseline.prof；
3. AGP 把当次 commit 的 sha 写进 META-INF/version-control-info.textproto。

要比"源码没动所以包也没动"，就绕开这三样：按条目名排序、跳过 META-INF/、
把其余条目喂进 sha256；dex 另外比可打印串的种类数。
这次用这套验过两对，各自全等：beta@beb9526 vs beta@cd55fb6、stable@beb9526 vs stable@6b05bae。
（老的"重编 md5 相同"那条自证办法作废，别再拿它当证据。）
