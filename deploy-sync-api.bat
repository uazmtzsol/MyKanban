@echo off
REM =========================================================================
REM  deploy-sync-api.bat — Personal Kanban
REM
REM  Copies the PHP sync API from this development checkout to the XAMPP
REM  web root so Apache serves it at http://localhost/sync/.
REM
REM  Usage:  deploy-sync-api.bat [target_folder]
REM      target_folder defaults to C:\xampp\htdocs\sync
REM
REM  NOTE: config.php is NOT overwritten — it holds the API-key hash and
REM  the database credentials of the machine XAMPP runs on. When the target
REM  has no config.php yet, config.sample.php is copied there as a starting
REM  point and a reminder is shown.
REM =========================================================================
setlocal

set "SOURCE=%~dp0sync"
set "TARGET=%~1"
if "%TARGET%"=="" set "TARGET=C:\xampp\htdocs\sync"

if not exist "%SOURCE%\" (
    echo ERROR: API source folder not found: %SOURCE%
    echo Run this file from the project root - it ships next to pom.xml.
    exit /b 1
)
if not exist "%TARGET%\.." (
    echo ERROR: target parent folder not found: %TARGET%\..
    echo Is XAMPP installed at C:\xampp? Pass the folder as an argument.
    exit /b 1
)

if not exist "%TARGET%" mkdir "%TARGET%"

copy /Y "%SOURCE%\index.php"         "%TARGET%\" >nul || goto :failed
copy /Y "%SOURCE%\config.sample.php" "%TARGET%\" >nul || goto :failed
copy /Y "%SOURCE%\setup.sql"         "%TARGET%\" >nul || goto :failed

if not exist "%TARGET%\config.php" (
    copy /Y "%SOURCE%\config.sample.php" "%TARGET%\config.php" >nul
    echo.
    echo CREATED %TARGET%\config.php from the sample.
    echo Edit it: set db_dsn/db_user/db_password and api_key_hash.
    echo Generate the hash with PHP: php -r "echo hash('sha256','TU_CLAVE')"
)

echo.
echo Deployed the sync API to %TARGET%
echo URL:  http://localhost/sync/index.php
echo Test: curl -H "X-API-Key: TU_CLAVE" "http://localhost/sync/index.php?action=catalog"
exit /b 0

:failed
echo ERROR: copy failed — is Apache/XAMPP running and the folder writable?
exit /b 1
