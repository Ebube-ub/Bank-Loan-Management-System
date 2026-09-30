# Compiles and runs the JUnit 5 test suite using the standalone console launcher in lib/.
$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Force -Path out | Out-Null
New-Item -ItemType Directory -Force -Path out-test | Out-Null

$junitJar = Get-ChildItem -Path lib -Filter "junit-platform-console-standalone-*.jar" |
    Select-Object -First 1 -ExpandProperty FullName

$mainSources = Get-ChildItem -Recurse -Path src\bank -Filter *.java | Select-Object -ExpandProperty FullName
javac -d out $mainSources

$testSources = Get-ChildItem -Recurse -Path test\bank -Filter *.java | Select-Object -ExpandProperty FullName
javac -d out-test -cp "out;$junitJar" $testSources

java -jar $junitJar execute -cp "out;out-test" --scan-classpath --details=tree
