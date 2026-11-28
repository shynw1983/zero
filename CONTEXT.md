# 项目交接文档 (CONTEXT.md)

> **给 AI 的强制指令**：
> 1. **禁止全盘扫描项目**。先阅读本文件，仅根据下方列出的“当前涉及文件”按需读取代码。
> 2. 忽略 `build/`、`app/build/`、`.gradle/`、`.kotlin/`、`.codex-attachments/` 等构建、缓存与附件目录。
> 3. 修改代码前，先确认是否违反下方的“核心约束”（另见根目录 `AGENTS.md`，两者已同步至 v2.18.1）。

---

## 1. 项目概况与技术栈
* **项目名称**：ZERO（包名 `com.zero.todo`）
* **当前发布版本**：`v2.18.1`（`versionCode = 53`）。发布提交使用 `git rev-parse 'v2.18.1^{commit}'` 查询，当前分支 HEAD 使用 `git rev-parse --short HEAD` 查询。
* **核心定位**：为“强迫症 × 拖延症”打造的原生 Android 双态待办仪器 —— `NOW`（执行：单卡跑道/多执行主题）⇄ `VAULT`（整理库：无限目录树、日历、矩阵、标签、回收站、搜索），外加 30 秒闪电清仓、地点提醒、AI 智能提取/拆解。
* **技术栈**：Kotlin + Jetpack Compose + Material 3 Adaptive、Hilt、Room（KSP）、Google Maps SDK 与 Mapbox Maps SDK（ATLAS 主题）、Google Play Services Geofencing、Robolectric 单元测试。`compileSdk = 35`，`minSdk = 26`。
* **运行/构建命令**（本机默认 PATH 无 java，必须加前缀）：
  ```bash
  JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest assembleRelease \
    --no-daemon --max-workers=4 -Dorg.gradle.jvmargs="-Xmx4096m -XX:MaxMetaspaceSize=1024m"
  ```
  * 完整构建约 9 分钟；用 `--no-daemon` + 4G 堆可避免 R8 阶段 GC 卡死。
  * 测试结果汇总：解析 `app/build/test-results/testDebugUnitTest/TEST-*.xml`（v2.18.1 基线：`TESTS=134 ERRORS=0 FAILURES=0 SKIPPED=0`）。
  * Android SDK：`/Users/wushengyin/Library/Android/sdk`。

## 2. 核心目录与关键文件（无需扫盘，直接定位）
根路径：`app/src/main/java/com/zero/todo/`
* **入口 / 外壳**：
  * `feature/main/ZeroAppRoot.kt`（根 Composable、主题、状态栏图标明暗、回调汇总 `ZeroShellCallbacks`）
  * `feature/main/ZeroAdaptiveShell.kt`（直板/折叠屏/平板三档自适应布局、快捷键）
  * `feature/main/MainViewModel.kt`（全局状态与几乎所有业务操作入口，体量很大，请按函数名定位读取）
* **数据模型 / 状态管理**：
  * `domain/model/TaskItem.kt`（`TaskItem`、`TagItem`、`TagPlace`、`PlaceTriggerMode`、`FocusMode`）
  * `data/local/ZeroDatabase.kt`（Room **`version = 6`**，`MIGRATION_1_2`..`MIGRATION_5_6`）
  * `data/local/DatabaseModule.kt`（Hilt 注册迁移）、`data/local/entity/`（实体）、`data/repository/`（`TaskRepository(Impl)`、`EntityMappers.kt`）
  * `data/backup/ZeroBackupManager.kt`（JSON 备份 `CURRENT_SCHEMA_VERSION = 5`，支持恢复 v1–v5）
  * `data/ai/`（AI 提供方调用）、`data/preferences/`（设置存储）
* **核心 UI / 页面**：
  * 执行页：`feature/execute/ExecuteHostScreen.kt`（主题切换宿主）、`MonolithThemeView.kt`、`RunwayThemeView.kt`、`AtlasThemeView.kt`（3D 地图主题，含 GroundOverlay 高斯渐变光晕 + 60fps `AtlasSpatialMotionOverlay`）、`AtlasMapStyles.kt`、`AtlasQuestBuilder.kt`
  * 整理库：`feature/tasklist/`（`TaskListPane`、`QuickInputCapsule`、`AntiMisclickTaskCard`、`UndoCapsule`）、`feature/folder/`（`FolderTreePanel`、`TagPlaceConfigDialog` 地点标签配置）、`feature/taskdetail/TaskDetailPane.kt`、`feature/calendar/`、`feature/matrix/`、`feature/trash/`
  * 其他：`feature/triage/TriageDeckOverlay.kt`（30s 清仓）、`feature/ai/AiAssistantDialogs.kt`（AI 智能提取/拆解）、`feature/focus/`、`feature/settings/SettingsOverlay.kt`、`widget/`（桌面小组件）
  * 设计系统：`core/designsystem/`（`ZeroColors.kt` 等）
  * 地理围栏：`core/geofence/GeofenceSyncController.kt`（`GeofenceEvaluator` + `AndroidGeofenceSyncController`）、`core/geofence/ZeroGeofenceReceiver.kt`
  * 本地 NLP 快速输入解析：`core/nlp/`；日期/重复格式化：`core/common/`
