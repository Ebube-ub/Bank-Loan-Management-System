#!/usr/bin/env bash
# Compiles and starts the web version, then open http://localhost:8090
# Pass a port to use a different one:  ./run-web.sh 9000
set -e

mkdir -p out
javac -d out $(find src/bank -name "*.java")
java -cp out bank.web.WebServer "$@"
