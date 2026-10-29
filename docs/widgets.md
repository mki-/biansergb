# 桌面卡片（App Widget）

氛围灯提供 **5 种尺寸**的桌面卡片，都带开关和六个颜色按钮，**任意点击都会自动连接
`FF:FF:10:98:3C:D9`**。

![桌面卡片设计稿](widget-mockup.png)

## 1. 尺寸一览

| 卡片 | 占用格 | 最小尺寸 | 内容 |
|---|---|---|---|
| `SmallLampWidgetProvider` | 2×1 | 110×40 dp | 标题 + 状态 + 开关 |
| `WideLampWidgetProvider` | 4×1 | 250×40 dp | 开关 + 六色（一行） |
| `SquareLampWidgetProvider` | 2×2 | 110×110 dp | 标题 + 开关 + 3×2 色块 |
| `TallLampWidgetProvider` | **2×4** | **110×250 dp** | **竖版**：标题 + 开关 + 2×3 色块 + 打开 |
| `BigLampWidgetProvider` | 4×2 | 250×110 dp | 标题 + 开关 + 六个大色块 + 打开按钮 |

竖卡（2×4）的色块按 **2 列 × 3 行**排列，适合桌面上的窄长空位；最小可缩到 110×180 dp。

- 支持 `resizeMode="horizontal|vertical"`，用户可自由拉宽拉高
- Android 12+ 有 `targetCellWidth/Height` 和 `previewLayout`，选择器里显示真实预览
- `updatePeriodMillis=1800000`（30 分钟）只做兜底刷新；正常靠 App 主动推

## 2. 六个颜色

对应主界面的预设（`ui/PresetColors` 的下标）：

| 卡片按钮 | 预设 | RGB |
|---|---|---|
| 白色 | 正白（idx 2） | `255,255,255` |
| 粉色 | 桃粉（idx 13） | `255,105,180` |
| 红色 | 大红（idx 9） | `255,0,0` |
| 紫色 | 紫罗兰（idx 16） | `138,43,226` |
| 蓝色 | 正蓝（idx 20） | `0,0,255` |
| 青色 | 青色（idx 24） | `0,255,255` |

映射表在 `widget/WidgetCore.kt` 的 `WidgetActions.PALETTE`，改这里即可换颜色；
色块外观在 `res/drawable/widget_dot_*.xml`。

## 3. 交互模型

```
点卡片空白处  ──→ 打开 App（不改变灯）
点开关        ──→ 打开 App + 开/关灯（取反当前已知状态）
点某个色块    ──→ 打开 App + 切到该颜色
```

三条路径都走同一个入口：`WidgetIntents` 构造带 extra 的 `PendingIntent` →
`MainActivity` → `LampViewModel.onWidgetAction()`。

`onWidgetAction` 的流程（关键设计）：

```
卡片被点
   ├─ 已连接？ → 立即下发命令
   └─ 未连接？ → 记住待执行动作
                → 连接 PinnedLamp.mac
                → 等 LinkState.Ready
                → 自动补齐：先切颜色 / 再开关
```

所以**冷启动也只需点一下**：从桌面直接点「蓝色」，App 会自己连上并把灯变蓝。

## 4. 两处刻意的技术选择

### 4.1 用 RemoteViews 而不是 Glance

Glance（Compose for widgets）要新增一个依赖和一套新的声明式 API，而且它的
`background` 只接受纯色或 ImageProvider，做不了渐变。本项目用
**RemoteViews + shape drawable**：

- 零新依赖，构建风险最低（这个项目本地没有编译器，只能靠 CI 验证）
- 渐变直接用 `<gradient>` 写在 XML 里，和主界面的 violet→fuchsia 完全一致
- 4 个布局文件全部手写，尺寸和间距可精确控制到 dp

代价是布局要用 `LinearLayout`/`ImageView` 这类老式视图，不能写 Compose。

### 4.2 用 Activity 而不是前台服务

真正的「点了就执行、不打开 App」需要 `BroadcastReceiver` + 前台服务
（Android 14 还要 `FOREGROUND_SERVICE_CONNECTED_DEVICE` 权限 + 常驻通知）。

本项目选择 **PendingIntent 直达 MainActivity**：

- 不需要前台服务、不需要通知权限，权限面最小
- BLE 连接的唯一所有者仍是 Activity/ViewModel，不存在两个进程抢 GATT
- 代价：点击会切到 App 界面（车机上反而更好——用户能看到连接状态）

如果以后要做「静默执行」，改造点很明确：新增一个 Service，把
`LampViewModel.onWidgetAction` 的「连接→等 Ready→下发」逻辑搬进去复用即可。

## 5. 状态的同步

卡片自己不碰 BLE，它显示的是 **App 记住的最后状态**：

- `WidgetState`（SharedPreferences）存 `power_on`
- App 收到设备状态帧、或自己下发了开关命令时，写回该值并调用
  `WidgetUpdater.refreshAll(context)` 重绘所有卡片
- 因此卡片上的「开/关」不会和灯的真实状态长期脱节

## 6. 改配置

| 想改 | 改哪里 |
|---|---|
| 自动连接的蓝牙地址 | `gradle.properties` → `ambient.pinnedMac`（会写进 `BuildConfig.PINNED_MAC`） |
| 卡片上的六个颜色 | `widget/WidgetCore.kt` → `WidgetActions.PALETTE` |
| 色块外观 | `res/drawable/widget_dot_*.xml` |
| 卡片背景渐变 | `res/drawable/widget_bg.xml` |
| 卡片尺寸 | `res/xml/widget_*_info.xml` + 对应的 `res/layout/widget_*.xml` |

## 7. 自测清单

1. 长按桌面 → 卡片 → 应看到 **4 个**「氛围灯…」条目，且选择器里有预览
2. 逐个添加，确认：
   - 渐变背景、圆角、文字清晰
   - 开关按钮点击后 App 被拉起并连上 `FF:FF:10:98:3C:D9`
   - 六个色块点击后灯变对应颜色
3. 在 App 里切开关，回到桌面确认卡片上的「开/关」跟着变
4. 把一个卡片拖到最小尺寸，确认不崩、不重叠（内容会被裁切但不崩）

> 静态自检（不需要设备）：`python tools/check_resources.py` 会校验所有
> `R.id.*`、`@drawable/*`、`@string/*` 引用是否存在——卡片布局是手写的，
> 这条检查能挡住拼写错误。
