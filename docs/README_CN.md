# 班车查询（Bus Schedule）

一款用于查看和管理班车/通勤车路线及发车时间的 Android 应用，界面为简体中文。

- 包名：`com.senk.bus`
- 单模块 Gradle 工程（`:app`）
- 当前版本：`1.0.0`（versionCode `100000`）

## 功能

- **路线管理** — 添加、编辑、删除路线（名称、始发站、终点站）。添加入口在工具栏菜单中，没有
  FAB 悬浮按钮。
- **车次管理** — 用自定义滚轮时间选择器（`WheelPicker`）为路线添加发车时间；已有车次可在车次
  列表的编辑弹窗中修改或删除。
- **下一班显示** — 路线卡片展示该路线的下一班发车时间；车次列表用 `NEXT` 徽章标出下一班，并
  显示倒计时横幅。两个页面都在每个整分钟重新计算，保证"下一班"不会停留在已过点的班次上。
- **收藏与默认路线** — 可收藏路线并设置一条默认路线；路线列表排序为：默认 → 收藏 → 最新创建。
- **桌面小组件** — `RemoteViews` 列表小组件展示所有路线及其下一班发车时间；点击条目进入对应
  路线的车次列表。数据变更、App 回到前台、以及"下一班"时刻由闹钟触发时都会刷新。
- **导入/导出** — 全部数据以 Base64 编码 JSON（`version: 2`）复制到剪贴板，也可从剪贴板导入。
  导入会校验字段与 `HH:mm` 时间、跳过非法条目、合并重复路线、最多保留一条默认路线，并提示
  导入/跳过数量。
- **深色模式** — 应用使用 Material3 DayNight 主题与主题属性；桌面小组件有独立的日间/夜间配色。
  页面不依赖硬编码的浅色。
- **边到边显示** — 系统栏透明，窗口内边距手动适配。
- **无障碍** — 工具栏返回图标带内容描述；文字颜色跟随主题，对比度随明暗模式变化。

App 不申请任何运行时权限；Manifest 中只有 `MainActivity`、小组件 `AppWidgetProvider` 与小组件
`RemoteViewsService` 三个组件。

## 技术栈

| 类别        | 取值                                                                          |
| ----------- | ----------------------------------------------------------------------------- |
| 语言        | Kotlin（全部生产/测试代码与 Room 实体）；JVM target 11                        |
| Kotlin 工具链 | 由 AGP 9 内置 Kotlin 2.2.10 编译，不引入 `kotlin-android` 插件；Room 用 KSP 2.2.10-2.0.2 |
| UI          | Material3 1.13.0、AppCompat 1.7.1、RecyclerView 1.4.0、Fragment 1.8.9、LiveData 2.10.0 |
| 持久化      | Room 2.8.4（KSP 处理器）                                                       |
| 时间选择器  | `WheelPicker`（JitPack：`com.github.open-android:WheelPicker:v1.0.0`）         |
| 构建        | Gradle 9.4.1（wrapper）、Android Gradle Plugin 9.0.0、Gradle 使用 JDK 17      |
| SDK 版本    | minSdk 29（Android 10）· targetSdk 36 · compileSdk 36                         |
| 测试        | JUnit 4.13.2（JVM）+ AndroidX Test/Espresso + Room Testing（含仪器测试）        |
| CI          | GitHub Actions —— main/PR 构建测试，`v*` 标签签名发布                          |

## 快速开始

### 环境要求

- JDK 17 运行 Gradle（Kotlin/Java 字节码目标为 11）。
- Android SDK：**Platform 36** 与 **Build-Tools 36.0.0**。
- 一台 API 29+ 的设备或模拟器。

自定义 `WheelPicker` 依赖来自 JitPack，首次构建需要能访问 `https://jitpack.io`。

### 构建

```bash
./gradlew assembleDebug           # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease         # app/build/outputs/apk/release/
./gradlew test                    # JVM 单元测试
./gradlew lintDebug               # Android Lint
./gradlew connectedAndroidTest    # 仪器测试（需连接设备/模拟器）
```

发布版默认**不签名**：仓库不保存 keystore。本地签名发布版时需同时提供四个环境变量；未设置
`RELEASE_KEYSTORE_FILE` 时 release 构建保持未签名：

```bash
RELEASE_KEYSTORE_FILE=/absolute/path/to/release.jks \
RELEASE_STORE_PASSWORD='...' \
RELEASE_KEY_ALIAS='...' \
RELEASE_KEY_PASSWORD='...' \
./gradlew assembleRelease
```

### 版本号策略

`versionCode = 100000`、`versionName = "1.0.0"` 是固定值。发版是显式动作：在
`app/build.gradle` 中上调两者并提交，然后推送 `v*` 标签；标签名决定发布产物文件名
（`Bus_<tag>.apk`）。

