# Compiles and starts the web version, then open http://localhost:8090
# Pass a port to use a different one:  .\run-web.ps1 9000
$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path out | Out-Null

$sources = Get-ChildItem -Recurse -Path src\bank -Filter *.java | Select-Object -ExpandProperty FullName
javac -d out $sources

java -cp out bank.web.WebServer @args
