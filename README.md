# NJUPTer

中文为默认说明，English version is provided later in this document.

## 项目简介

NJUPTer 是一个面向南京邮电大学学生的 Android 课程表应用，使用 Kotlin 与 Jetpack Compose 构建。

它当前聚焦于三件事：

- 本地维护多份课程表
- 以周视图方式快速查看和编辑课程
- 从南邮教务系统（JWXT）导入课表并生成新课表

项目整体偏轻量，数据主要保存在本地文件中，适合日常使用、课程设计、以及 Compose + Repository 架构练习。

## 当前功能

### 课表查看

- 按周展示课程表
- 支持左右切换周次
- 支持直接跳转到指定周
- 支持根据学期起始日期自动定位到当前周
- 可按课表配置决定是否显示周末
- 启动时会优先恢复上次打开的课表，并在初始化期间显示加载态而不是错误空态

### 课表编辑

- 手动新增课程
- 编辑已有课程信息
- 删除单条课程安排
- 支持设置课程名称、教师、地点等信息
- 支持设置星期、节次范围、周次范围等排课信息
- 课程卡片使用颜色区分不同课程

### 多课表管理

- 创建多份独立课表
- 在不同课表之间切换
- 记住上次选择的课表
- 每份课表拥有独立的元数据与课程数据

### 课表配置

- 修改当前课表名称
- 设置学期开始日期
- 设置总教学周数
- 设置是否显示周末
- 自定义 12 节课的时间段

### 教务系统导入

- 从新建课表流程进入导入
- 在内置 WebView 中完成统一认证登录
- 自动捕获登录后的 Cookie 与学号
- 请求并解析南邮教务系统课表页面
- 提取课程名、教师、地点、星期、节次、周次等信息
- 导入前提供预览，并以新课表形式落地

### 桌面小组件

- 展示今天（晚间自动切换为明天）的课程列表，超出高度可滚动
- 可固定到桌面，按实例自定义背景图、纯色与透明度
- 午夜自动刷新，课表数据变更后同步更新

### 上课提醒

- 按当前课表与提前量排定上课提醒通知
- 开机、系统时间变更、应用更新后自动重新排程

## 界面结构

应用当前主要分为两个一级页面：

- `课表`：主课程表视图，负责查看、切换周次、切换课表、增改课程
- `设置`：当前课表配置页，负责修改名称、起始日期、周数、周末显示和节次时间

当用户选择从教务系统导入时，会进入单独的登录与导入流程页面，并在导入完成前显示预览确认弹窗。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Glance（桌面小组件）
- WorkManager
- Kotlin Coroutines + Flow
- Gson
- OkHttp
- JSoup
- SharedPreferences
- 本地 JSON 文件存储

## 项目结构

```text
app/src/main/java/com/example/njupter/
|- data/           数据源、仓库、模型、导入相关实现
|- domain/         日期计算、导入匹配等领域逻辑
|- ui/             Compose 页面、弹窗、组件
|- viewmodels/     ViewModel
|- widget/         桌面小组件（Glance）
|- notification/   上课提醒
|- MainActivity.kt 应用入口与页面装配
```

几个关键文件如下：

- `app/src/main/java/com/example/njupter/data/FileTimetableRepository.kt`：内存状态持有、课表初始化、课表切换、文件同步
- `app/src/main/java/com/example/njupter/data/LocalFileDataSource.kt`：课表索引与课表内容的本地文件读写
- `app/src/main/java/com/example/njupter/ui/timetable/TimetableScreen.kt`：主课表页面
- `app/src/main/java/com/example/njupter/viewmodels/TimetableViewModel.kt`：组合 UI 状态并驱动页面
- `app/src/main/java/com/example/njupter/ui/timetable/dialog/TimetableConfigDialog.kt`：新建/编辑课表配置
- `app/src/main/java/com/example/njupter/ui/settings/SettingsImportScreen.kt`：教务系统登录流程
- `app/src/main/java/com/example/njupter/data/import/JwxtClient.kt`：请求教务系统页面
- `app/src/main/java/com/example/njupter/data/import/JwxtParser.kt`：解析课表 HTML
- `app/src/main/java/com/example/njupter/domain/import/TimetableImportMatcher.kt`：把导入结果转换为本地模型
- `app/src/main/java/com/example/njupter/widget/ui/WidgetLayouts.kt`：小组件布局与渲染

