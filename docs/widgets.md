# 桌面卡片（App Widget）

氛围灯提供 **5 种尺寸**的桌面卡片。**所有控件都在后台直连设备生效，不会唤起 App**；
只有点卡片空白处才打开 App。

![桌面卡片设计稿](widget-mockup.png)

## 1. 尺寸一览

| 卡片 | 占用格 | 最小尺寸 | 内容 |
|---|---|---|---|
| `SmallLampWidgetProvider` | 2×1 | 110×48 dp | 标题 + 状态 + 圆形开关 |
| `WideLampWidgetProvider` | 4×1 | 250×48 dp | 圆形开关 + 六色（一行） |
| `SquareLampWidgetProvider` | 2×2 | 110×110 dp | 标题 + 圆形开关 + 3×2 色块 |
| `TallLampWidgetProvider` | **2×4** | **110×250 dp** | **CarPlay 风格**：圆形开关 + 2×3 色块 + 分段色带 |
| `BigLampWidgetProvider` | 4×2 | 250×110 dp | 标题 + 圆形开关 + 六个大色块 + 打开按钮 |

设计要点（**CarPlay 取向**）：

- **深色磁贴**：近黑底（`#1E1826 → #0A0A0E` 极弱渐变）+ 22dp 大圆角 + 1dp 白色 12% 细边
  ——CarPlay 的颜色在**控件里**，不在背景上，所以不再用铺满的紫粉渐变
- 开关是**圆形按钮**：开 = iOS systemGreen `#30D158`，关 = systemGray3 `#3A3A3C`
- 色块等距、纯色 + 细白环，颜色取 iOS 系统色（见 §2）
- 文字用 iOS 灰阶：标题白、次要文字 `#8E8E93`（secondaryLabel）
- 2×4 竖卡额外带一条**分段色带**，去掉多余的「打开」按钮（点标题区域即可打开 App）

`resizeMode="horizontal|vertical"`，用户可自由拉伸；Android 12+ 有真实预览。

## 2. 三个控件

| 控件 | 行为 |
|---|---|
| **圆形开关** | 开/关灯（按下时取反当前已知状态），后台直连 |
| **固定色块 ×6** | 白 / 粉 / 红 / 紫 / 蓝 / 青，**直接下发字面 RGB** |
| **分段色带 ×6** | 红→黄→绿→青→蓝→品红（0°/60°/…/300°），后台直连 |

色块用的是 iOS 系统色，且**所见即所发**（不再按预设下标查表）：

| 色块 | RGB | 对应 iOS 色 |
|---|---|---|
| 白 | `255,255,255` | white |
| 粉 | `255,105,180` | —（品牌粉） |
| 红 | `255,59,48` | systemRed |
| 紫 | `191,90,242` | systemPurple |
| 蓝 | `10,132,255` | systemBlue |
| 青 | `100,210,255` | systemTeal |

这样卡片上看到的颜色和灯实际收到的颜色完全一致；改色只需动
`WidgetActions.SWATCHES` 与对应的 `res/drawable/widget_dot_*.xml`。

> **关于"滑动颜色"**：首页卡片**无法做真正的拖拽滑块** —— RemoteViews 只允许
> `ProgressBar`（只读），没有可交互的 `SeekBar`。所以这里做成**分段色带**：
> 视觉上是一条彩虹渐变条，点哪一段就切到那个色相。这是桌面上能做到的最接近"滑动选色"的形态。
> 需要连续拖拽调色时，点卡片空白处进 App 用色盘。

## 3. 交互模型（关键：不唤起 App）

```
点圆形开关   ──→ 广播 → 后台连 FF:FF:10:98:3C:D9 → 开/关
点固定色块   ──→ 广播 → 后台连接 → 若灯是关的则先开灯 → 下发该颜色
点分段色带   ──→ 广播 → 后台连接 → 同上，色相由色带段位决定
点卡片空白   ──→ 打开 App（并自动连接）
```

实现链路：

