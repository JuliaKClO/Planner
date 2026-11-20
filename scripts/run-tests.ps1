param([switch]$NoPause)

$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$toolsDir = Join-Path $env:SystemDrive 'Temp\workweek-build'

try {
    # Reuse the prepared tools only for environment variables the caller omitted.
    if (-not $env:JAVA_HOME) {
        $jdk = Get-ChildItem -LiteralPath (Join-Path $toolsDir 'jdk') -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } |
            Select-Object -First 1
        if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
    }
    if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
        $sdk = Join-Path $toolsDir 'sdk'
        if (Test-Path -LiteralPath $sdk) { $env:ANDROID_HOME = $sdk }
    }
    foreach ($setting in @{
        GRADLE_USER_HOME = 'gradle-cache'
        ANDROID_USER_HOME = 'android-user'
    }.GetEnumerator()) {
        $preparedDir = Join-Path $toolsDir $setting.Value
        if (-not [Environment]::GetEnvironmentVariable($setting.Key) -and (Test-Path -LiteralPath $preparedDir)) {
            [Environment]::SetEnvironmentVariable($setting.Key, $preparedDir, 'Process')
        }
    }
    if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        throw 'Set JAVA_HOME to your JDK 17 directory. See README.md.'
    }

    # A different checkout must never be tested through the prepared junction.
    $aliasDir = Join-Path $toolsDir 'source'
    $alias = Get-Item -LiteralPath $aliasDir -ErrorAction SilentlyContinue
    if ($alias.LinkType -eq 'Junction' -and @($alias.Target).Count -eq 1) {
        $targetDir = [IO.Path]::GetFullPath(@($alias.Target)[0]).TrimEnd('\')
        if ($targetDir -eq $projectDir.TrimEnd('\')) { $projectDir = $aliasDir }
    }
    if ($projectDir -match '[^\x00-\x7F]') {
        Write-Warning 'If the JVM cannot load test classes from this path, retry from an ASCII path such as C:\Projects\WorkWeek. See README.md.'
    }

    Write-Host "Running local JVM and Robolectric tests in $projectDir"
    $startedAt = Get-Date
    Push-Location -LiteralPath $projectDir
    try {
        & (Join-Path $projectDir 'gradlew.bat') -p $projectDir :app:testDebugUnitTest --rerun
        $testExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    $report = Join-Path $projectDir 'app\build\reports\tests\testDebugUnitTest\index.html'
    if ($testExitCode -eq 0) { Write-Host 'Tests passed.' }
    else { Write-Host "Tests failed. Gradle exit code: $testExitCode" }
    if ((Test-Path -LiteralPath $report) -and (Get-Item -LiteralPath $report).LastWriteTime -ge $startedAt) {
        Write-Host "HTML report: $report"
        if (-not $NoPause) { Invoke-Item -LiteralPath $report }
    }
    exit $testExitCode
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