## 数据存储方式

当前项目没有引入数据库，主要使用以下两类本地存储：

- `SharedPreferences`：保存轻量设置项，例如上次选择的课表 ID
- 本地 JSON 文件：保存课表索引以及每一份课表的详细课程数据

这种方式优点是实现简单、结构清晰、便于调试；也意味着数据默认只保存在当前设备本地。

## 运行环境

- Android Studio
- JDK 17 及以上（AGP 9 要求，可用 Android Studio 自带 JBR）
- Android SDK
- Android 模拟器或真机

当前 Gradle 配置：

- `minSdk = 24`
- `targetSdk = 36`
- `compileSdk = 36`

## 构建与运行

### 在 Android Studio 中运行

1. 克隆仓库
2. 使用 Android Studio 打开项目根目录
3. 等待 Gradle 同步完成
4. 选择模拟器或真机运行 `app`

### 命令行构建

Windows:

```bash
gradlew.bat assembleDebug
```

macOS / Linux:

```bash
./gradlew assembleDebug
```

如果你只想快速检查 Kotlin 编译是否通过，也可以执行：

```bash
./gradlew :app:compileDebugKotlin
```

### CI 自动构建与 Artifacts

项目已包含 GitHub Actions 工作流：`.github/workflows/android.yml`。

- Push / Pull Request / 手动触发（workflow_dispatch）会自动构建 Debug APK，并上传到 Actions Artifacts
- 打 `v*` 标签（例如 `v1.0.0`）会自动构建 Release APK + AAB，并上传 Artifacts
- 打 `v*` 标签时还会自动创建 GitHub Release，并附带 APK/AAB 文件

使用示例：

```bash
git tag v1.0.0
git push origin v1.0.0
```

## 网络与权限说明

- 应用声明了 `INTERNET` 权限
- 教务系统导入依赖对南邮相关站点的网络访问
- 工程已通过 `network_security_config` 处理对应域名的网络安全配置
- 导入流程目前依赖教务系统页面结构与登录流程保持稳定

## 当前限制

- 教务系统导入逻辑依赖当前网页结构，若学校系统改版，解析逻辑可能需要调整
- 数据仅保存在本地设备，不包含云同步、账号同步或跨设备同步
- 暂无内置导出、备份、恢复功能
- 自动化测试覆盖仍然有限
- 多课表的删除能力已在数据层存在，但当前 README 只描述已稳定暴露给用户的主流程功能

## 适用场景

- 南邮学生希望使用一个轻量、本地优先的课程表应用
- 想学习 Android Compose 项目组织方式
- 想参考本地文件持久化、仓库模式、网页课表导入的实现方式

## 后续可扩展方向

- 本地导出与导入课表文件
- 课表备份与恢复
- 更完善的导入错误提示与异常恢复
- 冲突检测与更智能的合并策略
- 更完整的自动化测试

## License

仓库当前未附带许可证文件。

如果你计划公开分发或接受外部贡献，建议补充一个明确的开源许可证，例如 MIT。

---

## English

## Overview

NJUPTer is an Android timetable app for Nanjing University of Posts and Telecommunications students, built with Kotlin and Jetpack Compose.

The project currently focuses on three core goals:

- managing multiple local timetables
- viewing and editing courses in a weekly layout
- importing timetable data from the NJUPT JWXT system

It is intentionally lightweight, stores data locally, and is also suitable as a learning project for Compose, repository-based architecture, and local persistence.

## Current Features

### Timetable Viewing

- weekly timetable layout
- horizontal week switching
- direct week selection
- automatic positioning based on semester start date
- optional weekend display per timetable
- restores the last selected timetable on launch, with a loading state during initialization

### Course Editing

- add courses manually
- edit existing course information
- delete individual course sessions
- configure course name, teacher, and classroom
- configure weekday, section range, and week range
- color-coded course cards

### Multi-Timetable Management

- create multiple independent timetables
- switch between timetables
- remember the last selected timetable
- keep metadata and course data isolated per timetable

### Timetable Configuration

- edit timetable name
- set semester start date
- set total teaching weeks
- toggle weekend visibility
- customize all 12 session time ranges

### JWXT Import

