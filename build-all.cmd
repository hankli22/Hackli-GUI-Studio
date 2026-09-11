@echo off
rem ============================================================
rem  Hackli GUI Studio - build everything and deploy the mod
rem  Output:
rem    build\libs\hackli-gui-studio-0.2.0.jar            (Minecraft mod)
rem    simulator-gl\build\libs\simulator-gl-0.2.0-all.jar  (OpenGL editor)
rem    tools\selftest\build\libs\selftest-0.2.0-all.jar    (core self test)
rem
rem  Deploying the mod into a game instance is opt-in: set
rem  HACKLI_MODS_DIR to that instance's mods folder, e.g.
rem
rem    set "HACKLI_MODS_DIR=%APPDATA%\.minecraft\mods"
rem    build-all.cmd
rem
rem  Leave it unset and the jar is simply left in build\libs.
rem
rem  The Swing desktop editor and the browser editor are archived and no
rem  longer built here.
rem ============================================================
setlocal EnableExtensions
cd /d "%~dp0"

call :find_gradle
if not defined GRADLE_CMD (
    echo [ERROR] No Gradle found. Install Gradle 9.x or place it in PATH.
    pause
    exit /b 1
)

echo === Hackli GUI Studio - build all ===
call "%GRADLE_CMD%" build
if errorlevel 1 (
    echo [ERROR] Build failed. See the log above.
    pause
    exit /b 1
)

echo.
echo [OK] Minecraft mod : %CD%\build\libs\hackli-gui-studio-0.2.0.jar
echo [OK] OpenGL editor : %CD%\simulator-gl\build\libs\simulator-gl-0.2.0-all.jar
echo [OK] Core self test: %CD%\tools\selftest\build\libs\selftest-0.2.0-all.jar

rem ---- optional deploy to a game instance ----
if not defined HACKLI_MODS_DIR (
    echo [..] HACKLI_MODS_DIR is not set - copy the mod jar into your instance's
    echo      mods folder yourself, or set it and run this script again:
    echo          set "HACKLI_MODS_DIR=^<path to your instance^>\mods"
    goto :done
)

if not exist "%HACKLI_MODS_DIR%" (
    echo [..] HACKLI_MODS_DIR="%HACKLI_MODS_DIR%" does not exist - nothing deployed.
    goto :done
)

copy /y "build\libs\hackli-gui-studio-0.2.0.jar" "%HACKLI_MODS_DIR%\" >nul
if errorlevel 1 (
    echo [..] Copy to "%HACKLI_MODS_DIR%" failed - nothing deployed.
) else (
    echo [OK] Mod deployed to: %HACKLI_MODS_DIR%
)

:done
pause
exit /b 0

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
if not errorlevel 1 (
    set "GRADLE_CMD=gradle"
)
exit /b 0