* **配置文件**：
  * `app/build.gradle.kts`（版本号、签名、`MAPS_API_KEY` 从 `local.properties` 注入 `manifestPlaceholders`）
  * `app/schemas/com.zero.todo.data.local.ZeroDatabase/*.json`（Room 导出 schema，当前最新 `6.json`）
  * 资源：`app/src/main/res/values{,-zh,-ja,-ko}/strings.xml`
* **测试**：`app/src/test/java/com/zero/todo/`（含 `LocalizationCompletenessTest`、`MainViewModelStateTest`、`core/geofence/PlaceGeofenceTest.kt`）

## 3. 已完成功能（默认不要改动）
* [x] NOW ⇄ VAULT 双态架构 + 底部 48dp 切换器；直板/折叠屏双栏/平板三栏自适应 + 硬件快捷键
* [x] 无限级目录树、标签、日历、四象限矩阵、回收站、全局搜索与排序；30 秒清仓 Triage
* [x] 防误触机制：0.3s 长按完成、30% 阻尼滑动、3s 撤销胶囊、删除确认弹窗
* [x] 执行主题引擎：MONOLITH / RUNWAY / ATLAS（3D 地图，焦橙 `0xFFE54300` 无边界高斯渐变光晕、连续流体光带、正弦呼吸动效）
* [x] 地点标签（多地点、半径、名称可编辑）+ 后台地理围栏提醒；**v2.16.0**：标签默认时机 + 单任务覆盖（`TaskItem.placeTriggerOverride`：`null`=跟随标签 / `ENTER` / `EXIT` / `BOTH`），围栏按并集注册、触发时按任务过滤
* [x] AI 智能提取（相机/图片/语音/文本）与 AI 拆解；**v2.16.0**：添加前可编辑（预览卡内联改标题 + `AiDraftTextEditDialog` 编辑备注/标签/子任务；拆解项第一步/子任务/清单可内联编辑）；语音快速输入改为回填输入框并聚焦，不再直接提交
* [x] 4 语言（EN/ZH/JA/KO）完整本地化；WebDAV/JSON 备份；动态主题桌面图标与小组件
* [x] 发布流水线：GitHub Release + App Labs 门户（`shynw1983/app-hub` → https://app-hub-eight-flax.vercel.app）
* [x] **v2.16.1**：本地导入和 WebDAV 拉取在写入前校验备份结构，兼容 v1–v4；ATLAS 地点任务卡连续按住 300ms 完成一次，提前松手或取消会清零进度。
* [x] **v2.17.0 修复**：AI 提取和拆解按当前会话处理结果，忽略过期回复；移动标签后刷新围栏注册；本地 JSON 和 WebDAV 上传从同一个 Room 事务读取六张表的快照。
* [x] **v2.17.0 改进**：筛选结果长按打开项目或主任务；围栏半径最高 3 km；任务负荷增加 2 小时；同级标签手动排序并写入备份；已保存地点显示完整名称和地址；循环任务跳过本次可撤销；顶层任务在子任务后短右滑时自动对齐层级。
* [x] **v2.18.0**：VAULT 侧栏的文件夹和标签共用拖动手势。拖右侧抓手到行上下边缘可插入对应层级，拖到行中间移为子项，拖到同分区标题移到顶层首位。子树随行移动，折叠目标自动展开，长列表靠边自动滚动。
* [x] **v2.18.1**：文件夹与标签统一用抓手排序和换层级，移除长按菜单中的上移、下移和更换父级入口。抓起和切换有效落点时轻振，遵循全局振动开关。
* [x] **ATLAS 启动镜头**：创建地图时直接指定初始镜头。有定位权限时最多等 2.5 秒，优先使用当前位置，之后才回退到首个地点任务或东京总览。迟到的定位直接居中，用户拖动地图或手动选点后保留其视角。
* [x] **ATLAS 地图设置**：执行主题选择中可选 Google 地图或 Mapbox，Mapbox 提供标准与卫星样式。首次启用需在应用内填写 `pk.` 开头的公开令牌，地图选项保存在本机。两种地图共用任务、路线、定位和日夜切换入口。