- start import from the new timetable flow
- log in through an embedded WebView
- capture cookies and student id automatically
- request and parse the NJUPT JWXT timetable page
- extract course name, teacher, classroom, weekday, sections, and weeks
- preview the imported result before creating a new timetable

### Home Screen Widget

- shows today's courses (automatically switches to tomorrow in the evening) in a scrollable list
- can be pinned to the home screen with per-instance background image, solid color, and transparency
- refreshes automatically at midnight and after timetable data changes

### Class Reminders

- schedules class reminder notifications from the current timetable with a configurable lead time
- reschedules automatically after boot, time changes, and app updates

## App Structure

The app currently has two primary top-level pages:

- `Timetable`: main weekly view for browsing, switching weeks, switching timetables, and editing courses
- `Settings`: current timetable configuration page for name, start date, total weeks, weekend visibility, and session times

The JWXT import flow is presented as a separate screen, followed by a preview dialog before the import is finalized.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Glance (home screen widget)
- WorkManager
- Kotlin Coroutines + Flow
- Gson
- OkHttp
- JSoup
- SharedPreferences
- local JSON file storage

## Project Layout

```text
app/src/main/java/com/example/njupter/
|- data/           data sources, repository, models, import implementation
|- domain/         date calculation and import matching logic
|- ui/             Compose screens, dialogs, and components
|- viewmodels/     ViewModels
|- widget/         home screen widget (Glance)
|- notification/   class reminders
|- MainActivity.kt app entry and screen composition
```

Key files:

- `app/src/main/java/com/example/njupter/data/FileTimetableRepository.kt`
- `app/src/main/java/com/example/njupter/data/LocalFileDataSource.kt`
- `app/src/main/java/com/example/njupter/ui/timetable/TimetableScreen.kt`
- `app/src/main/java/com/example/njupter/viewmodels/TimetableViewModel.kt`
- `app/src/main/java/com/example/njupter/ui/timetable/dialog/TimetableConfigDialog.kt`
- `app/src/main/java/com/example/njupter/ui/settings/SettingsImportScreen.kt`
- `app/src/main/java/com/example/njupter/data/import/JwxtClient.kt`
- `app/src/main/java/com/example/njupter/data/import/JwxtParser.kt`
- `app/src/main/java/com/example/njupter/domain/import/TimetableImportMatcher.kt`
- `app/src/main/java/com/example/njupter/widget/ui/WidgetLayouts.kt`

## Data Storage

The project does not currently use a database.

Instead, it relies on:

- `SharedPreferences` for lightweight app settings such as the last selected timetable id
- local JSON files for timetable indexes and detailed timetable content

This keeps the implementation simple and transparent, but also means data stays on the current device only.

## Requirements

- Android Studio
- JDK 17 or later (required by AGP 9; Android Studio's bundled JBR works)
- Android SDK
- Android emulator or physical device

Current Gradle configuration:

- `minSdk = 24`
- `targetSdk = 36`
- `compileSdk = 36`

## Build and Run

### Android Studio

1. Clone the repository
2. Open the project root in Android Studio
3. Wait for Gradle sync to finish
4. Run the `app` configuration on an emulator or device

### Command Line

Windows:

```bash
gradlew.bat assembleDebug
```

macOS / Linux:

```bash
./gradlew assembleDebug
```

To verify Kotlin compilation only:

```bash
./gradlew :app:compileDebugKotlin
```

## Network and Permissions

- the app declares the `INTERNET` permission
- JWXT import depends on network access to NJUPT-related endpoints
- the project includes a `network_security_config` for the required domains
- the import pipeline depends on the current JWXT page structure remaining stable

## Current Limitations

- JWXT import is tied to the current website structure and may need updates if the site changes
- data is stored locally only, with no cloud sync or account sync
- there is no built-in export, backup, or restore flow yet
- automated test coverage is still limited
- timetable deletion exists in the data layer, but this README only documents user-facing flows that are already stable in the UI

## Good Fit For

- NJUPT students who want a lightweight local-first timetable app
- developers learning Android app structure with Compose
- projects exploring repository patterns, local file persistence, and timetable import workflows

## Future Directions

- local timetable export/import
- backup and restore
- better import error handling and recovery
- conflict detection and smarter merge behavior
- broader automated test coverage

## License

No license file is currently included in this repository.

If you plan to publish the project or accept outside contributions, adding a clear open-source license such as MIT is recommended.
