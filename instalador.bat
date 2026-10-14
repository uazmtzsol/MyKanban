@echo off
setlocal EnableExtensions
rem ============================================================
rem  Personal Kanban - native Windows installer builder (jpackage)
rem
rem  Builds the portable jar and wraps it with the JDK's own
rem  jpackage tool, producing a double-click installer:
rem      target\installer\Personal Kanban-1.0.0.exe
rem
rem  Requirements:
rem    - JDK 21+ (jpackage). Found via JAVA_HOME, else on PATH.
rem    - WiX Toolset 3.x (light.exe on PATH) for the .exe type.
rem      Without WiX the script falls back to a portable app-image.
rem
rem  The portable launchers (kanban.bat / kanban.sh) are left intact.
rem  Data still lives in %USERPROFILE%\.personalkanban (or -Dpk.data.dir).
rem ============================================================

set "SCRIPT_DIR=%~dp0"
pushd "%SCRIPT_DIR%"

set "APP_NAME=Personal Kanban"
set "MAIN_JAR=personal-kanban.jar"
set "MAIN_CLASS=com.personalkanban.Launcher"
set "APP_VERSION=1.0.0"
set "DEST=target\installer"
set "INPUT=target\jpackage-input"

rem --- locate jpackage: JAVA_HOME first, else the first hit on PATH ---
set "JPACKAGE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
if not defined JPACKAGE for %%J in (jpackage.exe) do set "JPACKAGE=%%~$PATH:J"
if not defined JPACKAGE (
    echo [ERROR] jpackage not found. Install a JDK 21+ and set JAVA_HOME
    echo         or add its bin folder to PATH.
    goto :fail
)

rem --- the .exe installer type needs WiX Toolset; else build an app-image ---
set "TYPE=exe"
where light.exe >nul 2>&1
if errorlevel 1 (
    echo [WARN] WiX Toolset not found ^(light.exe^). Building a portable
    echo        app-image instead of a double-click .exe installer.
    set "TYPE=app-image"
)

rem --- 1) build the fat jar (portable profile bundles the JavaFX natives) ---
echo [1/3] Building the portable jar...
call mvn -q -Pportable -DskipTests package
if errorlevel 1 goto :fail
if not exist "target\%MAIN_JAR%" (
    echo [ERROR] target\%MAIN_JAR% was not produced.
    goto :fail
)

rem --- 2) stage a clean input folder holding just the jar ---
echo [2/3] Staging the input folder...
if exist "%INPUT%" rmdir /s /q "%INPUT%"
mkdir "%INPUT%"
copy /y "target\%MAIN_JAR%" "%INPUT%\" >nul

rem --- optional custom icon: drop a Windows .ico here to brand the app ---
set "ICON_OPT="
if exist "src\main\resources\icons\app.ico" set "ICON_OPT=--icon src\main\resources\icons\app.ico"

rem --- 3) package ---
echo [3/3] Running jpackage (%TYPE%)...
if exist "%DEST%" rmdir /s /q "%DEST%"
rem Shortcut / Start-menu options are only valid for the exe/msi types.
set "WIN_OPTS="
if "%TYPE%"=="exe" set WIN_OPTS=--win-shortcut --win-menu --win-menu-group PersonalKanban
"%JPACKAGE%" ^
    --type %TYPE% ^
    --name "%APP_NAME%" ^
    --app-version "%APP_VERSION%" ^
    --input "%INPUT%" ^
    --main-jar "%MAIN_JAR%" ^
    --main-class "%MAIN_CLASS%" ^
    --dest "%DEST%" ^
    --vendor "Personal Kanban" ^
    --description "Offline personal kanban board" ^
    %WIN_OPTS% ^
    --java-options "--enable-native-access=ALL-UNNAMED" ^
    %ICON_OPT%
if errorlevel 1 goto :fail

echo.
if "%TYPE%"=="exe" (
    echo [OK] Installer built into "%DEST%".
    echo      Run the "Personal Kanban-*.exe" there to install the app.
) else (
    echo [OK] Portable app-image built into "%DEST%\%APP_NAME%".
    echo      Launch "%DEST%\%APP_NAME%\%APP_NAME%.exe" ^(no installation^).
)
echo.
if defined ICON_OPT (
    echo      App icon: src\main\resources\icons\app.ico
) else (
    echo      No custom icon found. Drop a Windows .ico at
    echo      src\main\resources\icons\app.ico and rerun to brand the app.
)
echo      Note: if the icon looks unchanged, Windows cached it - rename or
echo      move the .exe, or run "ie4uinit.exe -show" and restart Explorer.
popd
endlocal
exit /b 0

:fail
echo.
echo [FAILED] Packaging did not complete.
popd
endlocal
exit /b 1
