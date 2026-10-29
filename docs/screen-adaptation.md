# 屏幕适配说明（800×480 车载横屏 + 普通手机）

## 1. 为什么不能按"设备类型"适配

车机的物理分辨率几乎都是 **800×480**，但 `density`（dpi）五花八门。
同一块 800×480 的屏，在 Android 里折算出的 **dp 尺寸可能差 2 倍**：

| density 桶 | 实际 dpi | 折算 dp | 高度够不够 |
|---|---|---|---|
| `mdpi` | 160 | **800 × 480** | 够（480） |
| `tvdpi` | 213 | **601 × 361** | 不够 |
| `hdpi` | 240 | **533 × 320** | 不够 |
| `xhdpi` | 320 | **400 × 240** | 很紧张 |

结论：**px 尺寸相同的车机，可用 dp 高度可能是 240 / 320 / 361 / 480。**
所以适配必须基于「Compose 实际拿到的 `maxWidth` / `maxHeight`」，而不是 `smallestScreenWidth`
或设备型号。

## 2. 决策规则

```kotlin
private val ShortHeight = 400.dp   // 低于此高度：底栏/侧栏都不划算
private val WideWidth   = 640.dp   // 宽于此值：放得下侧栏

fun layoutModeFor(width: Dp, height: Dp): LayoutMode = when {
    height < ShortHeight -> LayoutMode.TopTabs
    width  >= WideWidth  -> LayoutMode.Rail
    else                 -> LayoutMode.BottomNav
}
```

**先判高度、再判宽度** 是关键顺序：533×320 的车机虽然够"宽"，但高度只有 320dp，
若走侧栏/双栏，色盘会被压到 200dp 以下不可用；走顶部标签 + 可滚动单栏反而更实用。

### 三种外壳

| 布局 | 结构 | 上下文高度占用 |
|---|---|---|
| `BottomNav` | 状态条 + 内容 + 底部导航 | ~104 dp |
| `TopTabs` | 状态条 + 顶部标签 + 内容 | ~78 dp |
| `Rail` | 左侧栏 88dp + (状态条 + 内容) | ~44 dp |

## 3. 各分辨率下的实际结果

| 设备 | 物理 | density | dp | 布局 | 内容区高度 |
|---|---|---|---|---|---|
| 车机（常见） | 800×480 | hdpi 240 | 533×320 | TopTabs | ≈242 dp |
| 车机（部分） | 800×480 | mdpi 160 | 800×480 | Rail | ≈436 dp |
| 车机（tvdpi） | 800×480 | 213 | 601×361 | TopTabs | ≈283 dp |
| 车机（高 dpi） | 800×480 | xhdpi 320 | 400×240 | TopTabs | ≈162 dp（滚动） |
| 手机竖屏 | 1080×2400 | xxhdpi 480 | 360×800 | BottomNav | ≈696 dp |
| 手机横屏 | 2400×1080 | 480 | 800×360 | TopTabs | ≈282 dp |

## 4. 内容级适配

外壳决定导航形态，屏幕内部还要再适配一次：

### 色彩页

* `wide = true`（Rail）：**左色盘 + 右控件**双栏。
  色盘边长取 `min(maxWidth, maxHeight)`，永远保持正圆且不溢出。
* `wide = false`：**上图下控件**单栏可滚动；色盘边长取 `min(maxWidth, 320.dp)`，
  避免横屏手机上出现"色盘占满整屏、控件要滚半天"。

### 模式页

* `wide = true`：左列滑块（速度 / 灯珠数 / 灵敏度），右列开关 + 流水 + 高级按钮。
* `wide = false`：单列滚动。
* `short = true`：底部额外留白，防止最后一排按钮贴边。

### 设备页

* 扫描结果最多 24 条，用**普通 Row 纵向排列**而不是 `LazyColumn`。
  原因：`LazyColumn` 放进 `verticalScroll` 会触发"无限高度约束"崩溃；
  24 条的数据量也不需要懒加载。
* 双栏时左"操作 + 状态"、右"扫描列表"，各自独立滚动。

## 5. 针对车载的额外处理

| 问题 | 处理 |
|---|---|
| 屏幕小、手指粗、车在动 | 滑块独占整行（≥40dp 高）；按钮最小 44–52dp；chip 高度 ≥44dp |
| 需要一眼看清状态 | 顶部常驻状态条：连接点 + 文字 + 重连/断开 |
| 开车时不能熄屏 | 连接成功后 `keepScreenOn = true`，断开自动取消 |
| 车机常横屏 | 不锁 `screenOrientation`，交给面板决定 |
| 旋转/换屏打断连接 | `configChanges` 声明 `orientation|screenSize|density`，Activity 不重建，BLE 连接不断 |
| 廉价面板对比度低 | 全深色主题 + 高对比强调色，禁用低对比灰字做关键信息 |
| 字体被系统放大 | 全部用 `sp`（`MaterialTheme.typography`），不写死 px |

## 6. 为什么不放 `res/values-sw600dp` 之类

传统做法要维护多套 dimens/布局 XML。本项目：

* 尺寸全部 `dp`，布局全部 Compose 运行时计算 → **一套代码覆盖所有屏幕**
* 图标是矢量 drawable → 无 `mipmap-*dpi` 多份位图
* 结果：**单个 universal APK**，不需要按密度/aabi 拆分，装到任何机器都是同一份包

## 7. 如何自测

1. Android Studio → **Device Manager** 建两个模拟器：
   * `Car 800x480`：New Hardware Profile → 800×480，density 240（最容易出问题的一档）
   * 普通手机 Pixel 6（411×914 dp）
2. 打开 **Layout Inspector**，或直接旋转/分屏观察三套外壳切换。
3. 也可以只靠改窗口大小验证：Android 12+ 的 **分屏 / 自由窗口** 拖到 400×300 左右，
   应自动切到 `TopTabs` 且内容可滚动、不重叠。
