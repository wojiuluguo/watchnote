# 把三个前置模块示例打成 assets/plugins/*.wplugin（插件模块 0.3，minApi 3）：
#   uikit.wplugin       前置模块（kind=lib），只提供 FrontKit 这个类，没有页面
#   frontui.wplugin     依赖 uikit 的 UI 插件：大数字/环形进度都调前置模块的助手
#   frontalgo.wplugin   依赖 uikit 的算法插件：时间怎么念、百分比怎么算都调前置模块
# 需要先构建一次宿主（:app:assembleRelease），因为要拿带 0.3 API 的宿主 classes。
# 用法：pwsh -File build-front-samples.ps1   （需要在 WristNote-v5 目录下跑）
$ErrorActionPreference = "Stop"
$v5 = "C:\Users\ioo\Desktop\手表笔记\WristNote-v5"
$work = Join-Path $v5 "build-front-samples"
$classes = Join-Path $v5 "out\app\intermediates\built_in_kotlinc\release\compileReleaseKotlin\classes"
$jdk = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$sdk = "C:\Users\ioo\AppData\Local\Android\Sdk"
$kt = "C:\Users\ioo\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib\2.4.0\4e9f4b531dce5c7dee58ed2de9a8b9a763c8dc6c\kotlin-stdlib-2.4.0.jar"
$assets = Join-Path $v5 "app\src\main\assets\plugins"

Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$work\kit\classes", "$work\kit\dex", "$work\ui\classes", "$work\ui\dex", "$work\algo\classes", "$work\algo\dex" | Out-Null
New-Item -ItemType Directory -Force -Path $assets | Out-Null

function Compile-One($name, $src, $extraCp) {
    $cp = "$classes;$kt"
    if ($extraCp) { $cp = "$cp;$extraCp" }
    & "$jdk\bin\javac.exe" -encoding UTF-8 -source 17 -target 17 -cp $cp -d "$work\$name\classes" $src
    & "$jdk\bin\jar.exe" cf "$work\$name\$name.jar" -C "$work\$name\classes" .
    & "$sdk\build-tools\36.0.0\d8.bat" --release --min-api 26 --lib "$sdk\platforms\android-37.0\android.jar" --lib "$kt" --output "$work\$name\dex" "$work\$name\$name.jar"
}

# 先编前置模块：后面两个插件编译时要能解析 FrontKit
Compile-One "kit" "$v5\docs\external-sample\FrontKit.java" $null
Compile-One "ui" "$v5\docs\external-sample\FrontUiPlugin.java" "$work\kit\classes"
Compile-One "algo" "$v5\docs\external-sample\FrontAlgoPlugin.java" "$work\kit\classes"

function Pack-One($name, $zipName, $manifestJson) {
    $pkg = "$work\$name\pkg"
    New-Item -ItemType Directory -Force -Path $pkg | Out-Null
    Copy-Item "$work\$name\dex\classes.dex" "$pkg\classes.dex" -Force
    [System.IO.File]::WriteAllText("$pkg\plugin.json", $manifestJson, (New-Object System.Text.UTF8Encoding($false)))
    $zipPath = Join-Path $assets $zipName
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::CreateFromDirectory($pkg, $zipPath, [System.IO.Compression.CompressionLevel]::Optimal, $false)
    "OK -> $zipPath (" + (Get-Item $zipPath).Length + " bytes)"
}

$kitManifest = @'
{
  "schemaVersion": 1,
  "id": "com.example.uikit",
  "name": "前置 UI 工具",
  "version": 1,
  "minApi": 3,
  "kind": "lib",
  "provides": ["com.example.uikit.FrontKit"],
  "entryClass": "com.example.uikit.FrontKitPlugin",
  "author": "腕记",
  "description": "前置模块示例（0.3）：只有算法和排版助手，没有任何页面。"
}
'@
Pack-One "kit" "uikit.wplugin" $kitManifest

$uiManifest = @'
{
  "schemaVersion": 1,
  "id": "com.example.frontui",
  "name": "前置 UI 示例",
  "version": 1,
  "minApi": 3,
  "entryClass": "com.example.frontui.FrontUiPlugin",
  "permissions": ["READ_NOTES"],
  "requires": ["com.example.uikit>=1"],
  "author": "腕记",
  "description": "调用前置模块的 UI 插件示例：大数字/环形进度交给 uikit 排版。"
}
'@
Pack-One "ui" "frontui.wplugin" $uiManifest

$algoManifest = @'
{
  "schemaVersion": 1,
  "id": "com.example.frontalgo",
  "name": "前置算法示例",
  "version": 1,
  "minApi": 3,
  "entryClass": "com.example.frontalgo.FrontAlgoPlugin",
  "permissions": ["READ_NOTES"],
  "requires": ["com.example.uikit>=1"],
  "author": "腕记",
  "description": "调用前置模块的算法插件示例：字数→分钟→人话都走 uikit。"
}
'@
Pack-One "algo" "frontalgo.wplugin" $algoManifest
