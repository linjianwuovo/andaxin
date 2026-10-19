# 架构理解与新仓分层

2026-10-19。读法：先看第 1 节知道这是个什么东西，再看第 3 节知道新仓怎么分层，第 5 节是要他拍的事。

产出方式：`system-modeler` 建现状模型，`c4model` 出 `system-model.dsl`，`graphviz` 出 `target-layering.dot`，证据清单在 `system-model.evidence.md`。

## 1. 系统边界

这是一个**纯客户端**。没有我们自己的服务端：所有数据都是手机直连校方或第三方厂商的接口，本机只存会话、主题、已读、路线四类东西。所以"底层"这个词在这里指的从来不是服务器，而是三样：会话与 token 刷新、报文编解码、界面到数据的接缝。

外部依赖 12 个端点群：`in.aiit.edu.cn`（教务门户，`.do` 一族）、`aiitpass.fifedu.com`（登录）、`fdygl`（查寝）、`cqc`（学分）、`ldjy`（劳动）、`ywlz`（待办事务/请假）、`repair`（报修）、`sports.aiit.edu.cn:8082`（智慧体育，明文 http 且仅校内可达）、`sttp`/`resc`/`izuoye.fifedu.com`（AI 课堂与作业）、`17wanxiao.com`（校园卡，要先用校方会话换厂商会话）、高德瓦片（GCJ-02 网格）、GitHub Releases（安装包）。

## 2. 现状模型里最要紧的三件事

**首页是个上帝屏。** `HomeDashboard.kt` 703 行，一个人注入六个 Repository 加 TokenManager。它让首页一次出全部数据（这是他明确要的"一次出，别一路蹦"），代价是首页的依赖面最宽，任何一块协议改都要动它。新仓保留"一次出"这个行为，但把聚合从界面层挪到状态层：`HomeFeed` 之下挂六个源，界面只观察一个 `UiState`。

**协议知识分布极不均。** 消息、待办事务、报修、AI 课堂、运动这五块有厚抓包痕迹（每块 3~23 处明文证据）；课程表、成绩、学分、劳动、节假日、查寝、登录这七块只有接口名，字段是学长当年入库时写的样子。新仓按这个差别决定"哪屏能真接、哪屏只能先留接口"，不是按界面上线顺序决定。

**旧运动实现还挂在路由表上。** 24 个文件、两条进不去的死路由，而且 `core/location` 还 import 它两个类型。新仓不带这块，地图和轨迹都从头写。

## 3. 新仓分层

依赖只许从上往下，反向一律不许：

```
装配 AppGraph            唯一允许 new 的地方
  └ 平台适配             GpsFeed / LiveActivity / TileMapLayer / Store / Scan
      └ 协议 Contract    一块业务一个接口，只声明"要什么数据"
          ├ LocalFeed    现在就有：假数据 + 本地路线文件，让每一屏立刻可见可点
          └ CaptureFeed  他补完抓包才连上的那一侧（OkHttp + kotlinx.serialization）
  └ 状态 ViewModel       StateFlow + @Immutable UiState
  └ 界面 feature/*/ui    Compose + Strong Skipping
      └ design           色/字/形令牌 + 玻璃底栏 + 拖拽引擎
```

分层的取舍理由都在 `target-layering.dot` 的"选型变更"那一簇里，六条：去 Hilt、去 Retrofit+Gson、13 个 DataStore 合一个、osmdroid 换自写瓦片层、去掉 icons-extended、开 R8 加 Baseline Profile。玻璃那套（Miuix + haze + Kyant）保留，只把模糊范围收到底栏那一条并缓存。

**接缝就一个：Contract。** 他以后补抓包，只往 `CaptureFeed` 那一侧填，界面和状态一行不动。这也是"UI 先行"能成立的唯一前提。

## 4. 证据强度与未知

界面清单是 high（现读代码 + 装机截图核过）。字段级契约按第 2 节说的分块，medium 的那七块在接入前一律算未知。`izuoye` 作业那个 Api 是 low：零 `.do`、零抓包注释，只有反汇编级的 URL 常量。考勤两个接口和二维码格式在新仓里**不算已知**，需要重抓。

## 5. 需要他拍的（我先按默认走，不同意随时改）

- 包名默认 `com.andaxin`，和现在的 `com.linxin` 并排装、互不影响；将来要不要接管 `com.linxin` 让老用户直接升级，等他决定。
- LICENSE 暂不放，默认全版权归他。
- 目录 `…/2026-09-28/e5703bf8/andaxin`，与旧仓同级；GitHub 上另开新仓、旧仓转归档这件事由他手动做（我不碰远端）。
- 施工顺序：批次一 壳+设计系统+五格主屏 → 批次二 运动六屏+地图 → 批次三 其余十七屏 → 收尾装机量性能并出"接口待接入清单"。
