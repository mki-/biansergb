# 视觉风格：现代渐变风（Modern Gradient）

> 本项目的 UI 遵循 `modern-gradient` 风格规范。规范原文是 Tailwind/Web 语言，本文记录
> **逐条映射到 Jetpack Compose 的方式**，以及无法一比一实现时的替代方案。

## 1. 令牌映射表

| 规范（Tailwind） | Compose 实现 | 代码位置 |
|---|---|---|
| `bg-slate-950` | `Color(0xFF020617)` | `theme/Theme.kt` → `Slate950` |
| `bg-[#1e1b4b]` | `Color(0xFF1E1B4B)` | `InkIndigo` |
| 页面渐变 | `Brush.verticalGradient(#1e1b4b → #120f35 → slate-950)` | `LampGradients.background` |
| `from-violet-600 to-fuchsia-600` | `Brush.horizontalGradient(Violet600, Fuchsia600)` | `LampGradients.violetFuchsia` |
| `from-violet-500 via-purple-500 to-fuchsia-500` | 三色 `horizontalGradient` | `LampGradients.brand` |
| `from-violet-500 via-fuchsia-500 to-cyan-500` | 三色 `horizontalGradient` | `LampGradients.multi` |
| `from-cyan-500 to-blue-500` | 两色 `horizontalGradient` | `LampGradients.cyanBlue` |
| `from-pink-500 to-orange-500` | 两色 `horizontalGradient` | `LampGradients.pinkOrange` |
| `backdrop-blur-xl bg-white/10` | 半透明白渐变层（white 16%→6%）+ `border` white/20 | `components/Glass.kt` → `GlassCard` |
| `border-white/20 rounded-3xl` | `border(1.dp, white/20)` + `RoundedCornerShape(24.dp)` | `GlassCard` / `Shape3Xl` |
| `rounded-2xl` | `RoundedCornerShape(16.dp)` | `Shape2Xl` |
| `shadow-2xl shadow-violet-500/40` | `Modifier.shadow(12–18.dp, ambientColor = Violet500, spotColor = Fuchsia500)` | `GradientButton` / `PillChip` / `Swatch` |
| `blur-3xl opacity-30` 光晕 | `Canvas` + `Brush.radialGradient`（见 §3） | `GlowBackground` |
| `bg-clip-text text-transparent` | `TextStyle(brush = ...)`，用 `.copy(brush = LampGradients.brand)` | `AboutScreen` / `ModeScreen` |
| `text-white/70`、`text-white/80` | `Color.White.copy(alpha = 0.70f / 0.80f)` | `LampMuted` 等 |
| `hover:bg-right` 渐变位移 | 按下时叠加一条移动的高光带（`Gradient Flow`） | `GradientButton.drawBehind` |
| `hover:scale-[1.02]` / `active:scale-95` | `animateFloatAsState` + `graphicsLayer { scaleX/scaleY }` | `GradientButton` / `PillChip` |
| `transition-all duration-300` | `tween(180–500ms)` | 同上 |

## 2. 组件对照

| 规范要求 | 本项目组件 |
|---|---|
| 按钮：`rounded-2xl font-medium transition-all duration-300 text-white` + 渐变 | `GradientButton`（渐变 + 按下缩放 + 高光位移 + 彩色阴影） |
| 卡片：`backdrop-blur-xl bg-white/10 border border-white/20 rounded-3xl` | `GlassCard` |
| 可选中胶囊 | `PillChip`（未选=玻璃，选中=渐变 + 彩色光晕） |
| 页面容器 | `GlowBackground`（渐变 + 三个光晕球） |

## 3. 三处「规范无法一比一实现」的取舍

### 3.1 `backdrop-blur-xl`（背景模糊）

Compose **没有背景模糊**（`Modifier.blur` 模糊的是自身内容，且要 API 31+）。
替代：玻璃卡用「半透明白色渐变 + 白色描边」，下面的渐变背景自然透出来，视觉上等价。
代价是背景细节不会被模糊——但因为背景本身就是柔和渐变，肉眼看不出差别，还省了一次
`RenderEffect`，对车机的低端 GPU 更友好。

### 3.2 `blur-3xl` 光晕

用 `Canvas` 画 `Brush.radialGradient` 的圆（中心 30% 不透明 → 边缘全透明），
而不是真的高斯模糊。效果一致，成本是一个 draw call。

### 3.3 字体

规范要求避免 Inter / Roboto 等「过度使用」的字体。本项目使用系统默认字体（Roboto），
因为**离线环境无法引入自定义字体文件**。若要完全符合，把字体文件放进
`app/src/main/res/font/` 后用 `FontFamily(Font(R.font.xxx))` 接入，只需改 `Theme.kt`
的 `Typography`。

### 3.4 唯一一处 `Color.Black`：色块选中标记

规范禁止 `text-black` / `bg-white` 作为**配色方案**。色块的选中标记是例外：

```kotlin
val marker = if (luminance > 0.6f) Color.Black.copy(alpha = 0.85f) else Color.White
```

因为色块颜色是**用户数据**（可以是从纯白到纯黑之间的任意色），白色标记在「正白」色块上
会完全消失，黑色标记在深色块上同理。所以这里按底色亮度自适应取黑/白，是为了保证
选中态在任意底色上都能看见——服务于「不用颜色单独传递状态」这条无障碍要求。

## 4. 无障碍与降级

| 要求 | 实现 |
|---|---|
| `prefers-reduced-motion` 等价物 | `reducedMotion()` 读取 `ValueAnimator.areAnimatorsEnabled()`；关闭动画时用 `snap()` 代替 `tween()` |
| 正文对比度 WCAG AA | 正文 `white/70` 在 `#020617` 上约 9.6:1；`white/50` 约 5.5:1，仅用于弱化说明文字 |
| 不用颜色单独传递状态 | 连接状态同时有「色点 + 文字 + 断开/重连按钮」；选中色块除颜色外还有描边 + 中心点 |
| 触摸目标 | 按钮/胶囊 ≥ 48 dp；色块直径取可用空间最大值（车机约 56 dp） |

## 5. 自检（对照规范禁止项）

- [x] 主区域无纯色背景（`GlowBackground` 渐变 + 光晕）
- [x] 无单调灰色调（所有中性色都是 white + alpha，叠在彩色渐变上）
- [x] 无 `rounded-none`（最小圆角 12 dp，卡片 16/24 dp）
- [x] 无老式垂直渐变按钮（按钮统一水平渐变）
- [x] 渐变克制：只用于主按钮/选中态/标题/背景，色块本身是用户数据色
- [x] 深色渐变上没有深色文字（正文统一白色系）
- [x] 无卡片嵌套卡片（`GlassCard` 内部只有平板内容）
- [x] 无单侧粗边框装饰
- [x] 无 tiny uppercase tracked eyebrow
- [x] 无 bounce / elastic 缓动（只用 `tween`）

> 规范内部有一处自相矛盾：`必须遵守`要求使用渐变文字（`bg-clip-text`），
> 而`禁止项`里又列了「没有渐变文字」。本项目取**克制使用**：只在 About 页标题和
> 少数分组标题上用渐变文字，不铺满全站。

## 6. 如何快速自检风格

1. 全局搜索禁止项：`Select-String -Path app\src\**\*.kt -Pattern 'bg-white|Color\.Black|rounded-none'`
2. 确认每个页面都有：渐变背景（`GlowBackground`）、至少一个 `GlassCard`、主操作是 `GradientButton`
3. 真机上看：深色渐变上文字是否清楚、光晕是否过曝、选中态是否一眼可辨
