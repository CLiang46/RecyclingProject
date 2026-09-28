# Build and run the Recycling Buyback Calculator (requires JDK 17+).
#   .\run.ps1              build and start the server on http://127.0.0.1:8080/
#   .\run.ps1 test         build and run the tests
#   .\run.ps1 --port 9000  extra arguments are passed to the server
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

Remove-Item -Recurse -Force Newproj\build, Newproj\build-test -ErrorAction SilentlyContinue
$sources = Get-ChildItem -Recurse Newproj\src -Filter *.java | ForEach-Object FullName
javac --release 17 -d Newproj\build $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($args.Count -gt 0 -and $args[0] -eq 'test') {
	$tests = Get-ChildItem -Recurse Newproj\test -Filter *.java | ForEach-Object FullName
	javac --release 17 -cp Newproj\build -d Newproj\build-test $tests
	if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
	java -cp "Newproj\build;Newproj\build-test" com.recycleproj.BuybackCalculatorTest
	exit $LASTEXITCODE
}

java -cp Newproj\build com.recycleproj.Main @args
exit $LASTEXITCODE
