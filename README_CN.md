# 班车查询（Bus Schedule）

一款用于查看和管理班车/通勤车路线及发车时间的 Android 应用。

## 功能

- **路线管理** — 添加、编辑、删除班车路线（名称、起点、终点）。
- **车次管理** — 为每条路线增删改发车时间，使用滚轮时间选择器。
- **下一班次显示** — 路线卡片展示最近发车时间，车次列表每 60 秒自动刷新。
- **收藏与默认路线** — 标记常用路线，设置默认路线置顶显示。
- **导入/导出** — 所有数据以 Base64 编码 JSON 复制到剪贴板，或从剪贴板导入。
- **暗黑模式** — Material3 DayNight 主题自动适配。
- **边到边显示** — 系统栏透明，内容通过内边距适配。

## 截图

<!-- TODO: 添加截图 -->

## 技术栈

| 类别       | 技术                                                    |
| ---------- | ------------------------------------------------------- |
| 语言       | Java 11                                                 |
| UI         | Android Material3, RecyclerView, LiveData               |
| 持久化     | Room 2.8.4                                              |
| 时间选择器 | WheelPicker (JitPack: `com.github.open-android`)        |
| 构建       | Gradle 9.4.1, Android Gradle Plugin 9.0.0-alpha06       |
| 最低 API   | API 29 (Android 10)                                     |
| 目标 API   | API 36                                                  |

## 快速开始

### 环境要求

- Android Studio 或 JDK 17+ 及 Android SDK
- API 29+ 的设备或模拟器

### 构建

```bash
./gradlew assembleDebug          # 调试版 APK
./gradlew assembleRelease        # 发布版 APK
```

### 测试

```bash
./gradlew test                   # 单元测试
./gradlew connectedAndroidTest   # 仪器测试（需连接设备）
```

## 架构

**单 Activity** — `MainActivity` 承载 `QueryFragment` 容器，后者手动管理子 Fragment 的回退栈。不使用 Navigation Component、DI 框架或 ViewModel 层。

```
MainActivity (边到边)
  └── QueryFragment (导航容器)
        ├── RouteListFragment        ← 路线列表 + 导入/导出
        ├── ScheduleListFragment     ← 车次列表 + 自动刷新横幅
        ├── AddEditRouteFragment     ← 添加/编辑路线表单
        └── AddEditScheduleFragment  ← 滚轮时间选择器
```

### 包结构

```
com.senk.bus
├── MainActivity.java
├── data/
│   ├── AppDatabase.java            Room 单例（"bus_database"，v1）
│   ├── AppExecutors.java           单线程磁盘 IO 执行器
│   ├── dao/
│   │   ├── RouteDao.java           路线 CRUD、收藏、默认管理
│   │   └── ScheduleDao.java        车次 CRUD
│   └── entity/
│       ├── Route.java              id, name, origin, destination, isFavorite, isDefault
│       └── Schedule.java           id, routeId（外键 → 级联删除）, departureTime
└── ui/
    ├── QueryFragment.java          子 Fragment 导航容器
    ├── RouteListFragment.java      路线列表、导入导出、长按菜单
    ├── ScheduleListFragment.java   车次列表 + 滚动联动横幅
    ├── AddEditRouteFragment.java   路线增改表单
    ├── AddEditScheduleFragment.java   车次增改（滚轮选择器）
    ├── WheelTimePicker.java        封装 WheelPicker，提供时:分选择
    └── adapter/
        ├── RouteAdapter.java       路线 RecyclerView 适配器
        └── ScheduleAdapter.java    车次适配器（带"下个"标记）
```

### 数据模型

```
routes (路线)
├── id             INTEGER (主键，自增)
├── name           TEXT    (路线名称)
├── origin         TEXT    (起点)
├── destination    TEXT    (终点)
├── isFavorite     INTEGER (是否收藏)
└── isDefault      INTEGER (是否默认)

schedules (车次)
├── id             INTEGER (主键，自增)
├── routeId        INTEGER (外键 → routes.id，级联删除)
└── departureTime  TEXT    (格式 HH:mm，默认 "00:00")
```

### 线程模型

`AppExecutors.diskIO(Runnable)` 提供单线程执行器处理 Room 写操作。Room 返回的 `LiveData` 查询自动在主线程观察。

## 导入/导出

数据以 Base64 编码 JSON 导出到系统剪贴板。导入时从剪贴板读取并插入所有路线及车次。

**JSON 格式：**

```json
{
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

## CI/CD

GitHub Actions 在推送 `v*` 标签时自动构建发布版 APK、签名并上传至 GitHub Releases。

## 许可证

[MIT](LICENSE)

---

[English](README.md)
