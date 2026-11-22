# 把 docs/external-sample/HelloPlugin.java 打成 assets/plugins/hello.wplugin
# 用法：pwsh -File build-sample.ps1   （需要在 WristNote-v5 目录下跑）
$ErrorActionPreference = "Stop"
$v5 = "C:\Users\ioo\Desktop\手表笔记\WristNote-v5"
$work = Join-Path $v5 "build-external-sample"
$classes = Join-Path $v5 "out\app\intermediates\built_in_kotlinc\release\compileReleaseKotlin\classes"
$jdk = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
$sdk = "C:\Users\ioo\AppData\Local\Android\Sdk"
$kt = "C:\Users\ioo\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib\2.4.0\4e9f4b531dce5c7dee58ed2de9a8b9a763c8dc6c\kotlin-stdlib-2.4.0.jar"

Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$work\classes", "$work\jar", "$work\dex", "$work\pkg" | Out-Null
Copy-Item (Join-Path $v5 "docs\external-sample\HelloPlugin.java") "$work\HelloPlugin.java"

& "$jdk\bin\javac.exe" -encoding UTF-8 -source 17 -target 17 -cp "$classes;$kt" -d "$work\classes" "$work\HelloPlugin.java"
& "$jdk\bin\jar.exe" cf "$work\jar\hello.jar" -C "$work\classes" .
& "$sdk\build-tools\36.0.0\d8.bat" --release --min-api 26 --lib "$sdk\platforms\android-37.0\android.jar" --lib "$kt" --output "$work\dex" "$work\jar\hello.jar"

Copy-Item "$work\dex\classes.dex" "$work\pkg\classes.dex"
$manifest = @'
{
  "schemaVersion": 1,
  "id": "com.example.hello",
  "name": "示例插件",
  "version": 1,
  "minApi": 1,
  "entryClass": "com.example.hello.HelloPlugin",
  "permissions": [],
  "author": "腕记",
  "description": "外置插件最小样例：设置页多一行按钮，点一下弹提示。"
}
'@
[System.IO.File]::WriteAllText("$work\pkg\plugin.json", $manifest, (New-Object System.Text.UTF8Encoding($false)))

$assets = Join-Path $v5 "app\src\main\assets\plugins"
New-Item -ItemType Directory -Force -Path $assets | Out-Null
$zipPath = Join-Path $assets "hello.wplugin"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory("$work\pkg", $zipPath, [System.IO.Compression.CompressionLevel]::Optimal, $false)

Add-Type -AssemblyName System.IO.Compression
$z = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
$z.Entries | ForEach-Object { "{0}  {1} bytes" -f $_.FullName, $_.Length }
$z.Dispose()
"OK -> $zipPath (" + (Get-Item $zipPath).Length + " bytes)"
