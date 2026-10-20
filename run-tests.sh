#!/usr/bin/env bash
# Compiles and runs the JUnit 5 test suite using the standalone console launcher in lib/.
set -e

mkdir -p out out-test

junit_jar=$(ls lib/junit-platform-console-standalone-*.jar | head -n 1)

javac -d out $(find src/bank -name "*.java")

# Uses ';' as the classpath separator: on Windows, java.exe expects ';' even
# when invoked from a bash shell (Git Bash). Change to ':' on Linux/macOS.
javac -d out-test -cp "out;${junit_jar}" $(find test/bank -name "*.java")

java -jar "${junit_jar}" execute -cp "out;out-test" --scan-classpath --details=tree
