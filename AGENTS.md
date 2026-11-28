# ZERO Project Engineering Rules & Handoff Guide (AGENTS.md)

## 0. Conversation Handoff Protocol (MANDATORY)
1. **New conversation start**: Before doing anything else, read the root `CONTEXT.md` first. Do NOT scan the whole project; read only the files listed in its "当前涉及的核心文件" / key-file sections, on demand. Ignore `build/`, `.gradle/`, `.kotlin/`, `.codex-attachments/`.
2. **Auto-refresh after every completed unit of work**: Whenever a feature, fix, or release is finished and committed, refresh `CONTEXT.md` in the same turn — update「已完成功能」「当前涉及的核心文件」「卡点 / 已知 Bug」「下一步计划」(and version/DB version if changed) — keep it concise, and commit it together with (or right after) the code change.
3. **"交接" keyword**: When the user says “交接” (or “handoff”), immediately update `CONTEXT.md` following its existing template to reflect all work in the current conversation (including uncommitted in-progress work and blockers), commit and push it, then confirm briefly.
4. Keep `AGENTS.md` and `CONTEXT.md` consistent (version numbers, DB version, key file paths).

## 1. Current Product Baseline (ZERO v2.16.0 — Source of Truth)
> **IMPORTANT**: `docs/PRD.md` reflects the initial v1.0 draft and is **outdated** on visual accents and layout. Always treat the current `v2.16.0` codebase, the rules below, and the root `CONTEXT.md` handoff guide as the authoritative source of truth.

