$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$sdk = $env:ANDROID_HOME
if (!$sdk) { throw "Set ANDROID_HOME to the Android SDK directory" }
$bt = Join-Path $sdk "build-tools\35.0.0"
$jar = Join-Path $sdk "platforms\android-35\android.jar"
$out = Join-Path $PSScriptRoot "build"
function Run($exe, [string[]]$argv) {
    & $exe @argv
    if ($LASTEXITCODE -ne 0) { throw "Command failed: $exe ($LASTEXITCODE)" }
}
New-Item -ItemType Directory -Force "$out\gen", "$out\classes", "$out\dex" | Out-Null
Get-ChildItem "$out\gen", "$out\classes", "$out\dex" -Recurse -File | Remove-Item
Run "$bt\aapt2.exe" @("compile", "--dir", "res", "-o", "$out\res.zip")
Run "$bt\aapt2.exe" @("link", "-o", "$out\unsigned.apk", "-I", $jar, "--manifest", "AndroidManifest.xml", "--java", "$out\gen",
    "--min-sdk-version", "24", "--target-sdk-version", "35", "--version-code", "4", "--version-name", "3.1", "$out\res.zip")
$sources = Get-ChildItem "src", "$out\gen" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
Run "javac" (@("-source", "8", "-target", "8", "-bootclasspath", "$jar;$bt\core-lambda-stubs.jar", "-encoding", "UTF-8", "-Xlint:-options", "-d", "$out\classes") + $sources)
Run "jar" @("cf", "$out\classes.jar", "-C", "$out\classes", ".")
Run "java" @("-cp", "$bt\lib\d8.jar", "com.android.tools.r8.D8", "--release", "--min-api", "24", "--lib", $jar, "--output", "$out\dex", "$out\classes.jar")
Push-Location "$out\dex"
try { Run "$bt\aapt.exe" @("add", "$out\unsigned.apk", "classes.dex") } finally { Pop-Location }
Run "$bt\zipalign.exe" @("-f", "-p", "4", "$out\unsigned.apk", "$out\aligned.apk")
$key = if ($env:MUSIC_SIGNING_KEY) { $env:MUSIC_SIGNING_KEY } else { "$out\debug.keystore" }
if (!(Test-Path $key)) {
    if ($env:MUSIC_SIGNING_KEY) { throw "MUSIC_SIGNING_KEY was not found" }
    Run "keytool" @("-genkeypair", "-keystore", $key, "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
        "-storepass", "android", "-keypass", "android", "-dname", "CN=Android Debug,O=Android,C=US")
}
if ($env:MUSIC_SIGNING_KEY) {
    if (!$env:MUSIC_STORE_PASSWORD -or !$env:MUSIC_KEY_PASSWORD) { throw "Set MUSIC_STORE_PASSWORD and MUSIC_KEY_PASSWORD privately" }
    Run "java" @("-jar", "$bt\lib\apksigner.jar", "sign", "--ks", $key, "--ks-pass", "env:MUSIC_STORE_PASSWORD", "--key-pass", "env:MUSIC_KEY_PASSWORD", "--out", "$out\MusicPlayer.apk", "$out\aligned.apk")
} else {
    Run "java" @("-jar", "$bt\lib\apksigner.jar", "sign", "--ks", $key, "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", "$out\MusicPlayer.apk", "$out\aligned.apk")
}
Run "java" @("-jar", "$bt\lib\apksigner.jar", "verify", "$out\MusicPlayer.apk")
Write-Host "Built $out\MusicPlayer.apk"
