# 腕记 WristNote

给小米手表（Wear OS 圆屏）做的免费笔记 + 课程表 + 倒计时应用。
手表自带的笔记全要收费，所以自己写了一个：不联网、没有广告、数据只存在手表本地。

- 软件名称：腕记（WristNote）
- 包名：`com.ioo.watchnote`
- 当前版本：**Beta11（versionCode 77）**
- 测试机型：小米 Watch 5（M2505W1，Wear OS，480×480 圆屏，1.54" AMOLED）
- 最低系统：Wear OS 3 / Android 11（minSdk 26），目标 Android 16（targetSdk 36）

## 下载安装

APK 在 `release/` 目录：

- `release/腕记-Beta11.apk` —— 手机点开即可下载，传给手表安装

手表安装方法：手机用「小米运动健康」或 adb 连接手表后安装，或者把手表连 Wi-Fi 用 adb 安装。

## 功能

### A 笔记页
- 列表显示时间、标签、★标记、✓完成、语音图标、正文
- 新建 / 编辑 / 删除笔记，标签自己输入
- ★标记、✓完成
- 语音转文字（系统语音识别）、录音 / 播放 / 删除语音
- 长按 2 秒删除（带确认弹窗）

### B 课程表页
- 周一到周日切换，显示当天所有时段
- 时段卡片显示起止时间、类型、剩余时间
- 添加 / 编辑 / 删除时段；时间组（常用时段批量套用）；特殊时段（某一周单独覆盖）
- 恢复默认时间表

### C 剩余时间页
- 整屏圆环进度，几乎贴着圆屏边缘
- 当前阶段名 / 本段起止时间 + 当前时间 / 剩余大字 / 下一阶段名与起止时间
- 严格按设计稿：没有百分比、没有多余文字

### D 设置页
- 时间表管理、特殊时段
- 导出 / 从备份恢复（JSON）
- 更换主题颜色（5 套）
- 动画效果：满血（鱼眼、滑入、圆环）/ 性能（只保留翻页和按压）
- 顶部时间显示开关、震动反馈开关
- 发到手机：选笔记（可多选）→ 选文本 / 图片 → 手表开一个局域网下载服务，手机浏览器打开地址直接下载文件
- 清空全部数据、关于、自测工具、开源网址

### 其他
- 四页左右循环滑动；底部半圆按钮（笔记页是「＋」，其他页按需）
- 系统返回键：子页面回上一层 → 页面回上一页 → 笔记页再按退出确认
- 表冠滚动适配：小米手表缺少 Wear 触感类（`WearHapticFeedbackConstants`），官方表冠行为会崩，这里换成自定义的 `RotaryBus` 手动分发滚动
- 崩溃自动记录日志：崩溃页可复制 / 保存日志

## 构建

环境：

- JDK 21
- Gradle 9.4.1
- Android SDK：compileSdk 37 / build-tools 36.0.0
- Compose BOM 2026.06.01，Wear Compose Material3 1.6.2，Kotlin 2.4.0，AGP 9.2.1

因为依赖都在本地 Gradle 缓存里，构建命令用离线模式：

```bash
export JAVA_HOME=<你的 JDK21 路径>
export ANDROID_HOME=<Android SDK 路径>
export GRADLE_USER_HOME=<你的 Gradle 缓存目录>
cd WristNote-GitHub
gradle --offline --no-daemon --console=plain :app:assembleRelease
```

产物：`out/app/outputs/apk/release/app-release.apk`

release 开了 R8 混淆 + 资源压缩，签名用的是仓库里的 `app/debug.keystore`（测试签名，别名 `watchnote`，密码 `123456`）。要正式发布请换成你自己的签名。

## 目录结构

```
app/src/main/java/com/ioo/watchnote/
├── app/            应用入口、崩溃处理
├── core/           通用组件（列表页框架、返回键、设置行、鱼眼、确认弹窗、表冠兼容、长按删除、震动、squircle 圆角）
├── data/           数据存储（笔记、时段、时间组、特殊时段、偏好设置）
├── navigation/     四页 pager + 子页面栈
├── pages/
│   ├── notes/      笔记页、笔记编辑页
│   ├── timetable/  课程表页、管理页、时段编辑、时间组、特殊时段
│   ├── clock/      剩余时间页
│   └── settings/   设置页、主题、动画、关于、自测、发到手机、开源网址
├── share/          导出文本/图片、局域网下载服务
└── theme/          5 套主题色
```

## 权限

- `RECORD_AUDIO`：录音与语音转文字（仅本地）
- `VIBRATE`：震动反馈
- `INTERNET`：仅用于「发到手机」的局域网下载服务（手表开热点/同一 Wi-Fi 时手机访问）

应用不上传任何数据到服务器，没有账号，没有广告。

## 更新记录

- Beta11（77）：子页面返回键改回官方 EdgeButton（和主页面「＋」同款）；「发到手机」改成先选笔记（可多选）再选文本/图片，只生成选中的；关于页新增开源网址
- Beta10（76）：新增「发到手机」局域网直链下载；子页面返回键常显
- Beta9（74）：笔记长按滑动菜单（已回退）
- Beta8（73）：修复子页面返回键尺寸失控、子页面背景透明
- Beta7（72）：部分圆角改成连续曲率圆角（squircle）
- Beta6（71）：全局轻震动反馈
- 更早版本：见版本档案

## 开源协议

Apache License 2.0，见 [LICENSE](LICENSE)。
基于 Wear Compose for Material 3 构建，感谢 Android / Wear OS 开源生态。
