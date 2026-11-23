#!/usr/bin/env sh
# Build and run the Recycling Buyback Calculator (requires JDK 17+).
#   ./run.sh              build and start the server on http://127.0.0.1:8080/
#   ./run.sh test         build and run the tests
#   ./run.sh --port 9000  extra arguments are passed to the server
# The first run downloads the SQLite JDBC driver into lib/ (used for accounts and saved calculations).
set -e
cd "$(dirname "$0")"

SQLITE_VERSION=3.54.1.0
SQLITE_SHA256=f8dce420a6016278497f4f0560f65602b9db858de141b541cf91a4644ebe4639
SQLITE_JAR="lib/sqlite-jdbc-$SQLITE_VERSION.jar"
if [ ! -f "$SQLITE_JAR" ]; then
	mkdir -p lib
	echo "Downloading SQLite JDBC driver $SQLITE_VERSION ..."
	URL="https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/$SQLITE_VERSION/sqlite-jdbc-$SQLITE_VERSION.jar"
	if command -v curl >/dev/null 2>&1; then
		curl -fsSL -o "$SQLITE_JAR.part" "$URL"
	else
		wget -q -O "$SQLITE_JAR.part" "$URL"
	fi
	if command -v sha256sum >/dev/null 2>&1; then
		ACTUAL=$(sha256sum "$SQLITE_JAR.part" | cut -d' ' -f1)
	else
		ACTUAL=$(shasum -a 256 "$SQLITE_JAR.part" | cut -d' ' -f1)
	fi
	if [ "$ACTUAL" != "$SQLITE_SHA256" ]; then
		rm -f "$SQLITE_JAR.part"
		echo "Checksum mismatch for $SQLITE_JAR (got $ACTUAL)" >&2
		exit 1
	fi
	mv "$SQLITE_JAR.part" "$SQLITE_JAR"
fi

rm -rf Newproj/build Newproj/build-test
javac --release 17 -d Newproj/build $(find Newproj/src -name '*.java')

# --enable-native-access lets the SQLite driver load its native library without warnings on newer JDKs.
JAVA_OPTS="--enable-native-access=ALL-UNNAMED"

if [ "$1" = "test" ]; then
	javac --release 17 -cp Newproj/build -d Newproj/build-test $(find Newproj/test -name '*.java')
	STATUS=0
	for TEST_CLASS in com.recycleproj.BuybackCalculatorTest com.recycleproj.AccountsTest; do
		java $JAVA_OPTS -cp "Newproj/build:Newproj/build-test:lib/*" "$TEST_CLASS" || STATUS=$?
	done
	exit $STATUS
fi

exec java $JAVA_OPTS -cp "Newproj/build:lib/*" com.recycleproj.Main "$@"