# Сборка ОДНОФАЙЛОВЫХ exe (самораспаковка во временную папку):
#   dist\KeliumConstructor.exe — конструктор раскладок
#   dist\KeliumRunner.exe      — прогоны симуляций
#   dist\KeliumReplay2.exe     — разбор партии
#   dist\KeliumHelp.exe        — СПРАВОЧНИК отдельно (правила и все карты)
#   dist\Играть.exe            — САМА ИГРА: «Штаб» и партия
#   (проигрыватель 1.0 заархивирован 13.08.2026 — см. archive/replay-1.0/)
# Внутрь каждого зашито всё приложение вместе с урезанной Java-средой.
# Запуск: powershell -ExecutionPolicy Bypass -File make-exe.ps1
#
# ВНИМАНИЕ: файл должен быть сохранён в UTF-8 С BOM, иначе PowerShell 5.1
# читает его как ANSI и спотыкается о кириллицу.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# каталог данных игры (правила, карты, раскладки, модели) — только ПРЯМЫЕ слэши
$dataPath = (Resolve-Path "data").Path -replace '\\', '/'
$csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"

# ЗАПУЩЕННОЕ ПРИЛОЖЕНИЕ БОЛЬШЕ НЕ МЕШАЕТ СБОРКЕ (правка 08.09.2026).
#
# Раньше сборка отказывалась работать, пока открыто хоть одно окно: Windows
# держит запущенный exe и перезаписать его нельзя. На деле это значило «закрой
# то, что смотришь, и жди пять минут» — а смотрят как раз затем, чтобы сказать,
# что поправить.
#
# Занятый файл ПЕРЕИМЕНОВЫВАЕТСЯ: открытое приложение продолжает работать из
# переименованного файла (Windows это разрешает — дескриптор остаётся у файла, а
# не у имени), а сборка спокойно кладёт на его место новый. Старые «занятые»
# копии подчищаются при следующем запуске, когда их уже никто не держит.
$busy = Get-Process | Where-Object { $_.Name -like "Kelium*" }
Get-ChildItem "dist\*.занят-*.exe" -ErrorAction SilentlyContinue | ForEach-Object {
    try { Remove-Item -Force $_.FullName } catch { }
}
if ($busy) {
    $имена = ($busy | Select-Object -ExpandProperty Path -Unique) |
        Where-Object { $_ -and (Test-Path $_) }
    foreach ($путь in $имена) {
        $новое = [System.IO.Path]::GetFileNameWithoutExtension($путь) +
            ".занят-" + (Get-Date -Format "HHmmss") + ".exe"
        try {
            Rename-Item -LiteralPath $путь -NewName $новое -Force
            Write-Output "   открытое приложение отодвинуто: $новое"
        } catch {
            throw "Приложение $путь запущено, и отодвинуть файл не удалось: $_`nЗакрой окно и запусти сборку снова."
        }
    }
}

# Свежесозданный крупный exe часто ещё держат антивирус или синхронизация
# Яндекс.Диска — удаляем с повторами, а не падаем с первой попытки.
function Remove-FileStubborn([string]$path) {
    if (-not (Test-Path $path)) { return }
    for ($i = 0; $i -lt 20; $i++) {
        try { Remove-Item -Force $path; return }
        catch { Start-Sleep -Milliseconds 500 }
    }
    throw "Файл занят другим процессом: $path`nСкорее всего его сканирует антивирус или синхронизирует Яндекс.Диск. Подожди несколько секунд и запусти сборку снова."
}