## 架构

单 Activity + 手动管理子 Fragment。没有 ViewModel、没有 DI 框架、没有 Navigation Component。

全部生产代码、单元测试与仪器测试均为 Kotlin（`.kt`），仓库中没有 `.java` 源码；Kotlin 源码沿用
标准的 `src/main/java`、`src/test/java`、`src/androidTest/java` 目录。

```
MainActivity（边到边，launchMode=singleTop）
  └── QueryFragment（导航容器：子 FragmentManager + 手动回退栈）
        ├── RouteListFragment        路线列表、下一班时间、导入/导出、长按菜单
        ├── ScheduleListFragment     车次列表、倒计时横幅、编辑弹窗、分钟级刷新
        ├── AddEditRouteFragment     添加/编辑路线表单
        └── AddEditScheduleFragment  用滚轮选择器添加发车时间

桌面
  └── BusWidgetProvider（AppWidgetProvider）+ BusWidgetService（RemoteViewsService）
        ├── res/layout/widget_bus.xml
        └── res/layout/widget_bus_item.xml
```

### 包结构

```
com.senk.bus
├── MainActivity.kt                  单 Activity，边到边，处理小组件跳转 intent
├── data/
│   ├── AppDatabase.kt               Room 单例（"bus_database"，schema v1）
│   ├── AppExecutors.kt              单线程磁盘 IO 执行器（"bus-disk-io"）
│   ├── NextDeparture.kt             纯 Kotlin 的 HH:mm / 下一班计算工具（有单元测试）
│   ├── dao/
│   │   ├── RouteDao.kt              路线 CRUD、收藏、默认、同步列表
│   │   └── ScheduleDao.kt           车次 CRUD、按路线与全量同步查询
│   └── entity/
│       ├── Route.kt                   Kotlin @Entity：id、name、origin、destination、两个标记
│       └── Schedule.kt                Kotlin @Entity：id、routeId（外键 → 级联删除，有索引）、时间
├── ui/
│   ├── QueryFragment.kt             子 Fragment 导航容器（导航 500ms 防抖）
│   ├── RouteListFragment.kt         路线列表 + 分钟级刷新 + 导入/导出
│   ├── ScheduleListFragment.kt      车次列表 + 横幅 + 分钟级刷新 + 编辑弹窗
│   ├── AddEditRouteFragment.kt      路线增改表单
│   ├── AddEditRouteViewModel.kt     路线表单状态保留（配置变更不丢输入）
│   ├── AddEditScheduleFragment.kt   车次添加页（编辑走列表弹窗）
│   ├── WheelTimePicker.kt           封装两个 WheelPicker，提供时:分选择
│   └── adapter/
│       ├── RouteAdapter.kt          路线卡片（DiffUtil）
│       └── ScheduleAdapter.kt       车次条目 + "NEXT" 徽章（DiffUtil）
└── widget/
    ├── BusWidgetProvider.kt         AppWidgetProvider：RemoteViews、点击模板、闹钟刷新
    └── BusWidgetService.kt          RemoteViewsService：列表行与下一班时间
```

### 数据模型

```
routes（路线）
├── id             INTEGER  主键，自增
├── name           TEXT     非空
├── origin         TEXT     非空
├── destination    TEXT     非空
├── isFavorite     INTEGER  是否收藏
└── isDefault      INTEGER  是否默认

schedules（车次）
├── id             INTEGER  主键，自增
├── routeId        INTEGER  外键 → routes.id（级联删除，有索引）
└── departureTime  TEXT     严格 HH:mm，默认 "00:00"
```

Room schema 版本为 **2**，`exportSchema = true`；schema 会导出到 `app/schemas/`。将来修改表结构
时必须升级版本并在 `AppDatabase` 中增加 `Migration`。`android:allowBackup` 为开启状态，数据库
默认会进入 Android 自动备份。

### 下一班车计算

所有"下一班"逻辑集中在 `com.senk.bus.data.NextDeparture`（纯 Kotlin、无 Android 依赖，由
`app/src/test/java/com/senk/bus/data/NextDepartureTest.kt` 的 13 个 JVM 用例覆盖）：

- `nowHHmm()`、`isValid(String)`、`parseMinutes(String)`、`format(int)`、`minutesUntil(from, to)`：
  均处理跨天回绕；非法时间返回 `-1` 而不是抛异常；
- `find(nowHHmm, schedules)`：返回 `time`、`remainingMinutes` 与命中的 `Schedule`；没有合法班次时
  返回 `null`。该函数与列表顺序无关、会跳过非法条目；当所有合法班次都已过点时，返回次日最早的
  一班。

DAO 为展示需要按 `departureTime ASC` 返回车次，但正确性不依赖该顺序——请使用
`NextDeparture.find(...)`，不要用字符串比较。

