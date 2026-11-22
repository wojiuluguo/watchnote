# 给外置插件包 .wplugin 加作者签名（v2.2 / 4.1.14 起）
# 用法（在 WristNote-v5 目录下）：
#   pwsh -File docs\external-sample\sign-sample.ps1 -Package 你的插件.wplugin -NewKey
#   pwsh -File docs\external-sample\sign-sample.ps1 -Package 你的插件.wplugin        # 用已有私钥
# 第一次加 -NewKey 会在包旁边生成 keys\plugin-private.pem，之后一直用它。
# 私钥千万别外传、别提交到仓库；公钥会写进包里的 plugin.json。
param(
    [Parameter(Mandatory = $true)][string]$Package,
    [string]$KeyDir = "",
    [string]$PrivateKey = "",
    [switch]$NewKey
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path $Package)) { throw "找不到包：$Package" }
$Package = (Resolve-Path $Package).Path
if ($KeyDir -eq "") { $KeyDir = Join-Path (Split-Path -Parent $Package) "keys" }
New-Item -ItemType Directory -Force -Path $KeyDir | Out-Null
if ($PrivateKey -eq "") { $PrivateKey = Join-Path $KeyDir "plugin-private.pem" }
$pubPath = [System.IO.Path]::ChangeExtension($PrivateKey, ".public.spki.b64")

# 1. 私钥：有就用，没有就只在 -NewKey 时生成
if (Test-Path $PrivateKey) {
    $ec = [System.Security.Cryptography.ECDsa]::Create()
    $ec.ImportFromPem((Get-Content -Raw $PrivateKey))
    "用已有私钥：$PrivateKey"
} else {
    if (-not $NewKey) { throw "没有私钥：$PrivateKey（第一次签名请加 -NewKey）" }
    $ec = [System.Security.Cryptography.ECDsa]::Create([System.Security.Cryptography.ECCurve+NamedCurves]::nistP256)
    [System.IO.File]::WriteAllText($PrivateKey, $ec.ExportPkcs8PrivateKeyPem())
    "新生成私钥：$PrivateKey（不要外传）"
}
$spki = $ec.ExportSubjectPublicKeyInfo()
$pubB64 = [Convert]::ToBase64String($spki)
[System.IO.File]::WriteAllText($pubPath, $pubB64 + [Environment]::NewLine)
$fpBytes = [System.Security.Cryptography.SHA256]::HashData($spki)
$fp = -join ($fpBytes[0..7] | ForEach-Object { $_.ToString("X2") })
"公钥指纹：$fp"

# 2. 读包里所有文件（先不读旧 plugin.sig，等会儿整包重写）
$zip = [System.IO.Compression.ZipFile]::OpenRead($Package)
$files = @{}
$manifestText = $null
foreach ($e in $zip.Entries) {
    # 目录条目（名字以 / 结尾，或是空 Name）不算文件：宿主验签时也跳过它们，
    # 这里必须同样跳过，否则带 assets/ 这类目录的包签名对不上。
    if ([string]::IsNullOrEmpty($e.Name)) { continue }
    $name = $e.FullName.Replace('\', '/')
    if ($name -eq "plugin.sig") { continue }
    if ($e.Length -gt 8388608) { $zip.Dispose(); throw "文件太大：$name" }
    $ms = New-Object System.IO.MemoryStream
    $s = $e.Open()
    $s.CopyTo($ms)
    $s.Dispose()
    $bytes = $ms.ToArray()
    $ms.Dispose()
    if ($name -eq "plugin.json") {
        $manifestText = [System.Text.Encoding]::UTF8.GetString($bytes).TrimStart([char]0xFEFF)
    }
    $files[$name] = $bytes
}
$zip.Dispose()
if ($null -eq $manifestText) { throw "包里没有 plugin.json" }

# 3. 把公钥写进 plugin.json（清单变了要重新算摘要，所以顺序是先改清单再算）
$manifest = $manifestText | ConvertFrom-Json
$manifest | Add-Member -NotePropertyName publicKey -NotePropertyValue $pubB64 -Force
$manifest | Add-Member -NotePropertyName signingAlgorithm -NotePropertyValue "SHA256withECDSA" -Force
$manifestBytes = [System.Text.Encoding]::UTF8.GetBytes(($manifest | ConvertTo-Json -Depth 8))
$files["plugin.json"] = $manifestBytes

# 4. 拼签名原文：第一行固定标识，之后每行「sha256 字节数 包内路径」，按路径字节序升序，行尾 LF
# 排序必须和宿主一致：按路径的 UTF-8 字节序（无符号逐字节比较），不是当前区域排序。
# 做法是把每个字节写成两位十六进制，对定长十六进制串按序数排序，等价于逐字节比较。
$keyMap = @{}
foreach ($p in $files.Keys) {
    $hexKey = -join ([System.Text.Encoding]::UTF8.GetBytes($p) | ForEach-Object { $_.ToString("x2") })
    $keyMap[$hexKey] = $p
}
$hexKeys = [string[]]@($keyMap.Keys)
[Array]::Sort($hexKeys, [System.StringComparer]::Ordinal)
$arr = [string[]]@(foreach ($k in $hexKeys) { $keyMap[$k] })
$sb = New-Object System.Text.StringBuilder
[void]$sb.Append("WRISTNOTE-PLUGIN-SIGNATURE-V1").Append([char]10)
foreach ($p in $arr) {
    $h = -join ([System.Security.Cryptography.SHA256]::HashData($files[$p]) | ForEach-Object { $_.ToString("x2") })
    [void]$sb.Append($h).Append(" ").Append($files[$p].Length).Append(" ").Append($p).Append([char]10)
}
$payload = $sb.ToString()

# 5. ECDSA P-256 / SHA-256，DER 签名，Base64 后放进 plugin.sig
$sig = $ec.SignData(
    [System.Text.Encoding]::UTF8.GetBytes($payload),
    [System.Security.Cryptography.HashAlgorithmName]::SHA256,
    [System.Security.Cryptography.DSASignatureFormat]::Rfc3279DerSequence
)
$sigB64 = [Convert]::ToBase64String($sig)
if ($sigB64.Length -gt 4096) { throw "签名太长：$($sigB64.Length)" }

# 6. 整包重写：先写所有普通文件，plugin.sig 放最后
$tmp = $Package + ".signed"
if (Test-Path $tmp) { Remove-Item $tmp -Force }
$out = [System.IO.Compression.ZipFile]::Open($tmp, [System.IO.Compression.ZipArchiveMode]::Create)
foreach ($p in $arr) {
    $entry = $out.CreateEntry($p, [System.IO.Compression.CompressionLevel]::Optimal)
    $st = $entry.Open()
    $st.Write($files[$p], 0, $files[$p].Length)
    $st.Dispose()
}
$sigBytes = (New-Object System.Text.UTF8Encoding($false)).GetBytes($sigB64)
$se = $out.CreateEntry("plugin.sig", [System.IO.Compression.CompressionLevel]::Optimal)
$st = $se.Open()
$st.Write($sigBytes, 0, $sigBytes.Length)
$st.Dispose()
$out.Dispose()
Move-Item -Force $tmp $Package

"已签名：$Package（" + (Get-Item $Package).Length + " 字节）"
"签名长度：" + $sigB64.Length + " 字符；公钥已写进包内 plugin.json"
"安装预览里应显示「签名：已签名 · $fp」"
"私钥：$PrivateKey（别传）；公钥备份：$pubPath"