# Иконка приложения: делаем .ico из квадратного PNG.
#
# Формат ICO с Vista умеет нести PNG прямо в записи, поэтому картинку не надо
# перерисовывать — достаточно приписать 22-байтовую шапку. Так иконка остаётся
# ровно той, что нарисовал дизайнер, без потери качества и без сторонних утилит.
# Кладём в одну иконку несколько размеров: 256 (исходник), 48, 32 и 16 — иначе
# Проводник в мелких видах масштабирует 256-й и получается мыло.
function Convert-PngToIco([string]$png, [string]$ico) {
    if (-not (Test-Path $png)) { return $null }
    Add-Type -AssemblyName System.Drawing
    $src = [System.Drawing.Image]::FromFile((Resolve-Path $png).Path)
    try {
        $frames = @()
        foreach ($size in @(256, 48, 32, 16)) {
            $bmp = New-Object System.Drawing.Bitmap $size, $size
            $g = [System.Drawing.Graphics]::FromImage($bmp)
            $g.InterpolationMode =
                [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
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
        New-Item -ItemType Directory -Force (Split-Path $ico) | Out-Null
        [System.IO.File]::WriteAllBytes((Join-Path $PSScriptRoot $ico), $out.ToArray())
        $w.Dispose(); $out.Dispose()
        return $ico
    } finally {
        $src.Dispose()
    }
}

Write-Output "1/6 сборка jar…"
# Три модуля вместо одного (разделено 14.08.2026): корень — реестр-агрегатор
# (packaging=pom), реальные jar лежат в engine\target, bots\target, gui\target.
# `mvn package` из корня собирает все три по реактору в правильном порядке.
mvn -q package -DskipTests
# СБОРКА ОБЯЗАНА ПАДАТЬ ГРОМКО. Без этой проверки провалившийся mvn проходил
# мимо: exe собирался из ПРОШЛЫХ классов, и наружу уходил движок одной версии
# с окнами другой. Именно так 11.09.2026 уехала сборка, где кнопка «игроки…»
# падала с NoSuchMethodError.
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "СБОРКА НЕ ПРОШЛА: mvn package вернул $LASTEXITCODE." -ForegroundColor Red
    Write-Host "Exe не пересобраны — в dist лежит прежнее." -ForegroundColor Red
    exit 1
}
# Копировать зависимости надо от модуля gui — только у него полный
# ТРАНЗИТИВНЫЙ набор (движок + боты + все сторонние библиотеки). Собственные
# kelium-* jar сюда тоже попадут — они НЕ лишние: pkgin ниже наполняется
# per-module jar-ами вручную, а из pkg-libs берутся именно сторонние.
mvn -q -pl gui dependency:copy-dependencies "-DoutputDirectory=$PSScriptRoot\target\pkg-libs"

New-Item -ItemType Directory -Force target\pkgin | Out-Null
Remove-Item target\pkgin\*.jar -ErrorAction SilentlyContinue
# Классы приложения — из ВСЕХ ТРЁХ модулей (jpackage строит classpath из
# каждого jar в --input, отдельным файлом на модуль, а не одним fat-jar).
# МОДУЛЕЙ ЧЕТЫРЕ, А НЕ ТРИ (разделено 14.08.2026, cards добавлен позже):
# kelium-cards сюда не копировался, а из pkg-libs его вычищал -Exclude kelium-*,
# и в собранное приложение он не попадал вовсе — карты не грузились.
Copy-Item engine\target\kelium-engine-0.1.0.jar target\pkgin\
Copy-Item cards\target\kelium-cards-0.1.0.jar target\pkgin\
Copy-Item bots\target\kelium-bots-0.1.0.jar target\pkgin\
Copy-Item gui\target\kelium-gui-0.1.0.jar target\pkgin\
Copy-Item target\pkg-libs\*.jar target\pkgin\ -Exclude junit*, apiguardian*, opentest4j*, kelium-*

# ONNX Runtime поставляется с нативами под ВСЕ платформы и с 290 МБ отладочных
# символов. Для Windows-сборки оставляем классы и только win-x64 .dll — иначе
# 89 МБ библиотеки перевешивают всё приложение.
Write-Output "1-бис/6 обрезка ONNX до Windows…"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$onnx = Get-ChildItem target\pkgin\onnxruntime-*.jar | Select-Object -First 1
if ($onnx) {
    $slim = Join-Path $PSScriptRoot "target\onnx-win.jar"
    if (Test-Path $slim) { Remove-Item -Force $slim }
    $src = [System.IO.Compression.ZipFile]::OpenRead($onnx.FullName)
    $dst = [System.IO.Compression.ZipFile]::Open($slim, [System.IO.Compression.ZipArchiveMode]::Create)
    foreach ($e in $src.Entries) {
        if ($e.FullName.EndsWith("/")) { continue }
        $keep = $false
        if ($e.FullName -like "*.pdb") { $keep = $false }
        elseif ($e.FullName -like "ai/onnxruntime/native/*") {
            $keep = $e.FullName -like "ai/onnxruntime/native/win-x64/*"
        }
        elseif ($e.FullName -like "META-INF/*.SF" -or $e.FullName -like "META-INF/*.RSA" -or
                $e.FullName -like "META-INF/*.DSA") { $keep = $false }
        else { $keep = $true }
        if (-not $keep) { continue }
        $ne = $dst.CreateEntry($e.FullName, [System.IO.Compression.CompressionLevel]::Optimal)
        $rs = $e.Open(); $ws = $ne.Open(); $rs.CopyTo($ws); $ws.Close(); $rs.Close()
    }
    $dst.Dispose(); $src.Dispose()
    $wasMb = $onnx.Length / 1MB
    Remove-Item $onnx.FullName -Force
    Copy-Item $slim (Join-Path $PSScriptRoot "target\pkgin\onnxruntime-win-x64.jar")
    "   ONNX: {0:N1} МБ -> {1:N1} МБ" -f $wasMb, ((Get-Item $slim).Length / 1MB) | Write-Output
}

Write-Output "2/6 урезанная Java-среда (jlink)…"
if (Test-Path target\runtime-slim) { Remove-Item -Recurse -Force target\runtime-slim }
# jdeps показал java.base + java.desktop + java.logging; management и unsupported
# добавлены с запасом под сторонние библиотеки (ONNX Runtime).
jlink --add-modules java.base,java.desktop,java.logging,java.management,jdk.unsupported `
      --strip-debug --no-header-files --no-man-pages --compress=zip-9 `
      --output target\runtime-slim

Write-Output "3/6 jpackage (образ приложения)…"
$props = @()
$props += "main-class=kelium.gui.RunnerGui"
$props += "java-options=-Dkelium.data=`"$dataPath`""
$props += "win-console=false"
$props | Out-File -Encoding ascii target\runner-launcher.properties

# ПРОИГРЫВАТЕЛЬ 1.0 БОЛЬШЕ НЕ СОБИРАЕТСЯ (решение дизайнера 13.08.2026): версия
# устарела, её заменил разбор партии 2.0. Исходник убран из сборки в
# archive/replay-1.0/, лончер и exe для неё не делаются. Общие панели (планшеты
# науки и рынка, супер-задания) остались в проекте — их использует 2.0.

# РАЗБОР ПАРТИИ 2.0 — единственный проигрыватель.
$replay2Props = @()
$replay2Props += "main-class=kelium.gui.replay2.Replay2Gui"
$replay2Props += "java-options=-Dkelium.data=`"$dataPath`""
$replay2Props += "win-console=false"
$replay2Props | Out-File -Encoding ascii target\replay2-launcher.properties

# СПРАВОЧНИК ОТДЕЛЬНО. Дизайнер читает правила и каталог карт, не собираясь
# смотреть партию, — ради этого не нужно поднимать весь проигрыватель
# (просьба 13.08.2026). Данные те же: правила и карточные наборы из data.
$helpProps = @()
$helpProps += "main-class=kelium.gui.replay2.HelpApp"
$helpProps += "java-options=-Dkelium.data=`"$dataPath`""
$helpProps += "win-console=false"
$helpProps | Out-File -Encoding ascii target\help-launcher.properties

# САМА ИГРА (просьба дизайнера 27.08.2026). Точка входа — «Штаб»: собрать
# стол и сесть играть. Отдельным лончером, а не заменой RunnerGui: раннер
# прогонов — инструмент дизайнера, и подменять его игрой нельзя.
$gameProps = @()
$gameProps += "main-class=kelium.gui.StartMenuWindow"
$gameProps += "java-options=-Dkelium.data=`"$dataPath`""
$gameProps += "win-console=false"
$gameProps | Out-File -Encoding ascii target\game-launcher.properties

# KeliumBuilder В СБОРКУ НЕ ВХОДИТ (19.08.2026).
#
# Он затевался как «перенос конструктора на новый движок рендера», но проверка
# показала, что переносить нечего: LayoutEditor УЖЕ считает геометрию через
# kelium.report.FieldGeometry — те же hexCenter и TILT, что у разбора партии
# (см. LayoutEditor.center и LayoutEditor.hexPoly). Движок у старого
# конструктора и так новый.
#
# Поэтому отдельное приложение давало только потери: не было вкладок, журнала
# проверок, настроек экспорта, кнопки темы, нейтралов и сборки из блоков — всё
# это в LayoutEditor есть. Собирать и отдавать дизайнеру заведомо более бедный
# инструмент вредно, поэтому exe из него не делается.
#
# Исходник оставлен в дереве (gui/.../KeliumBuilder.java, BuilderScene.java) как
# заготовка: если однажды понадобится ДРУГОЙ конструктор — не копия старого, а
# инструмент с иным набором задач, — начинать будет с чего. Правки внешнего вида
# (скругления, вид ячеек) идут в LayoutEditor, а не сюда.

if (Test-Path dist\app) { Remove-Item -Recurse -Force dist\app }
New-Item -ItemType Directory -Force dist | Out-Null
jpackage --type app-image --name Kelium --input target\pkgin --runtime-image target\runtime-slim `
  --main-jar kelium-gui-0.1.0.jar --main-class kelium.gui.LayoutEditor `
  --add-launcher KeliumRunner=target\runner-launcher.properties `
  --add-launcher KeliumReplay2=target\replay2-launcher.properties `
  --add-launcher KeliumHelp=target\help-launcher.properties `
  --add-launcher KeliumGame=target\game-launcher.properties --dest dist\app

# jpackage режет --java-options по пробелам в пути, поэтому секцию [JavaOptions]
# главного лончера собираем сами (у add-launcher она берётся из properties и цела).
$cfgPath = "dist\app\Kelium\app\Kelium.cfg"
$lines = Get-Content $cfgPath
$out = @()
$inJava = $false
foreach ($l in $lines) {
    if ($l.Trim() -eq "[JavaOptions]") {
        $out += $l
        $out += "java-options=-Djpackage.app-version=1.0"
        $out += "java-options=-Dkelium.data=$dataPath"
        $inJava = $true
        continue
    }
    if ($inJava) {
        if ($l -like "java-options=*") { continue }
        $inJava = $false
    }
    $out += $l
}
if (-not ($out -join "`n").Contains("[JavaOptions]")) {
    $out += ""
    $out += "[JavaOptions]"
    $out += "java-options=-Dkelium.data=$dataPath"
}
Set-Content $cfgPath $out -Encoding ascii
if (-not (Select-String -Path $cfgPath -Pattern ([regex]::Escape($dataPath)) -Quiet)) {
    throw "cfg главного лончера не содержит kelium.data — проверь патч"
}

