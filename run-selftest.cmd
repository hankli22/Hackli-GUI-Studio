@echo off
rem ============================================================
rem  Hackli GUI Studio - core self test (headless, no window)
rem
rem  Checks the document model, Meteor layout, painter geometry,
rem  exporters, undo, templates, clipboard ops and the widget
rem  geometry (number / texture). No GL context, no font file,
rem  no window - it never steals focus.
rem
rem  Usage:  run-selftest.cmd
rem ============================================================
setlocal EnableExtensions
cd /d "%~dp0"

call :find_gradle
if not defined GRADLE_CMD (
    echo [ERROR] No Gradle found. Install Gradle 9.x or place it in PATH.
    pause
    exit /b 1
)

call "%GRADLE_CMD%" :selftest:fatJar -q
if errorlevel 1 (
    echo [ERROR] Build failed.
    pause
    exit /b 1
)

java -Djava.awt.headless=true "-Dhackli.home=%~dp0data" -cp "tools\selftest\build\libs\selftest-0.2.0-all.jar" com.hackli.guidesigner.testing.CoreSelfTest
set "CODE=%ERRORLEVEL%"

echo.
if "%CODE%"=="0" (echo [OK] Self test passed.) else (echo [FAIL] Self test failed with code %CODE%.)
pause
exit /b %CODE%

:find_gradle
if exist "gradlew.bat" (
    set "GRADLE_CMD=%CD%\gradlew.bat"
    exit /b 0
)
for /d %%D in ("%USERPROFILE%\.gradle\wrapper\dists\*") do (
    if exist "%%D\bin\gradle.bat" (
        set "GRADLE_CMD=%%D\bin\gradle.bat"
        exit /b 0
    )
)
where gradle >nul 2>nul
if not errorlevel 1 set "GRADLE_CMD=gradle"
exit /b 0