```
PendingIntent.getBroadcast
   └─ WidgetActionReceiver (goAsync)
        └─ LampQuickAction  → 组帧（LampCommand）
             └─ QuickBleWriter：connectGatt → discoverServices → write → disconnect
                  └─ WidgetState 落盘 + WidgetUpdater.refreshAll 重绘卡片
```

### 为什么不用前台服务

Android 12+ 限制后台启动前台服务，用服务还得配通知渠道、`POST_NOTIFICATIONS`、
`FOREGROUND_SERVICE_CONNECTED_DEVICE` 等一堆权限。

这里改用 **`goAsync()` + 一次性 GATT 会话**：

- 广播的异步窗口约 10 秒，`QuickBleWriter` 自己封顶 9 秒
- 直连已知 MAC 不需要扫描（后台受限的只有"扫描"，不是"连接"）
- 零新增权限、零常驻通知、零服务

代价：若设备不在范围内/被别的手机占用，连接会失败 —— 卡片会把状态文字改成
「连接失败，点这里打开 App」，让用户知道发生了什么事。

### 为什么 PendingIntent 能发到非导出组件

`exported="false"` 的 `WidgetActionReceiver` 依然能被卡片触发：`PendingIntent`
由本应用创建，**发送时以创建者的身份执行**，所以由桌面（另一个进程）发出也等同于本应用自发。

## 4. 状态同步

卡片自己不保持连接，它显示的是 **App/卡片动作记住的最后状态**：

- `WidgetState`（SharedPreferences）存 `power_on` 与 `last_error`
- 后台动作完成后写回并 `WidgetUpdater.refreshAll()` 重绘所有卡片
- App 收到设备状态帧或自己下发开关时也写回，所以卡片与真实状态不会长期脱节

## 5. 技术选型：RemoteViews 而非 Glance

Glance（Compose for widgets）要新增依赖，且 `background` 只接受纯色或 ImageProvider。
本项目用 **RemoteViews + shape drawable**：

- 零新依赖（这个项目本地没有编译器，只能靠 CI 验证，少一个依赖少一分风险）
- 深色磁贴、圆角、细边、圆形开关全部由 XML shape 精确表达
- 布局全部手写，尺寸精确到 dp

代价：只能用 `LinearLayout` / `ImageView` 这类老式视图，写不了 Compose。

## 6. 改配置

| 想改 | 改哪里 |
|---|---|
| 自动连接的蓝牙地址 | `gradle.properties` → `ambient.pinnedMac`（写进 `BuildConfig.PINNED_MAC`） |
| 六个固定颜色 | `widget/WidgetCore.kt` → `WidgetActions.SWATCHES` + `res/drawable/widget_dot_*.xml` |
| 色带色相 | `widget/WidgetCore.kt` → `WidgetActions.STRIP_HUES` + `res/drawable/widget_hue_*.xml` |
| 色块外观 | `res/drawable/widget_dot_*.xml` |
| 开关外观（开/关） | `res/drawable/widget_circle_on.xml` / `widget_circle_off.xml` |
| 卡片背景渐变 | `res/drawable/widget_bg.xml` |
| 卡片尺寸 | `res/xml/widget_*_info.xml` + `res/layout/widget_*.xml` |

## 7. 自测清单

1. 长按桌面 → 卡片 → 应看到 **5 个**「氛围灯…」条目，且选择器里有预览
2. **后台直连**（重点）：
   - 先把 App 从最近任务里划掉
   - 点卡片上的色块 → **不应**跳到 App，灯应直接变色
   - 点圆形开关 → 灯应直接开/关，卡片上的「开/关」跟着变
3. 点卡片空白处 → 应打开 App 并自动连接 `FF:FF:10:98:3C:D9`
4. 设备断电/远离后点色块 → 几秒后卡片状态变成「连接失败，点这里打开 App」
5. 把卡片拖到最小尺寸，确认不崩（内容裁切但不崩）

> 静态自检（无需设备）：`python tools/check_resources.py` 校验所有
> `R.id.*`、`@drawable/*`、`@string/*`、`@layout/*` 引用是否存在。
> 卡片布局是手写的，这条检查能挡住拼写错误。
