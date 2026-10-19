# 现状证据索引 · 安大信 beta 分支

生成 2026-10-19，来源全部是 `git show beta:<路径>` 现读（未 checkout）。本文件是**规格来源**，不是代码来源 —— 新仓照这份清单重做界面，但不引用其中任何一行源码，规则见 `../CLEAN_ROOM.md`。

计数已亲自复核：NavGraph 567 行、注册 33 个 destination；`feature/*/ui` 下 31 个 Screen 级文件、38 个 Screen 级 composable；`core/designsystem` 17 个文件 1535 行；全仓 195 个 Kotlin 文件、36845 行。

路径前缀 `…` = `app/src/main/java/com/linxin/`。

## 底栏五格

定义在 `…/feature/home/ui/HomeScreen.kt:50-56`（`private val tabs`），文案在 `res/values/strings.xml:10-13` 与 `:537`，渲染器 `…/feature/home/ui/LxBottomBar.kt:106-111`（那里的默认列表只有 4 格，被 HomeScreen 覆盖后没删，属旧副本）。

| 格 | 路由 | 实现 | 数据来源 | 报文证据 | confidence |
|---|---|---|---|---|---|
| 首页 | `home` 页 0 | `…/feature/home/ui/HomeDashboard.kt` 703 行 | 聚合 Schedule/Checkin/Holiday/Sports/Labor/AiClass 六个 Repository + TokenManager | 各子模块 | high |
| 课程表 | `home` 页 1 | `…/feature/schedule/ui/ScheduleScreen.kt` 438 | `…/feature/schedule/data/ScheduleRepository.kt` → ScheduleApi（in.aiit.edu.cn） | 只有 `.do` 名 ×2，无抓包注释 | 结构 high / 字段 medium |
| 消息 | `home` 页 2，另有 `messages/list` | `…/feature/messages/ui/MessagesScreen.kt` 470（+`MessagesUi.kt` 210） | MessagesRepository + 本地 `…/core/messages/MessagePrefs.kt` | 抓包 ×4、`.do` ×7 | high |
| 公告 | `home` 页 3 | `…/feature/news/ui/NewsScreen.kt` 358 | NewsRepository → NewsApi | 抓包 ×1、`.do` ×4 | high |
| 我的 | `home` 页 4 | `…/feature/home/ui/ProfileScreen.kt` 336 | 仅本地 `DeveloperPrefs`，无接口 | 不适用 | high |

## 从"我的"进

查寝 `checkin/list` 625 行 + `checkin/detail/{taskDateId}` 422 行（fdygl.aiit.edu.cn，`.do` ×4、无抓包注释，medium）；AI 课堂 `aiclass/home` 414、`aiclass/scan` 476、`aiclass/detail/{classId}` 1255、`aiclass/homework/...` 322（sttp.fifedu.com 抓包 ×6、`.do` ×20 = high；izuoye 那个 Api 零 `.do` 零抓包 = low）；更多 `more` 416（写死入口卡）；关于 `about` 378；主题 `theme` 373 + `ColorPickerDialog.kt` 290。

## 从"更多功能"进

节假日 `holiday/list` 271 + `holiday/register/{holidayId}` 311（+`HolidayDatePicker.kt` 459，Api 里 `.do`/抓包均 0 = low）；劳动 `labor/summary` 369（`.do` ×3 = medium）；报修 `repair/list` 184 + `repair/detail/{bxdh}` 432 + `repair/form` 307（抓包 ×3、`.do` ×14 = high）；待办事务 `leave/list` 942 + `leave/create` 409（抓包 ×2、`.do` ×23，表单字段来自 `getFlowSheet.do` = high）；校园卡 `card` 630（+`CardActions.kt` 237、`CardRecharge.kt` 318；抓包 ×3、`.do` ×4 = high）；成绩 `exam/scores` 329、学分 `credit/overview` 319（各 `.do` ×3 = medium）。

## 运动

路由名沿用旧的 `running/*`，落地全是 `…/feature/sports/`。六屏在 `…/feature/sports/ui/SportsScreens.kt`（1022 行、6 个 Screen）：开始运动、运动中、运动记录、运动明细、考勤维护、路线规划。支撑文件：`SportsRepository.kt`、`SportsApi.kt`（http://sports.aiit.edu.cn:8082，仅校内）、`SportsParse.kt`（`.do` ×10）、`SportsUploadCodec.kt`（9 个键 RSA、`runningRoute` 明文）、`service/SportsRunEngine.kt` 425 行（三秒一点、`Wander` 随机游走 ±2.5m）、`…/core/map/LiveTrackMap.kt` 185 行（osmdroid + 高德瓦片，三层坐标换算）、`…/core/location/InternalGpsFeed.kt`。证据：抓包 ×15、`.do` ×14 = high。

## 无路由的链路屏

`…/feature/splash/ui/SplashOverlay.kt` 58（落点由 TokenManager 决定）、`onboarding` 102（+Illustration 377）、`login` 246（+Illustration 275，RSA 口令）、`news/detail/{newsId}` 146。

## 死角（新仓不带过来）

- `feature/running` 24 个文件仍在，NavGraph 还注册 `running/club`、`running/exercise/{autoId}/{memberId}`，但全仓无 navigate 调用 —— 进不去的死路由。
- `…/core/location/InternalGpsFeed.kt:4-5` 还 import 旧 `feature/running` 的 `GeoDistance`/`TrackPoint`，摘的时候会被牵连。
- `Routes.kt` 里 `RUNNING_SIM`、`RUNNING_RESULT` 无注册，纯死常量；`SPORT_*` 常量反过来复用旧 route 名。
- 底栏默认 4 格与真实 5 格不一致。
- `NewsDetailScreen` 146 行 + 仓里 Coil 没配网络引擎 → 远程封面图静默不出。

## 上游原文残留（本次盘查的量化结果）

与 fork 入库提交 `a398e4c` 同路径仍存在的 140 个文件里，**字节级未改的 48 个、2469 行**，占全仓 6.7%。集中在 `core/auth`（5 个里 4 个）、`navigation`（3）、`feature/{aiclass,login,labor,exam,credit}/data`、`feature/schedule/data`、`core/notification`、`core/location`、`core/designsystem/theme`、已废的 `feature/running/exercise/domain`。

## 三处公开痕迹（旧仓，本仓不存在）

GitHub 仓库页挂着 `forked from Relianttt/lightxin`；`README.md:118` 明写 fork 来源；应用内文案 `res/values/strings.xml:378` 与 `values-en/strings.xml:377`（`ui_038`）写着"安大信基于轻小信重新设计"。