### 线程模型

`AppExecutors.diskIO(...)` 是单线程执行器（线程名 `bus-disk-io`），所有不返回 `LiveData` 的
Room 读写都必须走它；Room `LiveData` 查询自动在主线程观察。

后台任务禁止调用 `requireContext()`、`requireActivity()`、`getString()` 或访问 View。必须在主
线程先捕获 `final Context appContext = requireContext().getApplicationContext()` 以及所需字符串/
数据；回到 UI 时用 `runOnUiThread`/`view.post`，并在访问 View 前检查 `isAdded()`/
`getView() != null`。

### UI 行为细节

- `RouteListFragment` 在每个整分钟（以及列表变化后）重新计算各路线下一班时间。
- `ScheduleListFragment` 每分钟按"距当前时间的远近"重排车次，`NEXT` 徽章绑定真实的下一班
  schedule id，并更新倒计时横幅。
- `QueryFragment` 导航入口做 500ms 防抖，返回键使用 `popBackStackImmediate` 同步出栈，快速
  双击/连按返回不会跳级。
- 保存/删除只在数据库写成功后提示；失败时保留当前页面与输入。

## 桌面小组件

`BusWidgetProvider`（在 `AndroidManifest.xml` 中声明）通过 `BusWidgetService`
（`RemoteViewsService`）渲染可滚动的路线列表。

- 每行展示路线名、始发站 → 终点站、下一班发车时间；路线顺序与 App 内一致（默认 → 收藏 →
  id 倒序）。
- 行点击使用指向 `MainActivity` 的显式 `PendingIntent`，集合模板为 `FLAG_MUTABLE`（fill-in intent
  必需），并带 `FLAG_ACTIVITY_SINGLE_TOP | CLEAR_TOP`；`MainActivity` 为
  `launchMode="singleTop"`，在 `onNewIntent` 中处理，先按 routeId 查库校验（路线已不存在时 Toast
  提示），再打开对应车次列表。
- 数据在写操作后通过 `BusWidgetProvider.notifyDataChanged(...)` 刷新；App 回到前台时也会刷新。
  另外用 `AlarmManager.setAndAllowWhileIdle`（非精确闹钟，不申请精确闹钟权限）在"最早下一班"
  发车时刻后 1 秒触发自定义刷新广播并重新排下一次闹钟；没有班次或查询失败时按 30 分钟兜底。
  `updatePeriodMillis` 为 30 分钟，作为额外兜底。
- 小组件颜色在 `res/values/widget_colors.xml` + `res/values-night/widget_colors.xml`；小组件专用
  文案在 `res/values/strings_widget.xml`（与 App 共用的文案仍在 `strings.xml`）。每个 TextView
  都显式设置颜色，避免 RemoteViews 依赖主题上下文。
- `bus_widget_info.xml` 提供 `previewLayout` 与 `targetCellWidth/Height`（4×2）。集合 API
  （`setRemoteAdapter(Intent)`、`notifyAppWidgetViewDataChanged`）在 API 31/37 起已废弃但仍可用；
  迁移到 `RemoteCollectionItems` 是已知的后续事项。

## 导入/导出

全部数据以 Base64 编码 JSON 复制到系统剪贴板。导出使用 `Base64.NO_WRAP`；导入兼容
`Base64.DEFAULT`（容忍换行）。JSON 根节点带 version 字段：

```json
{
  "version": 2,
  "routes": [
    {
      "name": "班车 A",
      "origin": "公司",
      "destination": "车站",
      "isFavorite": true,
      "isDefault": false,
      "schedules": [
        { "departureTime": "08:30" },
        { "departureTime": "17:00" }
      ]
    }
  ]
}
```

导入行为（实现于 `RouteListFragment`）：

- 防御式读取剪贴板（无剪贴板内容/非文本内容 → 提示导入失败）；
- 名称/始发站/终点站为空的路线跳过并计数；
- 发车时间必须严格匹配 `HH:mm`（`NextDeparture.isValid`），非法时间跳过并计数，绝不写库；
- 整个导入在单个 `db.runInTransaction(...)` 中完成，事务结束后统一刷新界面并通知小组件；
- 重复路线（名称 + 始发站 + 终点站相同）合并：复用已有路线，只插入尚不存在的发车时间；重复
  导入同一份数据是幂等的；
- 最多一条路线保留 `isDefault = true`（导入前先清除默认，导入项中第一条默认生效）；合并时
  `isFavorite` 只会被置为 true；
- 结果 Toast 提示导入的路线数与跳过的条目数（`import_result`）。

`version` 目前只用于向前兼容导出，导入器接受任何包含合法 `routes` 数组的 payload，不校验该
字段；格式变化时递增版本号。

