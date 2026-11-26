# ZERO — Android Todo App Product & Design Specification

## 1. Core Philosophy
Target Users: Individuals with both OCD (need strict visual order & deep hierarchy) and Procrastination (need zero-friction capture).
Core Principle: **Decouple Capture from Organization.**
- Capture Mode: < 3 seconds to dump a thought into Inbox. Zero mandatory fields.
- Organize Mode: Deep nested folders, precise alignment, drag-and-drop, zero red-dot anxiety.

## 2. Design System: Near-Future Minimalism
Inspired by Linear, Nothing OS, and Dieter Rams. No heavy drop shadows, no gradients, no decorative clutter. Hierarchy is strictly defined by surface contrast and `0.5.dp` hairline borders.

### 2.1 Color Tokens
- **Dark Mode (Default)**:
  - Background: `#09090B` (Matte Obsidian)
  - Surface / Card: `#141417` (Carbon)
  - Surface Elevated / Modal: `#1C1C21`
  - Hairline Border: `#27272A` (`0.5.dp`)
  - Primary Text: `#F4F4F5`
  - Secondary Text: `#A1A1AA`
  - Muted / Disabled: `#52525B`
- **Light Mode**:
  - Background: `#F7F7F9` (Cool Paper)
  - Surface / Card: `#FFFFFF`
  - Hairline Border: `#E4E4E7` (`0.5.dp`)
  - Primary Text: `#09090B`
  - Secondary Text: `#71717A`
- **Accent Color (Single Functional Accent)**:
  - Signal Orange: `#FF4D00` (Default, user-switchable to Cyber Cyan `#00E5FF`)
  - Strict Usage: Active focus state, Pomodoro progress, P1 priority indicator, active input cursor.

### 2.2 Typography
- UI / Body: System Sans / Noto Sans SC (clean weight contrast: Medium 500 for titles, Regular 400 for body).
- **Tabular Numbers (Mandatory)**: All timestamps, dates, counters, and timers MUST use `JetBrains Mono` (or system monospace) with `fontFeatureSettings = "tnum"` to guarantee pixel-perfect vertical alignment.

### 2.3 Motion & Haptics
- Use Spring physics (`Spring.DampingRatioNoBouncy`, `Spring.StiffnessMediumLow`).
- Tactile feedback via `LocalHapticFeedback` / Android `VibrationEffect` (crisp mechanical click on task completion, subtle tick on drag reorder).

## 3. Core Feature Requirements (TickTick Parity + Enhancements)

### 3.1 Infinite Nested Folder Tree
- Structure: `Root -> Folder -> Sub-Folder -> ... -> Tasks` (Unlimited depth in DB; UI renders clean indentation up to 4 visible levels with horizontal scroll or breadcrumb drill-down beyond level 4).
- Features: Collapse/expand nodes, drag-and-drop reordering, drag tasks directly into folders, toggle between "Current folder only" and "Include all sub-folders".

### 3.2 Anti-Procrastination: Command-First Quick Input
- Persistent bottom capsule input bar positioned in the thumb zone (with safe bottom padding above Android gesture bar).
- **Local NLP Parser (Real-time token highlighting)**:
  - Date/Time: `今天`, `明天`, `后天`, `周五`, `下周一`, `15:00`, `下午3点`, `2026-12-01`
  - Folder routing: `/工作`, `/财务/报销` (auto-complete dropdown on typing `/`)
  - Priority: `!1` (Urgent/Orange), `!2` (High), `!3` (Medium), `!4` (None)
  - Tags: `#标签名`
- **Continuous Entry Mode**: Pressing Enter/Done saves the task and keeps the keyboard open with a cleared input field.
- **Voice-to-Task**: Long-press the bottom capsule to trigger Android SpeechRecognizer and parse directly into a structured task.

### 3.3 Anti-Misclick Mechanics (Critical)
- **No Fragile Single-Tap Disappear**:
  - Default mode: Checkbox requires either a **0.3s short press with radial fill animation** OR **Swipe-to-Complete (30% horizontal drag threshold with spring resistance + crisp haptic snap)**.
  - Scroll Lock: Disable checkbox tap listeners while `LazyColumn.isScrollInProgress == true`.
- **Hitbox Isolation**:
  - Left 48.dp: Complete action zone.
  - Center: Open task detail pane/sheet.
  - Right 40.dp: Dedicated drag handle for reordering.
- **3-Second Undo Capsule**: Completed tasks do not vanish abruptly; show a floating bottom "Undo" capsule for 3 seconds.

### 3.4 Smart Views & Focus Tools
- Smart Views: Inbox (收集箱), Today (今天), Tomorrow (明天), Next 7 Days (最近7天), Calendar View (Month/Week/Day).
- **Single-Task Focus Mode**: Full-screen distraction-free view showing ONLY 1 current task.
- **Pomodoro + "2-Minute Starter"**: Standard 25/5 Pomodoro timer + a dedicated **"Just 2 Mins" (先做2分钟)** button to overcome initiation paralysis, seamlessly transitioning into full focus if the user continues.