- **Aesthetic & Palette (NO Sci-Fi Neon)**:
  - Keep the Matte Obsidian Dark (`#09090B`) / Cool Paper Light (`#F7F7F9`) bases, `0.5.dp` hairline borders, and tabular monospace numbers (`tnum`).
  - Use the 4 calm, Dark/Light-adaptive accents in `ZeroColors.kt`: `SlateBlue` (default), `Monochrome`, `Sage`, `WarmClay`. **Do NOT revert to `#FF4D00` / `#00E5FF` neon colors.** (Sole user-approved exception: the ATLAS execution theme's borderless burnt-orange `0xFFE54300` gradient glows in `AtlasThemeView.kt` — keep them seamless, feathered, and calmly "breathing", never hard-edged or bullet-like.)
  - System status bar and navigation bar icon contrast MUST dynamically follow `isDarkTheme` (`ZeroAppRoot.kt` via `WindowCompat.getInsetsController`).
- **Dual-State Core Architecture (`NOW` ⇄ `VAULT`)**:
  - Bottom `48.dp` tactile switcher toggles between `WorkspaceMode.NOW` (`feature/execute/ExecuteHostScreen.kt` hosting the swappable execution themes `MonolithThemeView.kt` / `RunwayThemeView.kt` / `AtlasThemeView.kt` — single active task card, 0.3s hold-to-complete, guilt-free skip, First Tiny Step -> Forward Momentum Stopwatch flow, Day Capacity Ruler) and `WorkspaceMode.VAULT` (infinite folder tree, calendar, matrix, tags, trash, global search, sort).
  - Full-screen `TriageDeckOverlay.kt` ("30s Triage") clears Inbox & Overdue backlog via single-card swipe/buttons.
- **Multi-Form-Factor Layout (`ZeroAdaptiveShell.kt`)**:
  - **Compact (<600dp)**: Single pane + left modal drawer (`ListDetailPaneScaffold`).
  - **Medium (600–840dp, unfolded foldable)**: **Smart Dynamic Full-Height Two-Pane** — when no task is selected: `[Full-Height Folder Tree | Full-Height Task List]`; when a task is selected: `[Full-Height Task List | Full-Height Task Detail]` + left modal drawer. **NEVER vertically stack the folder tree above the task list.**
  - **Expanded (>840dp, tablet)**: Persistent 3-pane layout + hardware keyboard shortcuts (`Ctrl/Cmd+N`, `Ctrl/Cmd+F`, `Ctrl/Cmd+,`, `Ctrl/Cmd+1..5`, `Esc`).
- **Touch Ergonomics & Anti-Misclick**:
  - Minimum interactive touch targets: **`44–54.dp`** on mobile/foldable controls. Do NOT cram tiny `22.dp` action icons into row ends; use overflow `···` / long-press action menus (`FolderActionMenuDialog`, `TagActionMenuDialog`).
  - Preserve strict anti-misclick mechanics (`AntiMisclickTaskCard.kt`, scroll lock, 0.3s hold / 30% damped swipe, 3s Undo capsule, folder/tag delete confirmation dialogs).

---

## 2. Mandatory 4-Language Localization Rule (EN / ZH / JA / KO)
ZERO ships with complete, native 4-language UI support:
- English (`app/src/main/res/values/strings.xml`) — default fallback
- Simplified Chinese (`app/src/main/res/values-zh/strings.xml`)
- Japanese (`app/src/main/res/values-ja/strings.xml`)
- Korean (`app/src/main/res/values-ko/strings.xml`)

Whenever adding or modifying ANY feature, screen, dialog, snackbar/capsule, widget, or formatter:
1. **NEVER hardcode user-facing copy** in Kotlin/Compose files or ViewModels. Always declare string keys in `strings.xml` and read them via `stringResource(R.string.*)` or `context.getString(R.string.*)`.
2. **ALWAYS add translations simultaneously to ALL 4 resource files** (`values`, `values-zh`, `values-ja`, `values-ko`) in the same commit/turn, preserving exact format placeholders (`%1$s`, `%1$d`, etc.).
3. Pure-Kotlin date/time or recurrence formatters (`core/common`) must support all 4 locales (`en`, `zh`, `ja`, `ko`) via an explicit `locale: Locale` parameter.
4. `LocalizationCompletenessTest` automatically fails `testDebugUnitTest` if any key or format placeholder is missing or mismatched across the 4 languages.

---

## 3. Local Build, Test & Database Migration Rules
- **JDK 17 & Android SDK on this Mac**:
  - Default `PATH` has no `java`; ALWAYS prefix Gradle commands with `JAVA_HOME=/opt/homebrew/opt/openjdk@17`:
    ```bash
    JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest assembleRelease
    ```
  - Android SDK is at `/Users/wushengyin/Library/Android/sdk` (`compileSdk = 35`, `minSdk = 26`).
- **In-Place Upgrade Signing**:
  - `app/build.gradle.kts` signs `release` builds with `signingConfigs.getByName("debug")` (using local `~/.android/debug.keystore`). **Do NOT change `signingConfig`** so installed APKs on the user's Android devices always upgrade in-place without losing data.
- **Room Database & Backup Compatibility**:
  - `ZeroDatabase` is currently at **`version = 5`** with `MIGRATION_1_2` … `MIGRATION_4_5` (latest schema: `app/schemas/com.zero.todo.data.local.ZeroDatabase/5.json`). `ZeroBackupManager.CURRENT_SCHEMA_VERSION = 4`.
  - Any schema change MUST increment `version`, add a lossless `Migration` registered in `DatabaseModule.kt`, export the new schema JSON, and update `ZeroBackupManager.kt` backward-compatibly (supporting all v1..v4+ JSON backups). **NEVER enable `fallbackToDestructiveMigration()`.**
- **Robolectric + Room Async Tests**:
  - Do NOT combine `runTest` virtual time with `withTimeout` when awaiting Room Flows (Room executes on real background threads and causes instant false timeouts). Follow `MainViewModelStateTest.kt`: use `runBlocking` + `Dispatchers.setMain(Dispatchers.Default)` + real-time `withTimeout`.

---

## 4. Release & Software Portal Publishing Workflow
- **App Repo**: `https://github.com/shynw1983/zero` (`gh` CLI is authenticated as `shynw1983`).
  1. Increment `versionCode` and `versionName` in `app/build.gradle.kts`.
  2. Run `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest assembleRelease`.
  3. Copy `app/build/outputs/apk/release/app-release.apk` to `/tmp/ZERO-v<VER>-release.apk`, compute `shasum -a 256`, push `main`, and publish via `gh release create v<VER> /tmp/ZERO-v<VER>-release.apk -R shynw1983/zero`.
- **App Labs Release Portal**: `https://github.com/shynw1983/app-hub` (auto-deploys to `https://app-hub-eight-flax.vercel.app`).
  - **CRITICAL macOS Pitfall**: Do NOT run `git push`/`git fetch` inside `/Users/wushengyin/Desktop/VC/release-portal` (iCloud Desktop causes `fatal: mmap failed`). Always clone cleanly to `/tmp/app-hub-push`, update `data/apps.json` (`latestVersion`, `fileSize`, `fileName`, `downloadUrl`, `sha256`, `downloads`, `releases`), commit, push, and `rm -rf /tmp/app-hub-push`.
- **Known Minor Cleanup**:
  - `TaskDetailPane.kt:1041`: compiler warning "Condition is always 'true'" (harmless; clean up on the next edit of that file).
- **Secrets**: NEVER commit `local.properties` or any `AIzaSy…` Maps API key (verify `git diff --cached | grep -E 'AIzaSy[0-9A-Za-z_-]{30,}'` prints nothing), and never stage `.codex-attachments/` or `.kotlin/`.