## 深色模式与资源约定

- `Theme.Bus` 继承 `Theme.Material3.DayNight.NoActionBar`，明暗色板由 Material3 提供，因此不需要
  `values-night/themes.xml`。
- 布局使用主题属性（`?attr/colorOnSurface`、`?attr/colorSurface`、`?attr/colorOnSurfaceVariant`、
  `?attr/colorPrimary`、`?attr/colorError`），不硬编码颜色。唯一例外是 `NEXT` 徽章：固定绿色底上
  保留白字以保证对比度。
- 只在主题上下文之外需要的具体颜色（如弹窗背景）放在 `values/colors.xml`，并在
  `values-night/colors.xml` 提供夜间值。
- 启动图标是 `res/mipmap-anydpi-v26/` 中的单个自适应图标，带 `<monochrome>` 图层；`-v26` 限定符
  是 `<adaptive-icon>` XML 必需的（改到 `mipmap-anydpi` 会导致 AAPT 链接 `@mipmap/ic_launcher`
  失败）。
- 用户可见文案在 `values/strings.xml`；无障碍专用文案在 `values/strings_accessibility.xml`；
  小组件专用文案在 `values/strings_widget.xml`。

## CI/CD

`.github/workflows/android.yml`（默认最小权限 `contents: read`，release job 提升为
`contents: write`；concurrency 会取消同一 ref 的旧运行）：

| 触发条件 | 执行内容 |
| --- | --- |
| 推送到 `main`、向 `main` 提 PR | JDK 17 + Android SDK，`sdkmanager "platforms;android-36" "build-tools;36.0.0"`，`./gradlew --no-daemon assembleDebug test lintDebug`，上传 debug APK artifact（保留 7 天） |
| 推送 `v*` 标签 | 先跑上面的 build job，再解码 `RELEASE_KEYSTORE_BASE64` 为临时 keystore，带签名环境变量执行 `./gradlew --no-daemon assembleRelease`，复制为 `dist/Bus_<tag>.apk`，用 `softprops/action-gh-release@v2` 发布 GitHub Release |

发布 job 需要的仓库 secrets：

| Secret | 用途 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64 编码的 release keystore（`.jks`） |
| `RELEASE_STORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | keystore 中的 key alias |
| `RELEASE_KEY_PASSWORD` | key 密码 |

生成 keystore secret：

```bash
base64 -w 0 release.jks        # Linux/macOS
# PowerShell:
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))
```

## 已知限制 / 后续事项

当前代码刻意保持轻量、少依赖的架构，以下为已知且暂时接受的事项：

- **状态层仍是渐进式**：`AddEditRouteViewModel` 已覆盖路线表单状态保留，但多数页面仍是
  Fragment 本地状态 + 手写导航（无 Navigation Component）。
- **Room 演进进入基线阶段**：已启用 schema 导出、补齐 1→2 迁移测试，并为 schedules 增加
  `routeId + departureTime` 唯一约束；后续约束扩展仍需版本升级与迁移。
- **小组件 API**：collection `RemoteViews` API 已废弃；不支持"每个小组件实例固定一条路线"的
  配置；闹钟为非精确闹钟（Doze 下可能延迟）。
- **测试**：已新增 `RouteTransferTest`（JVM）与 `AppDatabaseDaoTest`、`AppDatabaseMigrationTest`
  （仪器测试），但 UI 流程覆盖仍需继续补齐。
- **Kotlin + KSP 工具链**：Room 使用 KSP 处理。KSP 目前仍通过 Kotlin source-set DSL 注册生成
  源码，因此 `gradle.properties` 保留了实验性开关 `android.disallowKotlinSourceSets=false`；待 KSP
  原生支持 AGP 9 内置 Kotlin 后可移除。
- **Lint**：构建 0 error，但存在依赖/SDK 版本偏旧的 warning，以及 `mipmap-anydpi-v26` 必需目录
  导致的 `ObsoleteSdkInt` 误报。
- **打包**：`versionCode`/`versionName` 为静态值（发版时手动上调）；App 仅中文（无翻译资源
  目录）；`WheelPicker` 依赖 JitPack。
- **截图**：仓库中暂未提供截图。

## 文档

| 文件 | 内容 |
| --- | --- |
| `README.md` | 项目总览（英文） |
| `docs/README_CN.md` | 本文件（中文版） |
| `AGENTS.md` | 面向 AI/开发代理的仓库工作指南 |
| `LICENSE` | MIT 许可证 |

以下本地内容不纳入版本控制：历史审计/修复报告放在 `.reports/`（已被 git 忽略）；IDE/工具目录
（`.idea/`、`.gradle/`、`.kotlin/`、`.claude/`）同样被忽略。

## 许可证

[MIT](../LICENSE)

---

[English](../README.md)
