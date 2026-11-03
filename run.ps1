# Compiles and runs the Bank Loan Management System console app.
$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path out | Out-Null

$sources = Get-ChildItem -Recurse -Path src\bank -Filter *.java | Select-Object -ExpandProperty FullName
javac -d out $sources

java -cp out bank.Main
