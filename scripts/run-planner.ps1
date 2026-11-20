$ErrorActionPreference = 'Stop'
$toolsDir = 'C:\Temp\workweek-build'
$env:ANDROID_HOME = Join-Path $toolsDir 'sdk'
$env:ANDROID_USER_HOME = Join-Path $toolsDir 'android-user'
$env:ANDROID_AVD_HOME = Join-Path $toolsDir 'avd'
$emulator = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$apk = Join-Path (Split-Path -Parent $PSScriptRoot) 'app\build\outputs\apk\debug\app-debug.apk'
$avdName = 'WorkWeek37'

function Find-PlannerEmulator {
    $devices = & $adb devices
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось получить список устройств Android.' }
    foreach ($line in $devices) {
        if ($line -match '^(emulator-\d+)\s+device$') {
            $candidate = $Matches[1]
            $name = & $adb -s $candidate emu avd name
            if ($LASTEXITCODE -eq 0 -and $name -contains $avdName) { return $candidate }
        }
    }
}

try {
    foreach ($path in @($emulator, $adb, $apk, (Join-Path $env:ANDROID_AVD_HOME "$avdName.ini"))) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Не найден файл $path" }
    }
    $serial = Find-PlannerEmulator
    if (-not $serial) {
        $acceleration = & $emulator -accel-check
        if (($acceleration -join "`n") -notmatch '(?m)^\s*0\s*$') {
            throw 'Эмулятору недоступно ускорение. Включите «Платформа гипервизора Windows» в optionalfeatures и перезагрузите компьютер.'
        }
        Write-Host "Запускается $avdName..."
        Start-Process -FilePath $emulator -ArgumentList '-avd', $avdName
    }
    Write-Host 'Ожидание загрузки Android...'
    $deadline = (Get-Date).AddMinutes(5)
    do {
        if (-not $serial) { $serial = Find-PlannerEmulator }
        if ($serial) {
            $booted = & $adb -s $serial shell getprop sys.boot_completed
            if ($LASTEXITCODE -eq 0 -and $booted -contains '1') { break }
        }
        if ((Get-Date) -ge $deadline) { throw "За 5 минут $avdName не загрузился. Проверьте окно эмулятора и повторите запуск." }
        Start-Sleep -Seconds 2
    } while ($true)

    Write-Host 'Установка планировщика...'
    & $adb -s $serial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось установить APK. Подробности приведены выше.' }
    & $adb -s $serial shell am start -W -n ru.workweek.planner/.MainActivity
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось открыть планировщик.' }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
exit 0
