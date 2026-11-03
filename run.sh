#!/usr/bin/env bash
# Compiles and runs the Bank Loan Management System console app.
set -e

mkdir -p out
javac -d out $(find src/bank -name "*.java")
java -cp out bank.Main
