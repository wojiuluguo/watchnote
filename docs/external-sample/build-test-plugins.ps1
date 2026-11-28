# 构建 0.4.5 的两个测试插件（界面全测 / 数据全测），并把 .wplugin 直接放进 app 的 assets/plugins。
# 前置：先在 WristNote-v5 里跑过 :app:assembleRelease（要 out...compileReleaseKotlinclasses 当编译期 classpath）。
# 用法：pwsh -File build-test-plugins.ps1

$ErrorActionPreference = "Stop"

$v5 = "C:\Users\ioo\Desktop\手表笔记\WristNote-v5"
$jdk = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$sdk = "C:\Users\ioo\AppData\Local\Android\Sdk"
$kt = "C:\Users\ioo\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib\2.4.0\4e9f4b531dce5c7dee58ed2de9a8b9a763c8dc6c\kotlin-stdlib-2.4.0.jar"
$classes = "$v5\out\app\intermediates\built_in_kotlinc\release\compileReleaseKotlin\classes"
$d8 = "$sdk\build-tools\36.0.0\d8.bat"
$androidJar = "$sdk\platforms\android-37.0\android.jar"
$assets = "$v5\app\src\main\assets\plugins"

if (-not (Test-Path $classes)) { throw "先构建宿主：$classes 不存在（gradlew :app:assembleRelease）" }

function Build-Plugin([string]$name, [string]$manifest) {
    $root = "C:\Users\ioo\Desktop\手表笔记\WristNote-v5\docs\external-sample\work\$name"
    $src = "$root\src"
    $outClasses = "$root\classes"
    $jarDir = "$root\jar"
    $dexDir = "$root\dex"
    $pkgDir = "$root\pkg"
    foreach ($d in @($outClasses, $jarDir, $dexDir, $pkgDir)) {
        if (Test-Path $d) { Remove-Item $d -Recurse -Force }
        New-Item -ItemType Directory -Path $d | Out-Null
    }
    $sources = Get-ChildItem $src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    if (-not $sources) { throw "$name 没有 Java 源文件" }

    & "$jdk\bin\javac.exe" -encoding UTF-8 -source 17 -target 17 -cp "$classes;$kt" -d $outClasses $sources
    if ($LASTEXITCODE -ne 0) { throw "$name javac 失败" }

    & "$jdk\bin\jar.exe" cf "$jarDir\$name.jar" -C $outClasses .
    if ($LASTEXITCODE -ne 0) { throw "$name jar 失败" }

    & $d8 --release --min-api 26 --lib $androidJar --lib $kt --output $dexDir "$jarDir\$name.jar"
    if ($LASTEXITCODE -ne 0) { throw "$name d8 失败" }

    Copy-Item "$dexDir\classes.dex" "$pkgDir\classes.dex" -Force
    [System.IO.File]::WriteAllText("$pkgDir\plugin.json", $manifest, (New-Object System.Text.UTF8Encoding($false)))

    $zip = "$assets\$name.wplugin"
    if (Test-Path $zip) { Remove-Item $zip -Force }
    [System.IO.Compression.ZipFile]::CreateFromDirectory($pkgDir, $zip, [System.IO.Compression.CompressionLevel]::Optimal, $false)
    Write-Host ("{0}.wplugin  {1} 字节" -f $name, (Get-Item $zip).Length)
}

$manifestUi = @'
{
  "schemaVersion": 1,
  "id": "com.example.testui",
  "name": "界面全测",
  "version": 1,
  "minApi": 5,
  "entryClass": "com.example.testui.TestUiPlugin",
  "permissions": [],
  "author": "腕记",
  "description": "0.4.5 界面能力全测：容器树、16 种官方组件、元素画布、整页画布、点击与拖动。"
}
'@

$manifestData = @'
{
  "schemaVersion": 1,
  "id": "com.example.testdata",
  "name": "数据全测",
  "version": 1,
  "minApi": 5,
  "entryClass": "com.example.testdata.TestDataPlugin",
  "permissions": ["READ_NOTES", "WRITE_NOTES", "READ_SETTINGS", "NET"],
  "author": "腕记",
  "description": "0.4.5 数据能力全测：笔记/标签、kv、事件、前置模块、宿主页面、三个按钮插槽、联网六条拦截路径。"
}
'@

Build-Plugin "testui" $manifestUi
Build-Plugin "testdata" $manifestData
