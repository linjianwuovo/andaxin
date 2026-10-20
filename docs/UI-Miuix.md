# 界面上哪些是 Miuix、哪些不是，以及为什么

10-20 上午这一轮把可交互控件过完了一遍。判据很简单：**凡是要按、要拖、要输入的，走 Miuix；
只是把字画出来的，留 Material3 的 `Text`。**

## 已经是 Miuix 的

| 位置 | 组件 |
|---|---|
| 全局 | `MiuixTheme`（套在 `MaterialTheme` 里面一层，只覆盖 paper/ink/card/outline/dividerLine/error/primary 七个色） |
| 每一屏的壳 | `Scaffold` + `SmallTopAppBar`（`BriefKit.ScaffoldPage`） |
| 卡片 / 分节标题 / 分隔线 | `Card`、`SmallTitle`、`HorizontalDivider`（`shell/Bits.kt` 的 `Sheet` / `SectionTitle` / `Hairline`） |
| 设置里那种"左标题右值"一行 | `BasicComponent`（`shell/Bits.kt` 的 `Labeled`） |
| 所有提交 / 销假 / 确认键 | `Button` + `ButtonDefaults.buttonColorsPrimary`，危险操作另配 `dangerColors()` |
| 所有输入框 | `TextField`（**它没有 `placeholder` 槽，提示语走 `label`**） |
| 主题页四根滑杆 | `Slider`（`valueRange` 默认 0f..1f，松手才落盘） |

## 故意留 Material3 的

**日期选择器**（`DatePicker` / `DatePickerDialog` / `rememberDatePickerState`）。
Miuix 0.9.4 的 `miuix-ui` 里没有日期控件 —— 把 `classes.jar` 里 `top/yukonga/miuix/kmp/**`
的类名列出来扫过 `datepicker`/`timepicker`/`calendar`，**零命中**。所以表单里日期那一格点开的是 M3 的弹层。
它跟 Miuix 的圆角和配色放在一起确实看得出是两套，这一条先记着，等 Miuix 出了再换。

真要现在就用 Miuix 的样子，能拿到的砖是 `NumberPicker`（`NumberPickerKt` / `NumberPickerColors` /
`NumberPickerDefaults`）和 `ColorPicker` —— 也就是**三列滚轮自己拼一个年月日**，
自己处理每月天数、闰年、以及"2 月 30 日"这种组合。那是一整块新代码，不是换个 import，
所以没顺手做。

**纯文字**。Miuix 的 `Text` 字号参数是 **Dp** 不是 Sp（`fontSize: TextUnit.Dp`），
把满屏的 `13.sp` 机械换成 `13.dp` 会跟着系统字号设置走样，而这两者的差别在国产 ROM 上
是要他拿手指试的，不是我能靠读屏看出来的。共用壳那几个 `Text` 已经在 `Bits.kt` 里走了 Miuix，
业务屏上的先留 M3。要真换，得连"系统字号放大以后会不会撑破卡片"一起验，那是单独一趟活。

## 一条没动的红线

底栏五个 tab 的**结构**和四根玻璃滑杆的**规格**不许动（`shell/ShelfBar.kt`）。
换 UI 库换到把底栏改成 Miuix 的 `NavigationBar` 是另一回事 —— 那个玻璃滑块是我们自己算的，
HyperOS 上才刚调对。
