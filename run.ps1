# Build and run the Recycling Buyback Calculator (requires JDK 17+).
#   .\run.ps1              build and start the server on http://127.0.0.1:8080/
#   .\run.ps1 test         build and run the tests
#   .\run.ps1 --port 9000  extra arguments are passed to the server
# The first run downloads the SQLite JDBC driver into lib\ (used for accounts and saved calculations).
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$sqliteVersion = '3.54.1.0'
$sqliteSha256 = 'f8dce420a6016278497f4f0560f65602b9db858de141b541cf91a4644ebe4639'
$sqliteJar = "lib\sqlite-jdbc-$sqliteVersion.jar"
if (-not (Test-Path $sqliteJar)) {
	New-Item -ItemType Directory -Force lib | Out-Null
	Write-Host "Downloading SQLite JDBC driver $sqliteVersion ..."
	[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
	$url = "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/$sqliteVersion/sqlite-jdbc-$sqliteVersion.jar"
	Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile "$sqliteJar.part"
	$actual = (Get-FileHash "$sqliteJar.part" -Algorithm SHA256).Hash.ToLower()
	if ($actual -ne $sqliteSha256) {
		Remove-Item "$sqliteJar.part"
		Write-Error "Checksum mismatch for $sqliteJar (got $actual)"
	}
	Move-Item "$sqliteJar.part" $sqliteJar
}

Remove-Item -Recurse -Force Newproj\build, Newproj\build-test -ErrorAction SilentlyContinue
$sources = Get-ChildItem -Recurse Newproj\src -Filter *.java | ForEach-Object FullName
javac --release 17 -d Newproj\build $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# --enable-native-access lets the SQLite driver load its native library without warnings on newer JDKs.
$javaOptions = @('--enable-native-access=ALL-UNNAMED')

if ($args.Count -gt 0 -and $args[0] -eq 'test') {
	$tests = Get-ChildItem -Recurse Newproj\test -Filter *.java | ForEach-Object FullName
	javac --release 17 -cp Newproj\build -d Newproj\build-test $tests
	if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
	$status = 0
	foreach ($testClass in 'com.recycleproj.BuybackCalculatorTest', 'com.recycleproj.AccountsTest') {
		java @javaOptions -cp "Newproj\build;Newproj\build-test;lib\*" $testClass
		if ($LASTEXITCODE -ne 0) { $status = $LASTEXITCODE }
	}
	exit $status
}

java @javaOptions -cp "Newproj\build;lib\*" com.recycleproj.Main @args
exit $LASTEXITCODE