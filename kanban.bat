@echo off
rem ============================================================
rem  Personal Kanban - portable launcher for Windows
rem  Looks for a Java 21+ runtime next to this script (jre\),
rem  else falls back to java on PATH. All data (SQLite database,
rem  undo history, settings) stays in data\ on this drive.
rem ============================================================
setlocal
set "SCRIPT_DIR=%~dp0"
set "DATA_DIR=%SCRIPT_DIR%data"

if exist "%SCRIPT_DIR%jre\bin\java.exe" (
    set "JAVA_CMD=%SCRIPT_DIR%jre\bin\java.exe"
) else (
    set "JAVA_CMD=java"
)

if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"

"%JAVA_CMD%" -Dpk.data.dir="%DATA_DIR%" -jar "%SCRIPT_DIR%personal-kanban.jar" %*
if errorlevel 1 pause
endlocal
