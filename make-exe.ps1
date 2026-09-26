# СБОРКА ПРИЛОЖЕНИЙ: МАЛЕНЬКИЕ EXE + ПАКИ (26.09.2026).
#
# Прежде (make-exe-old.ps1) каждый из пяти exe был самораспаковывающимся
# архивом на 390 МБ: Java, код, данные и картинки внутри каждого, и любая
# правка перепаковывала всё. Теперь:
#
#   dist\Играть.exe, KeliumReplay2.exe, KeliumHelp.exe, KeliumRunner.exe,
#   KeliumConstructor.exe   — только запускатели (launcher.cs), десятки КБ
#   dist\packs\runtime.pak  — Java-среда (jlink)
#   dist\packs\libs.pak     — сторонние библиотеки
#   dist\packs\code.pak     — код игры: четыре jar
#   dist\packs\rules.pak    — правила, карты, сценарии, справка, книга правил
#   dist\packs\textures.pak — картинки (без сжатия: PNG уже сжаты)
#   dist\packs\packs.txt    — имя, отпечаток и файл каждого пака
#
# ЧТО ПЕРЕСОБИРАЕТСЯ. По умолчанию — только то, у чего изменились исходники:
# у каждого пака свой отпечаток исходников (target\packs\<пак>.fp). Поправил
# код — пересоберётся code.pak, картинки не тронутся. Явно:
#   make-exe.ps1 -Only code            только код
#   make-exe.ps1 -Only rules,textures  данные
#   make-exe.ps1 -Only exe             только запускатели
#   make-exe.ps1 -All                  всё заново
# Запуск: powershell -ExecutionPolicy Bypass -File make-exe.ps1 [-Only ...] [-All]
#
# ВНИМАНИЕ: файл должен быть сохранён в UTF-8 С BOM, иначе PowerShell 5.1
# читает его как ANSI и спотыкается о кириллицу.
param(
    [string[]]$Only = @(),
    [switch]$All
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$dataPath = (Resolve-Path "data").Path
$csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
$state = "target\packs"
$packsOut = "dist\packs"
New-Item -ItemType Directory -Force $state, $packsOut | Out-Null

# Приложения: имя exe, точка входа Java, иконка.
$apps = @(
    @{ Name = "Играть";            Main = "kelium.gui.StartMenuWindow";      Icon = "game";        Title = "игра" },
    @{ Name = "KeliumReplay2";     Main = "kelium.gui.replay2.Replay2Gui";   Icon = "replay2";     Title = "разбор партии" },
    @{ Name = "KeliumHelp";        Main = "kelium.gui.replay2.HelpApp";      Icon = "help";        Title = "справочник" },
    @{ Name = "KeliumRunner";      Main = "kelium.gui.RunnerGui";            Icon = "runner";      Title = "прогоны" },
    @{ Name = "KeliumConstructor"; Main = "kelium.gui.LayoutEditor";         Icon = "constructor"; Title = "конструктор" }
)
# В раздачу не идёт: обучение ботов, архивы, заготовки художника.
$junk = @("training", "selfplay", "genomes-archive-*", "genomes-boi2", "_archive", "tsv")

# ==================== отпечатки исходников ====================

# Отпечаток набора файлов: путь, размер и время правки каждого. Дёшево и надёжно:
# меняется, когда меняется хоть один файл.
function Get-Fingerprint([string[]]$roots, [scriptblock]$filter, [string]$extra = "") {
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine($extra)
    foreach ($r in $roots) {
        if (-not (Test-Path $r)) { continue }
        $items = if ((Get-Item $r) -is [System.IO.DirectoryInfo]) {
            Get-ChildItem -Recurse -File $r
        } else { @(Get-Item $r) }
        foreach ($f in ($items | Where-Object $filter | Sort-Object FullName)) {
            $line = "{0}|{1}|{2}" -f $f.FullName, $f.Length, $f.LastWriteTimeUtc.Ticks
            [void]$sb.AppendLine($line)
        }
    }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($sb.ToString())
    $sha = [System.Security.Cryptography.SHA256]::Create()
    return ([BitConverter]::ToString($sha.ComputeHash($bytes)) -replace "-", "").Substring(0, 16)
}

function Test-Junk([System.IO.FileInfo]$f) {
    $rel = $f.FullName.Substring($dataPath.Length + 1)
    $top = $rel.Split('\')[0]
    foreach ($j in $junk) { if ($top -like $j) { return $true } }
    return $false
}

# версия Java — из свойств файла: `java -version` пишет в поток ошибок, и
# PowerShell с $ErrorActionPreference = Stop счёл бы это падением
$javaExe = (Get-Command java).Source
$javaVer = "$javaExe|" + (Get-Item $javaExe).VersionInfo.ProductVersion
$fp = @{
    code     = Get-Fingerprint @("engine\src\main", "cards\src\main", "bots\src\main", "gui\src\main",
                                 "pom.xml", "engine\pom.xml", "cards\pom.xml", "bots\pom.xml", "gui\pom.xml") { $true }
    libs     = Get-Fingerprint @("pom.xml", "engine\pom.xml", "cards\pom.xml", "bots\pom.xml", "gui\pom.xml") { $true }
    rules    = Get-Fingerprint @("data", "rules") {
                   if ($_.FullName.StartsWith($dataPath)) {
                       -not (Test-Junk $_) -and $_.FullName -notlike "$dataPath\textures\*"
                   } else { $_.Extension -eq ".md" -or $_.FullName -like "*\иконки*" }
               }
    textures = Get-Fingerprint @("data\textures") { $_.FullName -notlike "*\_образцы\*" }
    runtime  = Get-Fingerprint @() { $true } "$javaVer|java.base,java.desktop,java.logging,java.management,jdk.unsupported"
    exe      = Get-Fingerprint @("launcher.cs", "icons") { $true } (($apps | ForEach-Object { $_.Name + $_.Main }) -join ";") + $dataPath
}

$allParts = @("code", "libs", "rules", "textures", "runtime", "exe")
$want = @()
if ($All) {
    $want = $allParts
} elseif ($Only.Count -gt 0) {
    $want = $Only | ForEach-Object { $_.Split(",") } | ForEach-Object { $_.Trim() } | Where-Object { $_ }
    foreach ($w in $want) { if ($allParts -notcontains $w) { throw "нет такой части: $w (есть: $($allParts -join ', '))" } }
} else {
    foreach ($p in $allParts) {
        $old = if (Test-Path "$state\$p.fp") { (Get-Content "$state\$p.fp" -Raw).Trim() } else { "" }
        $pak = if ($p -eq "exe") { "dist\Играть.exe" } else { "$packsOut\$p.pak" }
        if ($old -ne $fp[$p] -or -not (Test-Path $pak)) { $want += $p }
    }
}
if ($want.Count -eq 0) {
    Write-Output "Ничего не изменилось — пересобирать нечего. (-All — пересобрать всё.)"
    exit 0
}
Write-Output ("Пересобираю: " + ($want -join ", "))

# ==================== пак: zip без лишнего ====================

# Пак — обычный zip. Картинки и jar уже сжаты — кладутся без сжатия (распаковка
# тогда — простое копирование); текст и Java-среда — со сжатием.
function New-Pak([string]$srcDir, [string]$pak, [bool]$store) {
    $tmp = "$pak.tmp"
    if (Test-Path $tmp) { Remove-Item -Force $tmp }
    $level = if ($store) { [System.IO.Compression.CompressionLevel]::NoCompression }
             else { [System.IO.Compression.CompressionLevel]::Optimal }
    $root = (Resolve-Path $srcDir).Path
    $zip = [System.IO.Compression.ZipFile]::Open((Join-Path $PSScriptRoot $tmp),
        [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($f in Get-ChildItem -Recurse -File $root) {
            $name = $f.FullName.Substring($root.Length + 1).Replace('\', '/')
            [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $f.FullName, $name, $level)
        }
    } finally {
        $zip.Dispose()
    }
    if (Test-Path $pak) { Remove-Item -Force $pak }
    Move-Item $tmp $pak
    $sha = (Get-FileHash $pak -Algorithm SHA256).Hash.Substring(0, 12).ToLower()
    Set-Content -Encoding ascii "$pak.sha" $sha
    "   {0} — {1:N1} МБ, отпечаток {2}" -f (Split-Path $pak -Leaf), ((Get-Item $pak).Length / 1MB), $sha | Write-Output
}

function Reset-Dir([string]$d) {
    if (Test-Path $d) { Remove-Item -Recurse -Force $d }
    New-Item -ItemType Directory -Force $d | Out-Null
}

# ==================== код и библиотеки ====================

if ($want -contains "code" -or $want -contains "libs") {
    Write-Output "код: mvn package…"
    mvn -q package -DskipTests
    # СБОРКА ОБЯЗАНА ПАДАТЬ ГРОМКО: иначе пак собрался бы из прошлых классов.
    if ($LASTEXITCODE -ne 0) {
        Write-Host "СБОРКА НЕ ПРОШЛА: mvn package вернул $LASTEXITCODE. Паки не тронуты." -ForegroundColor Red
        exit 1
    }
}
if ($want -contains "code") {
    Reset-Dir "target\stage\code"
    foreach ($m in @("engine", "cards", "bots", "gui")) {
        Copy-Item "$m\target\kelium-$m-0.1.0.jar" "target\stage\code\"
    }
    New-Pak "target\stage\code" "$packsOut\code.pak" $true
}
if ($want -contains "libs") {
    Reset-Dir "target\pkg-libs"
    mvn -q -pl gui dependency:copy-dependencies "-DoutputDirectory=$PSScriptRoot\target\pkg-libs"
    Reset-Dir "target\stage\libs"
    Copy-Item target\pkg-libs\*.jar target\stage\libs\ -Exclude junit*, apiguardian*, opentest4j*, kelium-*, onnxruntime-*
    # ONNX Runtime — с нативами под все платформы и 290 МБ отладочных символов:
    # оставляем классы и только win-x64 .dll.
    $onnx = Get-ChildItem target\pkg-libs\onnxruntime-*.jar | Select-Object -First 1
    if ($onnx) {
        $slim = Join-Path $PSScriptRoot "target\stage\libs\onnxruntime-win-x64.jar"
        $src = [System.IO.Compression.ZipFile]::OpenRead($onnx.FullName)
        $dst = [System.IO.Compression.ZipFile]::Open($slim, [System.IO.Compression.ZipArchiveMode]::Create)
        foreach ($e in $src.Entries) {
            if ($e.FullName.EndsWith("/") -or $e.FullName -like "*.pdb") { continue }
            if ($e.FullName -like "ai/onnxruntime/native/*" -and
                $e.FullName -notlike "ai/onnxruntime/native/win-x64/*") { continue }
            if ($e.FullName -like "META-INF/*.SF" -or $e.FullName -like "META-INF/*.RSA" -or
                $e.FullName -like "META-INF/*.DSA") { continue }
            $ne = $dst.CreateEntry($e.FullName, [System.IO.Compression.CompressionLevel]::Optimal)
            $rs = $e.Open(); $ws = $ne.Open(); $rs.CopyTo($ws); $ws.Close(); $rs.Close()
        }
        $dst.Dispose(); $src.Dispose()
    }
    New-Pak "target\stage\libs" "$packsOut\libs.pak" $true
}

# ==================== Java-среда ====================

if ($want -contains "runtime") {
    Write-Output "Java-среда: jlink…"
    if (Test-Path target\runtime-slim) { Remove-Item -Recurse -Force target\runtime-slim }
    jlink --add-modules java.base,java.desktop,java.logging,java.management,jdk.unsupported `
          --strip-debug --no-header-files --no-man-pages --compress=zip-9 `
          --output target\runtime-slim
    if ($LASTEXITCODE -ne 0) { throw "jlink не прошёл" }
    New-Pak "target\runtime-slim" "$packsOut\runtime.pak" $false
}

# ==================== данные ====================

if ($want -contains "rules") {
    Write-Output "данные: правила, карты, сценарии, книга правил…"
    Reset-Dir "target\stage\rules"
    New-Item -ItemType Directory -Force "target\stage\rules\data" | Out-Null
    foreach ($d in Get-ChildItem "data") {
        if ($d.Name -eq "textures") { continue }
        $skip = $false
        foreach ($j in $junk) { if ($d.Name -like $j) { $skip = $true } }
        if ($skip) { continue }
        Copy-Item -Recurse -Force $d.FullName "target\stage\rules\data\"
    }
    # книга правил — рядом с data: справочник ищет <data>\..\rules\Книга правил…
    $bookSrc = Get-ChildItem -Path "rules" -Directory -Filter "Книга правил*" | Select-Object -First 1
    if ($bookSrc) {
        $bookDst = "target\stage\rules\rules\$($bookSrc.Name)"
        New-Item -ItemType Directory -Force $bookDst | Out-Null
        Get-ChildItem -Path $bookSrc.FullName -Filter "*.md" | Copy-Item -Destination $bookDst -Force
        foreach ($icons in @("иконки-экспорт", "иконки")) {
            if (Test-Path "rules\$icons") {
                Copy-Item -Recurse -Force "rules\$icons" "target\stage\rules\rules\$icons"
            }
        }
    }
    New-Pak "target\stage\rules" "$packsOut\rules.pak" $false
}

if ($want -contains "textures") {
    Write-Output "картинки…"
    Reset-Dir "target\stage\textures"
    Copy-Item -Recurse -Force "data\textures\*" "target\stage\textures\"
    Get-ChildItem -Path "target\stage\textures" -Filter "_образцы" -Directory -ErrorAction SilentlyContinue |
        Remove-Item -Recurse -Force
    New-Pak "target\stage\textures" "$packsOut\textures.pak" $true
}

# ==================== список паков ====================

$lines = @("# имя`tотпечаток`tфайл — читает запускатель; пишет make-exe.ps1")
foreach ($p in @("runtime", "libs", "code", "rules", "textures")) {
    $pak = "$packsOut\$p.pak"
    if (-not (Test-Path $pak)) { throw "нет пака $pak — запусти с -All" }
    if (-not (Test-Path "$pak.sha")) {
        Set-Content -Encoding ascii "$pak.sha" (Get-FileHash $pak -Algorithm SHA256).Hash.Substring(0, 12).ToLower()
    }
    $lines += "$p`t$((Get-Content "$pak.sha" -Raw).Trim())`t$p.pak"
}
[System.IO.File]::WriteAllLines((Join-Path $PSScriptRoot "$packsOut\packs.txt"), $lines,
    (New-Object System.Text.UTF8Encoding $false))

# ==================== запускатели ====================

# Иконка приложения: .ico из квадратного PNG (ICO с Vista несёт PNG прямо в
# записи). Несколько размеров, иначе Проводник мылит мелкие виды.
function Convert-PngToIco([string]$png, [string]$ico) {
    if (-not (Test-Path $png)) { return $null }
    Add-Type -AssemblyName System.Drawing
    $src = [System.Drawing.Image]::FromFile((Resolve-Path $png).Path)
    try {
        $frames = @()
        foreach ($size in @(256, 48, 32, 16)) {
            $bmp = New-Object System.Drawing.Bitmap $size, $size
            $g = [System.Drawing.Graphics]::FromImage($bmp)
            $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $g.Clear([System.Drawing.Color]::Transparent)
            $g.DrawImage($src, 0, 0, $size, $size)
            $g.Dispose()
            $ms = New-Object System.IO.MemoryStream
            $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
            $bmp.Dispose()
            $frames += , @{ Size = $size; Bytes = $ms.ToArray() }
            $ms.Dispose()
        }
        $out = New-Object System.IO.MemoryStream
        $w = New-Object System.IO.BinaryWriter $out
        $w.Write([uint16]0); $w.Write([uint16]1); $w.Write([uint16]$frames.Count)
        $offset = 6 + 16 * $frames.Count
        foreach ($f in $frames) {
            $dim = if ($f.Size -ge 256) { 0 } else { $f.Size }
            $w.Write([byte]$dim); $w.Write([byte]$dim)
            $w.Write([byte]0); $w.Write([byte]0)
            $w.Write([uint16]1); $w.Write([uint16]32)
            $w.Write([uint32]$f.Bytes.Length); $w.Write([uint32]$offset)
            $offset += $f.Bytes.Length
        }
        foreach ($f in $frames) { $w.Write($f.Bytes) }
        $w.Flush()
        [System.IO.File]::WriteAllBytes((Join-Path $PSScriptRoot $ico), $out.ToArray())
        $w.Dispose(); $out.Dispose()
        return $ico
    } finally {
        $src.Dispose()
    }
}

# Свежий exe держат антивирус или Яндекс.Диск — удаляем с повторами.
function Remove-FileStubborn([string]$path) {
    if (-not (Test-Path $path)) { return }
    for ($i = 0; $i -lt 20; $i++) {
        try { Remove-Item -Force $path; return } catch { Start-Sleep -Milliseconds 500 }
    }
    throw "Файл занят другим процессом: $path"
}

if ($want -contains "exe") {
    Write-Output "запускатели…"
    $stub = Get-Content launcher.cs -Raw -Encoding UTF8
    $refs = "/r:System.dll /r:System.Drawing.dll /r:System.IO.Compression.dll " +
            "/r:System.IO.Compression.FileSystem.dll /r:System.Windows.Forms.dll"
    foreach ($app in $apps) {
        $src = "target\launcher_$($app.Icon).cs"
        $stub.Replace("@MAIN@", $app.Main).Replace("@APP@", $app.Title).Replace("@DEVDATA@", $dataPath) |
            Out-File -Encoding UTF8 $src
        $exe = "dist\$($app.Name).exe"
        Remove-FileStubborn $exe
        $iconArg = ""
        $ico = Convert-PngToIco "icons\$($app.Icon).png" "target\$($app.Icon).ico"
        if ($ico) { $iconArg = "/win32icon:$ico " }
        $cmd = "& `"$csc`" /nologo /target:winexe /platform:anycpu /optimize+ " +
               "$iconArg/out:`"$exe`" $refs $src"
        Invoke-Expression $cmd
        if (-not (Test-Path $exe)) { throw "не собрался $exe" }
    }
}

# Отпечатки — только после успешной сборки: упавшая часть пересоберётся в следующий раз.
foreach ($p in $want) { Set-Content -Encoding ascii "$state\$p.fp" $fp[$p] }

# Прежние «толстые» сборки больше не нужны.
foreach ($old in @("dist\app", "target\payload.zip")) {
    if (Test-Path $old) { Remove-Item -Recurse -Force $old -ErrorAction SilentlyContinue }
}
Get-ChildItem "dist\*.занят-*.exe" -ErrorAction SilentlyContinue | ForEach-Object {
    try { Remove-Item -Force $_.FullName } catch { }
}

Write-Output ""
Write-Output "ГОТОВО. Раздача — папка dist целиком (exe + packs):"
foreach ($app in $apps) {
    $f = Get-Item "dist\$($app.Name).exe" -ErrorAction SilentlyContinue
    if ($f) { "   {0,-24} {1,8:N0} КБ   {2}" -f $f.Name, ($f.Length / 1KB), $app.Title | Write-Output }
}
foreach ($p in Get-ChildItem "$packsOut\*.pak") {
    "   packs\{0,-17} {1,8:N1} МБ" -f $p.Name, ($p.Length / 1MB) | Write-Output
}
Write-Output ""
Write-Output "Первый запуск на машине распаковывает паки в %LOCALAPPDATA%\Kelium (один раз;"
Write-Output "дальше — только изменившиеся). На машине сборки игра берёт живые данные проекта"
Write-Output "($dataPath); KELIUM_PACKS=1 заставляет брать паки и здесь."
