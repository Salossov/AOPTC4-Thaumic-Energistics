$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskWorkspace = $taskRoot
for ($taskDepth = 0; $taskDepth -lt 8; $taskDepth++) {
    if (Test-Path -LiteralPath (Join-Path $taskWorkspace '.tools/java21')) { break }
    $taskParent = Split-Path -Parent $taskWorkspace
    if (-not $taskParent -or $taskParent -eq $taskWorkspace) { break }
    $taskWorkspace = $taskParent
}
$taskJava = $null
foreach ($taskJdkRoot in @((Join-Path $taskRoot '.tools\java21'), (Join-Path $taskWorkspace '.tools\java21'))) {
    if (-not (Test-Path -LiteralPath $taskJdkRoot)) { continue }
    $taskJdk = Get-ChildItem -LiteralPath $taskJdkRoot -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
    if ($null -ne $taskJdk) { $taskJava = Join-Path $taskJdk.FullName 'bin\java.exe'; break }
}
if ($null -eq $taskJava -and $env:JAVA_HOME) {
    $taskCandidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path -LiteralPath $taskCandidate) { $taskJava = $taskCandidate }
}
if ($null -eq $taskJava) { $taskJava = (Get-Command java.exe -ErrorAction SilentlyContinue).Source }
if ($null -eq $taskJava) { throw 'JDK 21 is required. Set JAVA_HOME.' }
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $taskJava)
$taskCacheRoot = Join-Path $taskRoot '.cache'
if (Test-Path -LiteralPath (Join-Path $taskWorkspace '.cache\gradle')) { $taskCacheRoot = Join-Path $taskWorkspace '.cache' }
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $taskCacheRoot 'gradle' }
$env:TEMP = Join-Path $taskCacheRoot 'tmp'
$env:TMP = $env:TEMP
$taskJavaHome = Join-Path $taskCacheRoot 'java-home'
New-Item -ItemType Directory -Force -Path $env:GRADLE_USER_HOME,$env:TEMP,$taskJavaHome | Out-Null
Push-Location -LiteralPath $taskRoot
try {
    & $taskJava '-Xmx1536m' "-Duser.home=$taskJavaHome" "-Dorg.gradle.jvmargs=-Xmx1536m -Duser.home=$taskJavaHome" '-classpath' (Join-Path $taskRoot 'gradle\wrapper\gradle-wrapper.jar') 'org.gradle.wrapper.GradleWrapperMain' @args
    $taskExit = $LASTEXITCODE
} finally { Pop-Location }
exit $taskExit
