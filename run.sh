#!/usr/bin/env sh
# Build and run the Recycling Buyback Calculator (requires JDK 17+).
#   ./run.sh              build and start the server on http://127.0.0.1:8080/
#   ./run.sh test         build and run the tests
#   ./run.sh --port 9000  extra arguments are passed to the server
set -e
cd "$(dirname "$0")"

rm -rf Newproj/build Newproj/build-test
javac --release 17 -d Newproj/build $(find Newproj/src -name '*.java')

if [ "$1" = "test" ]; then
	javac --release 17 -cp Newproj/build -d Newproj/build-test $(find Newproj/test -name '*.java')
	exec java -cp "Newproj/build:Newproj/build-test" com.recycleproj.BuybackCalculatorTest
fi

exec java -cp Newproj/build com.recycleproj.Main "$@"
