@echo off
rem Double-click this file to compile and start NepoLM, then open http://localhost:8090
rem (a .cmd file isn't blocked by Windows' "running scripts is disabled" setting)
rem To use another port, run it from a terminal: start-web.cmd 9000
cd /d "%~dp0"

if not exist out mkdir out
javac -d out -sourcepath src src\bank\web\WebServer.java
if errorlevel 1 (
    echo.
    echo Could not compile. Is Java installed? Try: java -version
    pause
    exit /b 1
)

echo.
java -cp out bank.web.WebServer %*

echo.
echo The server has stopped.
pause
