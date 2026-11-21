param(
    [string]$Root = 'C:\Users\joe\Desktop\dhwork\hypersound',
    [string]$Sdk  = 'C:\Users\joe\Desktop\dhwork\sdk',
    [string]$Libx = 'C:\Users\joe\Desktop\dhwork\libx\classes.jar',
    [string]$OutDir = '',
    [string]$OutName = 'HyperSound-4.3.apk'
)
$ErrorActionPreference = 'Stop'

# Output folder: by default the "output..." folder next to the project (created if missing).
if (-not $OutDir) {
    $parent = Split-Path $Root -Parent
    $cand = Get-ChildItem -Path $parent -Directory -Filter 'output*' -ErrorAction SilentlyContinue |
            Select-Object -First 1
    if ($cand) { $OutDir = $cand.FullName } else { $OutDir = Join-Path $parent 'output' }
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Out = Join-Path $OutDir $OutName

$bt   = Join-Path $Sdk 'build-tools-35\android-15'
$plat = Join-Path $Sdk 'platform-35\android-35\android.jar'
$aapt2 = Join-Path $bt 'aapt2.exe'
$zipalign = Join-Path $bt 'zipalign.exe'
$apksigner = Join-Path $bt 'lib\apksigner.jar'
$d8jar = Join-Path $bt 'lib\d8.jar'
$jdk = 'C:\Program Files\Java\jdk-27'
$jar = Join-Path $jdk 'bin\jar.exe'
$keytool = Join-Path $jdk 'bin\keytool.exe'

$build = Join-Path $Root 'build'
if (Test-Path $build) { Remove-Item -Recurse -Force $build }
New-Item -ItemType Directory -Force -Path $build | Out-Null
$gen = Join-Path $build 'gen'
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'
New-Item -ItemType Directory -Force -Path $gen, $classes, $dex | Out-Null

Write-Host '== aapt2 compile =='
& $aapt2 compile --dir (Join-Path $Root 'res') -o (Join-Path $build 'res.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host '== aapt2 link =='
& $aapt2 link -o (Join-Path $build 'base.apk') -I $plat --manifest (Join-Path $Root 'AndroidManifest.xml') -R (Join-Path $build 'res.zip') --auto-add-overlay --java $gen --min-sdk-version 29 --target-sdk-version 34 --version-code 24 --version-name 4.3
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '== javac =='
$sources = Get-ChildItem -Path (Join-Path $Root 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$rjava = Get-ChildItem -Path $gen -Recurse -Filter R.java | ForEach-Object { $_.FullName }
$all = @($sources) + @($rjava)
& javac -encoding UTF-8 --release 11 -classpath "$plat;$Libx" -d $classes @all
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '== d8 =='
& $jar cf (Join-Path $build 'classes.jar') -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
& java -cp $d8jar com.android.tools.r8.D8 --release --min-api 29 --lib $plat --output $dex (Join-Path $build 'classes.jar')
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '== package =='
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$unsigned = Join-Path $build 'unsigned.apk'
Copy-Item (Join-Path $build 'base.apk') $unsigned -Force
$zip = [System.IO.Compression.ZipFile]::Open($unsigned, [System.IO.Compression.ZipArchiveMode]::Update)
function Add-Stored($zip, $name, $path) {
    $e = $zip.CreateEntry($name, [System.IO.Compression.CompressionLevel]::NoCompression)
    $s = $e.Open()
    $bytes = [IO.File]::ReadAllBytes($path)
    $s.Write($bytes, 0, $bytes.Length)
    $s.Close()
}
Add-Stored $zip 'classes.dex' (Join-Path $dex 'classes.dex')
Add-Stored $zip 'META-INF/xposed/module.prop' (Join-Path $Root 'meta\META-INF\xposed\module.prop')
Add-Stored $zip 'META-INF/xposed/scope.list' (Join-Path $Root 'meta\META-INF\xposed\scope.list')
Add-Stored $zip 'META-INF/xposed/java_init.list' (Join-Path $Root 'meta\META-INF\xposed\java_init.list')
$zip.Dispose()

Write-Host '== zipalign =='
$aligned = Join-Path $build 'aligned.apk'
& $zipalign -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

Write-Host '== keystore =='
$ks = Join-Path $Root 'hypersound.jks'
if (-not (Test-Path $ks)) {
    & $keytool -genkeypair -keystore $ks -alias hypersound -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass hypersound -keypass hypersound `
        -dname 'CN=HyperSound, OU=Mod, O=HyperSound, L=CN, ST=CN, C=CN'
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}

Write-Host '== sign =='
if (Test-Path $Out) { Remove-Item -Force $Out }
$tmpOut = Join-Path $build 'signed.apk'
& java -jar $apksigner sign --ks $ks --ks-key-alias hypersound --ks-pass pass:hypersound --key-pass pass:hypersound `
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
    --out $tmpOut $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

Copy-Item $tmpOut $Out -Force
Write-Host ('== done: ' + $Out + ' (' + (Get-Item $Out).Length + ' bytes) ==')
