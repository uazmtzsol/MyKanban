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

rem JDK 24+ restricts native loading (JavaFX loads its natives here) and
rem JDK 23+ warns about sun.misc.Unsafe inside JavaFX internals. Both flags
rem silence those warnings; older runtimes do not know them, so probe first.
set "JVM_FLAGS="
"%JAVA_CMD%" --enable-native-access=ALL-UNNAMED -version >nul 2>&1
if not errorlevel 1 set "JVM_FLAGS=%JVM_FLAGS% --enable-native-access=ALL-UNNAMED"
"%JAVA_CMD%" --sun-misc-unsafe-memory-access=allow -version >nul 2>&1
if not errorlevel 1 set "JVM_FLAGS=%JVM_FLAGS% --sun-misc-unsafe-memory-access=allow"

if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"

"%JAVA_CMD%" %JVM_FLAGS% -Dpk.data.dir="%DATA_DIR%" -jar "%SCRIPT_DIR%personal-kanban.jar" %*
if errorlevel 1 pause
endlocal
