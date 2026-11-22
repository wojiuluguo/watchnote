# 腕记 WristNote（小米 Watch 5 · Wear OS 圆屏笔记）

给小米 Watch 5（Wear OS，480×480 圆屏）做的免费手表笔记应用：记作业、记事情、课程表、倒计时，配中国传统色主题。

- 包名：`com.ioo.watchnote`
- 当前版本：**4.0.20（versionCode 108）**
- 技术：Kotlin + Jetpack Compose for Wear OS，原生实现，不依赖 WebView

## 页面

- **笔记**：分类筛选、新建/编辑/查看、勾完成、长按删除
- **办事**：待办卡片、完成/编辑/删除
- **课程表**：星期切换、时段管理、单双周/特殊周
- **设置**：震动反馈、主题颜色（5 个预设色 + 526 个中国传统色，按 15 个主色系分页）、分类管理、导出备份、发到手机、关于、自检

## 构建

```
JAVA_HOME=D:\OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1\jdk-21.0.12.1+1
ANDROID_HOME=C:\Users\ioo\AppData\Local\Android\Sdk
gradle --offline --no-daemon --console=plain :app:assembleRelease
```

产物：`out/app/outputs/apk/release/app-release.apk`

签名用的是仓库里的测试 keystore：`app/debug.keystore`（别名 `watchnote`，密码 `123456`），只用于调试/内测，正式发布请换成自己的正式签名。

## 文档

- [docs/版本历史.md](docs/版本历史.md)：从早期版本到 4.0.20 的更新记录
- [docs/最终版施工计划.md](docs/最终版施工计划.md)：最终版各 Step 的施工计划