## 4. 当前进度与待解决问题
* **当前进度**：v2.18.1 已发布到 GitHub Release 和 App Labs。134 个测试通过，Release APK 构建成功。APK 签名与 v2.18.0 一致，公开下载内容和门户 SHA-256 已核对。
* **ATLAS 验证**：本地 `testDebugUnitTest` 共 141 项通过，含 4 项启动镜头与 3 项地图偏好回归测试。`assembleDebug`、APK 签名验证及 16 KB ZIP 对齐检查通过。
* **发布记录**：应用标签 `v2.18.1` 指向 `0bfdacb`，App Labs 生产部署来自 `shynw1983/app-hub` 的 `bc34552`。[GitHub Release](https://github.com/shynw1983/zero/releases/tag/v2.18.1) · [App Labs](https://app-hub-eight-flax.vercel.app)
* **当前涉及的核心文件**：
  * `feature/execute/AtlasMapSettingsSheet.kt`、`ExecuteHostScreen.kt`（主题选择入口）；`domain/model/AtlasMapSettings.kt`、`data/preferences/UserPreferencesRepository.kt`（地图偏好）；`feature/execute/AtlasMapboxSurface.kt`、`AtlasMapCamera.kt`（Mapbox 原生地图和共用镜头）。
  * `feature/execute/AtlasThemeView.kt`、`AtlasStartupCamera.kt`（ATLAS 首帧与迟到定位）；回归测试 `feature/execute/AtlasStartupCameraTest.kt`。
  * `feature/folder/FolderTreePanel.kt`、`SidebarTreeDrag.kt`、`SidebarTreeDropTargets.kt`（侧栏抓手、预览与落点判定）；触感复用 `core/designsystem/ZeroHaptics.kt`
  * `feature/folder/FolderDialogs.kt`、`feature/main/ZeroAdaptiveShell.kt`（文件夹和标签长按菜单）
  * `data/repository/FolderRepositoryImpl.kt`、`TagRepositoryImpl.kt`、`data/local/dao/TagDao.kt`（事务内排序与换层级）
  * 拖动回归测试：`feature/folder/SidebarTreeGestureTest.kt`、`SidebarTreeDropTargetsTest.kt`、`data/repository/FolderRepositoryTest.kt`、`TaskAndTagRepositoryTest.kt`
  * `data/backup/ZeroBackupManager.kt`（事务内读取备份快照、恢复前按版本校验）
  * `feature/main/MainViewModel.kt`、`ZeroAdaptiveShell.kt`、`ZeroAppRoot.kt`（AI 会话与回调绑定）
  * `core/geofence/GeofenceSyncController.kt`（串行刷新围栏注册；移动标签入口在 `MainViewModel.kt`）
  * `feature/tasklist/TaskListHierarchy.kt`、`feature/main/ZeroAdaptiveShell.kt`（右滑层级、筛选任务菜单和撤销入口）
  * `feature/folder/TagPlaceConfigDialog.kt`、`feature/taskdetail/TaskDetailPane.kt`（完整地点与 3 km、2 小时负荷）
  * `data/local/dao/TagDao.kt`、`domain/model/TagTreeBuilder.kt`（同级标签排序）、`data/repository/TaskRepositoryImpl.kt`（循环跳过与事务撤销）
  * `feature/execute/AtlasThemeView.kt`（地点任务卡 300ms 长按与取消）
  * 回归测试：`data/backup/ZeroBackupManagerTest.kt`、`feature/main/MainViewModelStateTest.kt`、`feature/execute/AtlasHoldGestureTest.kt`（位于 `app/src/test/java/com/zero/todo/`）。发布时已修复 `MainViewModelStateTest.kt` 的退出竞态，关闭 Room 前等待 ViewModel 协程结束。
* **v2.16.0 发布涉及的核心文件**：
  * `core/geofence/GeofenceSyncController.kt`（`effectiveTriggerMode` / `shouldTriggerTask` / `aggregateModeForGeofence`）
  * `core/geofence/ZeroGeofenceReceiver.kt`（触发时按任务过滤 + 冷却）
  * `feature/taskdetail/TaskDetailPane.kt`（地点触发时机 4 段选择器）
  * `feature/folder/TagPlaceConfigDialog.kt`（地点标签配置弹窗）
  * `feature/execute/AtlasThemeView.kt`（ATLAS 光晕与动效）
  * `feature/ai/AiAssistantDialogs.kt`（AI 提取/拆解及预编辑）
  * `feature/tasklist/QuickInputCapsule.kt`（快速输入、语音识别回填）
* **卡点 / 已知问题**：
  * Mapbox 在线底图待用有效公开令牌在折叠屏验收。检查标准和卫星样式、任务点点击、路线、底部 Mapbox 标识与版权按钮。
  * ATLAS 启动镜头待折叠屏真机验收，覆盖定位快速返回、定位晚到、未授权和手动拖图四种情况。
  * 文件夹与标签拖动待折叠屏真机验收。重点检查插入线、目标高亮、靠边滚动，以及抓起和切换有效落点时的轻振是否随全局开关启停。
  * 用户真机为折叠屏（日语系统界面），ATLAS 页需注意快速输入胶囊、Google 水印与任务卡片的遮挡问题（v2.14.2 已修过一次，改动该区域时需回归检查）。

## 5. 核心约束（绝对不能忘的规矩）
* **UI / 设计规范**：
  * 哑光黑 `#09090B` / 冷纸白 `#F7F7F9` 底色、`0.5.dp` 细线、等宽数字 `tnum`；常规主题强调色仅限 `ZeroColors.kt` 中 `SlateBlue`/`Monochrome`/`Sage`/`WarmClay`，**禁止回退到 `#FF4D00` / `#00E5FF` 霓虹色**（ATLAS 主题的焦橙光晕是用户明确要求的例外）。
  * 动效要“连贯、有呼吸感、不浮夸”：光晕必须无边界、边缘羽化、连续渐变无断层，不要颗粒“子弹”式流光或生硬闪烁。
  * 触控目标 `44–54.dp`；行尾不要塞小图标，用 `···` / 长按菜单；中等宽度（600–840dp）**绝不**把目录树竖向堆叠在任务列表上方。
* **代码规范**：
  * **禁止硬编码用户可见文案**，所有文案必须同时加入 `values` / `values-zh` / `values-ja` / `values-ko` 四个 `strings.xml`，占位符一致（`LocalizationCompletenessTest` 会卡住）。
  * 保持现有目录结构，不要大面积重构未提及的文件；`MainViewModel.kt` 很大，按需定位读取。
  * Robolectric + Room 异步测试：不要把 `runTest` 虚拟时间和 `withTimeout` 混用，参照 `MainViewModelStateTest.kt` 用 `runBlocking` + `Dispatchers.setMain(Dispatchers.Default)`。
* **其他雷区**：
  * **不要修改** `app/build.gradle.kts` 中 release 的 `signingConfig = signingConfigs.getByName("debug")`（保证用户设备原地升级不丢数据）。
  * 数据库变更必须：`version` +1、新增无损 `Migration` 并在 `DatabaseModule.kt` 注册、导出新 schema json、`ZeroBackupManager.kt` 向下兼容旧备份。**禁止** `fallbackToDestructiveMigration()`。
  * **绝不提交** `local.properties` 或任何 `AIzaSy` 开头的 Maps API Key（提交前执行 `git diff --cached | grep -E 'AIzaSy[0-9A-Za-z_-]{30,}'` 确认无输出）；不要暂存 `.codex-attachments/`、`.kotlin/`。
  * 发布门户：**不要**在 `/Users/wushengyin/Desktop/VC/release-portal`（iCloud 桌面）里执行 git push/fetch，必须 clone 到 `/tmp/app-hub-push` 修改 `data/apps.json` 后推送并删除临时目录。

## 6. 下一步计划（新对话直接从这里开始）
1. 在日语折叠屏上分别验收外屏抽屉与展开双栏的文件夹和标签拖动、轻振和长按菜单。
2. 验收原地升级与旧数据，覆盖 Room v5 → v6 迁移、旧备份恢复及标签排序往返。
3. 在折叠屏外屏和展开状态验收 ATLAS 两种地图、Mapbox 样式切换、冷启动首帧、定位晚到时居中及光晕，再回归地点进入和离开通知、地点地址换行、2 小时负荷、循环任务撤销及 AI 弹窗切换与预编辑。
4. 发布新版本时按流程：`app/build.gradle.kts` 递增 `versionCode`/`versionName` → 运行测试与 `assembleRelease` → 复制 APK 到 `/tmp/ZERO-v<VER>-release.apk` 并计算 `shasum -a 256` → 推送 `main` → `gh release create v<VER> ... -R shynw1983/zero` → 在 `/tmp/app-hub-push` 更新 `data/apps.json`（`latestVersion`、`updatedAt`、`fileSize`、`fileName`、`downloadUrl`、`sha256`、`downloads[0]`、`releases` 头部插入中文更新说明）→ 推送并验证 HTTP 200。
5. 每次发布或改动数据库/架构后，同步更新本文件与 `AGENTS.md` 的版本号、数据库版本及关键文件说明。

> 交接规则见 `AGENTS.md` 第 0 节：新对话开局自动先读本文件；每完成并提交一项工作即刷新本文件；用户说「交接」时立即更新、提交并推送。