## 4. Adaptive Multi-Form Factor Layout (Material 3 Adaptive)
Must preserve input state across fold/unfold configuration changes.
- **Compact (< 600.dp, Slab Phones)**: Single pane. Left modal navigation drawer for Folder Tree. Bottom persistent quick-input capsule.
- **Medium (600.dp - 840.dp, Unfolded Foldables)**: 2-Pane Split (`ListDetailPaneScaffold`). Left pane: Collapsible Folder Tree + Task List. Right pane: Task Detail / Markdown Notes / Focus Timer. Support cross-pane drag-and-drop.
- **Expanded (> 840.dp, Tablets)**: 3-Pane Desktop-class Layout. Left: Persistent Folder Tree (`240.dp`–`280.dp`). Center: Task List / Calendar. Right: Task Detail & Subtasks. Full hardware keyboard shortcut support (`Cmd/Ctrl + N` new task, `Esc` close).

## 5. Execution Theme Engine (`実行 / NOW` 执行主题引擎)
To create unmistakable experiential contrast between ZERO's **VAULT** organization studio and **Execute (`実行 / NOW`)** focus stage, the Execute home screen is architected as an extensible multi-theme engine rather than a single fixed card layout.

### 5.1 Decoupled Architecture & State Sharing
- **Theme Enum**: `enum class ExecuteTheme { MONOLITH, RUNWAY }` persisted in `SharedPreferences` (`KEY_EXECUTE_THEME = "execute_theme"`), designed to accommodate additional execution themes cleanly.
- **Single Shared State Contract**: Every Execute theme consumes the exact same `ExecuteViewModel` interface and `ExecuteUiState` (`executeTheme`, `runwayQueue`, `focusedTask`, `focusedTaskIndex`, `scheduledTodayMinutes`, `dailyCapacityMinutes`, `focusState` / 2-minute starter countdown, `quickInputText` & NLP tokens, `undoBanner`). Switching themes MUST preserve 100% of active focus timers, queue selection, and draft input without loss.
- **Instant Minimalist Theme Picker**: A compact top-right theme switcher icon opens a minimalist `ModalBottomSheet` with swatch previews and concise descriptions; selecting a theme triggers an immediate smooth `Crossfade` transition.

### 5.2 Theme 1: `MONOLITH` (极致留白 · 呼吸感单任务场)
- **Visuals**: Pure `#09090B` matte obsidian canvas. Zero card borders, zero inline property chips, zero form inputs, and zero bottom action buttons in the center stage. Only the active task's large high-contrast title floats center-screen over a slow-breathing `#FF4D00` Signal Orange radial aura whose radius/luminance scales subtly with effort weight (`5m` / `25m` / `1h`).
- **Full-Gesture Interactions**:
  - **Long-Press Center (`~320ms`)**: Radial aura expands with progressive linear-motor haptic ticks and directly starts the **"Just 2 Mins" (2分钟起步)** countdown, rendered in `tnum` monospace digits beneath the title.
  - **Horizontal Swipe Left / Right**: Smooth GPU glide switches cleanly to the previous or next task on the runway.
  - **Tap Task Title**: Opens a minimalist `ModalBottomSheet` to adjust effort weight (`5m` / `25m` / `1h`), edit/complete **"First Tiny Step (最初の一歩)"**, Hold-to-Complete, or defer (`後回し`).
- **Bottom Chrome**: Retains the quick-input capsule and bottom `NOW / VAULT` switcher softly dimmed to minimize visual noise.

### 5.3 Theme 2: `RUNWAY` (实体跑道 · 能量时间轴)
- **Visuals**: Replaces uniform cards with a vertical physical execution runway:
  - **Left Precision Time Ruler (`tnum`)**: Cumulative timeline ticks (`0m`, `30m`, `1h`, `2h`...).
  - **Right Proportional Geometric Blocks**: Each task's vertical block height scales proportionally with its effort tier (`5m` slender bar, `25m` medium block, `1h` tall monolith block) with crisp `0.5.dp` hairline borders.
  - **Center Fixed NOW Reticle**: A high-precision `#FF4D00` targeting frame locked vertically at the center of the runway.
  - **Energy Limit Warning Rail (`4h 限界`)**: Across the cumulative slot where allocated duration meets `dailyCapacityMinutes` (default `240m` / `4h`), draw a full-width Signal Orange/Red boundary line; overflow tasks below it are visually dimmed.
- **Snap-Fling & Active Reticle Actions**:
  - Uses `rememberSnapFlingBehavior` (`SnapPosition.Center`) so vertical flings snap each task block cleanly into the central **NOW Reticle** with mechanical tick haptics.
  - **ONLY** the task snapped inside the **NOW Reticle** expands inline quick actions (`2分だけ` / `後回し` / `長押し完了`); all non-focused blocks display only title and tabular duration.

### 5.4 Multi-Form-Factor Constraints
- Both `MONOLITH` and `RUNWAY` enforce a centered content width cap (`maxWidth = 600.dp`) across slab phones, unfolded foldables, and tablets so typography, aura geometry, and time-ruler alignment remain balanced without horizontal over-stretching.