# ДАННЫЕ ИГРЫ — ВНУТРЬ ПРИЛОЖЕНИЯ (08.09.2026).
#
# Прежде в exe прибивался только ПУТЬ к папке data на машине сборки, и на чужом
# компьютере приложение оставалось без правил, карт и картинок: показать
# кому-нибудь готовую сборку было нельзя. Теперь data кладётся рядом с jar-ами,
# а движок ищет её там сам (GameConfig.resolveDataRoot), если прибитого пути на
# машине нет. Свойство kelium.data остаётся: на машине разработчика оно и
# указывает на рабочую папку, а править данные удобнее в проекте, а не в exe.
Write-Output "3-бис/6 данные игры внутрь образа…"
$dataDst = "dist\app\Kelium\app\data"
Copy-Item -Recurse -Force "data" $dataDst
# В РАЗДАЧУ ИДЁТ ТОЛЬКО ТО, ЧЕМ ИГРАЮТ. Обученные модели, архивы геномов,
# отработанные наборы и размеченные образцы для художника игре не нужны, а весят
# они больше самой игры (одно data/training — два гигабайта).
foreach ($junk in @("training", "genomes-archive-*", "genomes-boi2", "_archive", "tsv")) {
    Get-ChildItem -Path $dataDst -Filter $junk -ErrorAction SilentlyContinue |
        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
}
Get-ChildItem -Path (Join-Path $dataDst "textures") -Filter "_образцы" `
    -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
"   данные: {0:N1} МБ" -f ((Get-ChildItem -Recurse $dataDst | Measure-Object Length -Sum).Sum / 1MB) | Write-Output

Write-Output "4/6 упаковка образа в архив…"
$zip = "target\payload.zip"
if (Test-Path $zip) { Remove-Item -Force $zip }
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory(
    (Resolve-Path "dist\app\Kelium").Path, (Join-Path $PSScriptRoot $zip),
    [System.IO.Compression.CompressionLevel]::Optimal, $false)

# Отпечаток содержимого — им же именуется временная папка: новая сборка
# распакуется в новую папку, старая версия не подхватится по ошибке.
$hash = (Get-FileHash $zip -Algorithm SHA256).Hash.Substring(0, 12).ToLower()
"   архив {0:N1} МБ, отпечаток {1}" -f ((Get-Item $zip).Length / 1MB), $hash | Write-Output

Write-Output "5/6 сборка однофайловых exe…"
$stub = Get-Content sfx-stub.cs -Raw -Encoding UTF8
$refs = "/r:System.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll /r:System.Windows.Forms.dll"
foreach ($app in @(
        @{ Name = "KeliumConstructor"; Target = "Kelium.exe"; Icon = "constructor" },
        @{ Name = "KeliumRunner"; Target = "KeliumRunner.exe"; Icon = "runner" },
        @{ Name = "KeliumReplay2"; Target = "KeliumReplay2.exe"; Icon = "replay2" },
        @{ Name = "KeliumHelp"; Target = "KeliumHelp.exe"; Icon = "help" },
        @{ Name = "Играть"; Target = "KeliumGame.exe"; Icon = "game" })) {
    $src = "target\stub_$($app.Name).cs"
    $stub.Replace("@VERSION@", $hash).Replace("@TARGET@", $app.Target) |
        Out-File -Encoding UTF8 $src
    $exe = "dist\$($app.Name).exe"
    Remove-FileStubborn $exe
    # ИКОНКА приложения: PNG из icons\ превращается в .ico и вшивается в exe.
    # Нет файла — собираем без иконки, а не падаем.
    $iconArg = ""
    $ico = Convert-PngToIco "icons\$($app.Icon).png" "target\$($app.Icon).ico"
    if ($ico) { $iconArg = "/win32icon:$ico " }
    $cmd = "& `"$csc`" /nologo /target:winexe /platform:anycpu /optimize+ " +
           "$iconArg/out:$exe /resource:$zip,payload.zip $refs $src"
    Invoke-Expression $cmd
    if (-not (Test-Path $exe)) { throw "не собрался $exe" }
}

Write-Output "6/6 проверка…"
foreach ($n in @("KeliumConstructor", "KeliumRunner", "KeliumReplay2", "KeliumHelp",
                 "Играть")) {
    $f = Get-Item "dist\$n.exe"
    "   {0} — {1:N1} МБ" -f $f.Name, ($f.Length / 1MB) | Write-Output
}

Write-Output ""
Write-Output "ГОТОВО — по одному файлу на приложение:"
Write-Output "  dist\KeliumConstructor.exe  — конструктор раскладок"
Write-Output "  dist\KeliumRunner.exe       — прогоны симуляций"
Write-Output "  dist\KeliumReplay2.exe      — разбор партии"
Write-Output "  dist\KeliumHelp.exe         — справочник: правила и все карты"
Write-Output "  dist\Играть.exe             — САМА ИГРА: «Штаб» и партия"
Write-Output ""
Write-Output "Файлы самодостаточны: Java внутри, при первом запуске распаковываются"
Write-Output "в %TEMP%\Kelium-$hash (дальше стартуют сразу). Отчёты и логи пишутся"
Write-Output "рядом с тем exe, который запустили. Данные игры: $dataPath"



