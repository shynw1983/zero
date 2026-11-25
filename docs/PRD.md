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