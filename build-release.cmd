@echo off
rem ============================================================
rem  Hackli GUI Studio - build a self-contained release package
rem
rem  Output:
rem    release\hackli-gui-studio-0.2.0\
rem    release\hackli-gui-studio-0.2.0-src.zip
rem    release\hackli-gui-studio-0.2.0.zip          (everything)
rem
rem  What goes in:
rem    Minecraft mod      build\libs\hackli-gui-studio-0.2.0.jar
rem    OpenGL editor      simulator-gl\build\libs\simulator-gl-0.2.0-all.jar
rem    Self test          tools\selftest\build\libs\selftest-0.2.0-all.jar
rem    Icon set           assets\icons  (GENERATED synthetic icons, CC0-1.0)
rem    examples\          design documents to open or render
rem    docs\              design, architecture, widget plan, licensing notes
rem    source\            full source tree at the current commit
rem
rem  The package deliberately contains NO Minecraft artwork: the item icons are
rem  drawn from item id hashes by `gradle :core:genIconAtlas`. To preview with the
rem  real sprites locally, extract them for yourself (they stay in the gitignored
rem  build\mc-assets and are never packaged):
rem
rem    gradle :core:extractMcAssets -PclientJar="<path to 1.21.11.jar>"
rem
rem  Usage:  build-release.cmd
rem ============================================================
setlocal EnableExtensions
cd /d "%~dp0"

set "VERSION=0.2.0"
set "REL=release\hackli-gui-studio-%VERSION%"

rem ---- 1) toolchain ----------------------------------------------------------
call :find_gradle
if not defined GRADLE_CMD (
    echo [ERROR] No Gradle found. Install Gradle 9.x or place it in PATH.
    pause
    exit /b 1
)

echo === [1/4] Building ===
call "%GRADLE_CMD%" build fatJar -x test --console=plain -q
if errorlevel 1 (
    echo [ERROR] Build failed.
    pause
    exit /b 1
)

rem ---- 2) assemble -----------------------------------------------------------
echo === [2/4] Assembling %REL% ===
if exist "%REL%" rmdir /s /q "%REL%"
mkdir "%REL%"
mkdir "%REL%\editor"
mkdir "%REL%\selftest"
mkdir "%REL%\examples"
mkdir "%REL%\docs"
mkdir "%REL%\assets"

copy /y "build\libs\hackli-gui-studio-%VERSION%.jar" "%REL%\" >nul
copy /y "simulator-gl\build\libs\simulator-gl-%VERSION%-all.jar" "%REL%\editor\" >nul
copy /y "tools\selftest\build\libs\selftest-%VERSION%-all.jar" "%REL%\selftest\" >nul
copy /y "examples\*.json" "%REL%\examples\" >nul
copy /y "LICENSE" "%REL%\LICENSE.txt" >nul
copy /y "THIRD-PARTY-NOTICES.md" "%REL%\" >nul
copy /y "docs\DESIGN.md" "%REL%\docs\" >nul
copy /y "docs\ARCHITECTURE.md" "%REL%\docs\" >nul
copy /y "docs\CONTRIBUTING.md" "%REL%\docs\" >nul
copy /y "docs\PLAN-WIDGETS.md" "%REL%\docs\" >nul
copy /y "docs\LICENSE-PLUGINS.md" "%REL%\docs\" >nul

rem Generated icon set only - no Minecraft artwork is redistributed.
if exist "assets\icons\icon-atlas.tsv" (
    copy /y "assets\icons\icon-atlas.png" "%REL%\assets\" >nul
    copy /y "assets\icons\icon-atlas.tsv" "%REL%\assets\" >nul
    copy /y "assets\icons\LICENSE-icons.txt" "%REL%\assets\" >nul 2>nul
    echo [OK] Generated icon set bundled ^(%REL%\assets^)
) else (
    echo [..] assets\icons\icon-atlas.tsv is missing - run:
    echo        gradle :core:genIconAtlas -PclientJar="^<path to 1.21.11.jar^>"
    echo      The editor will draw placeholders instead.
)

rem ---- 3) source snapshot ---------------------------------------------------
echo === [3/4] Source snapshot ===
git rev-parse --is-inside-work-tree >nul 2>nul
if errorlevel 1 (
    echo [..] Not a git checkout - copying the working tree instead.
    robocopy . "%REL%\source" /e /q /nfl /ndl /njh /njs ^
        /xd .git build release .gradle run .idea .vscode ^
        /xf release-exclude.txt >nul
) else (
    git archive --format=zip -o "release\hackli-gui-studio-%VERSION%-src.zip" HEAD
    if errorlevel 1 (
        echo [ERROR] git archive failed.
        pause
        exit /b 1
    )
)

rem ---- 4) scripts + readme --------------------------------------------------
echo === [4/4] Scripts and readme ===
call :write_scripts "%REL%"
call :write_readme "%REL%"

rem ---- zip ------------------------------------------------------------------
if exist "release\hackli-gui-studio-%VERSION%.zip" del /q "release\hackli-gui-studio-%VERSION%.zip"
powershell -NoProfile -Command "Compress-Archive -Path '%REL%' -DestinationPath 'release\hackli-gui-studio-%VERSION%.zip' -CompressionLevel Optimal"

echo.
echo === Done ===
echo   folder : %CD%\%REL%
echo   all    : %CD%\release\hackli-gui-studio-%VERSION%.zip
if exist "release\hackli-gui-studio-%VERSION%-src.zip" echo   source : %CD%\release\hackli-gui-studio-%VERSION%-src.zip
echo.
pause
exit /b 0

rem ===========================================================================
rem  helpers
rem ===========================================================================

:write_scripts
rem %1 = release folder
> "%~1\run-editor.cmd" echo @echo off
>>"%~1\run-editor.cmd" echo rem OpenGL editor. Pass a design to open, or a design plus an output png to render.
>>"%~1\run-editor.cmd" echo rem   run-editor.cmd                       ^(examples\welcome.json^)
>>"%~1\run-editor.cmd" echo rem   run-editor.cmd examples\widgets.json
>>"%~1\run-editor.cmd" echo rem   run-editor.cmd examples\widgets.json out.png
>>"%~1\run-editor.cmd" echo rem
>>"%~1\run-editor.cmd" echo rem Your projects, layouts, templates and exports are kept in data\ next to
>>"%~1\run-editor.cmd" echo rem this script, so copying the whole folder moves them with it.
>>"%~1\run-editor.cmd" echo rem Point somewhere else with:   --data ^<dir^>
>>"%~1\run-editor.cmd" echo setlocal
>>"%~1\run-editor.cmd" echo cd /d "%%~dp0"
>>"%~1\run-editor.cmd" echo set "DESIGN=%%~1"
>>"%~1\run-editor.cmd" echo if "%%DESIGN%%"=="" set "DESIGN=examples\welcome.json"
>>"%~1\run-editor.cmd" echo set DATA=-Dhackli.home="%%~dp0data"
>>"%~1\run-editor.cmd" echo java %%DATA%% -Dhackli.assets="%%CD%%\assets" -cp "editor\simulator-gl-%VERSION%-all.jar" com.hackli.guidesigner.gl.GlSimulator "%%DESIGN%%" %%~2
>>"%~1\run-editor.cmd" echo endlocal

> "%~1\run-selftest.cmd" echo @echo off
>>"%~1\run-selftest.cmd" echo rem Headless check of the document model, layout, painters and exporters.
>>"%~1\run-selftest.cmd" echo setlocal
>>"%~1\run-selftest.cmd" echo cd /d "%%~dp0"
>>"%~1\run-selftest.cmd" echo java -Djava.awt.headless=true "-Dhackli.home=%%~dp0data" -Dhackli.assets="%%CD%%\assets" -cp "selftest\selftest-%VERSION%-all.jar" com.hackli.guidesigner.testing.CoreSelfTest
>>"%~1\run-selftest.cmd" echo pause
>>"%~1\run-selftest.cmd" echo endlocal
exit /b 0

:write_readme
rem %1 = release folder
set "R=%~1\README.txt"
> "%R%" echo Hackli GUI Studio %VERSION% - release package
>>"%R%" echo ==========================================
>>"%R%" echo.
>>"%R%" echo A visual GUI designer for Meteor Client plugins (Minecraft 1.21.11, Fabric).
>>"%R%" echo Licensed under GPLv3 - see LICENSE.txt. Runtime exports link this mod and inherit
>>"%R%" echo GPLv3; static exports are self-contained. Details: docs\LICENSE-PLUGINS.md.
>>"%R%" echo Third-party components and asset terms: THIRD-PARTY-NOTICES.md
>>"%R%" echo.
>>"%R%" echo WHAT IS IN HERE
>>"%R%" echo.
>>"%R%" echo   hackli-gui-studio-%VERSION%.jar     the Minecraft mod (put this in your mods folder)
>>"%R%" echo   editor\                        standalone OpenGL editor, no Minecraft needed
>>"%R%" echo   selftest\                      headless self test of the shared core
>>"%R%" echo   run-editor.cmd                 launches the editor with the bundled icon set
>>"%R%" echo   run-selftest.cmd               runs the self test
>>"%R%" echo   examples\                      design documents to open or render
>>"%R%" echo   assets\                        GENERATED item icon set (see ICONS below)
>>"%R%" echo   data\                          YOUR projects/layouts/templates/exports (created on use)
>>"%R%" echo   docs\                          design, architecture, widget plan, licensing
>>"%R%" echo   source\ or the -src.zip        full source tree
>>"%R%" echo.
>>"%R%" echo PORTABLE BY DESIGN
>>"%R%" echo.
>>"%R%" echo   This folder is self-contained. The editor keeps your projects, player layouts,
>>"%R%" echo   templates and exports in data\ next to run-editor.cmd, and writes nothing to
>>"%R%" echo   your home directory - copy or move the whole folder and everything comes with it.
>>"%R%" echo   Use  run-editor.cmd ... --data ^<dir^>  to keep the data somewhere else.
>>"%R%" echo.
>>"%R%" echo REQUIREMENTS
>>"%R%" echo.
>>"%R%" echo   Java 21 or newer for the editor and the self test.
>>"%R%" echo   For the mod itself: Minecraft 1.21.11 + Fabric Loader + Meteor Client 1.21.11.
>>"%R%" echo.
>>"%R%" echo QUICK START - Minecraft
>>"%R%" echo.
>>"%R%" echo   1. Drop hackli-gui-studio-%VERSION%.jar next to Meteor Client in your mods\ folder.
>>"%R%" echo   2. Launch the game and run .hackligui in chat (alias .hgd), or use the
>>"%R%" echo      "gui-designer" module in the GUI Designer category.
>>"%R%" echo   3. Place widgets, set their IDs, then Export Runtime or Export Meteor.
>>"%R%" echo      Generated sources land in config\hackli-gui-studio\export\.
>>"%R%" echo.
>>"%R%" echo QUICK START - editor (no Minecraft required)
>>"%R%" echo.
>>"%R%" echo   run-editor.cmd
>>"%R%" echo   run-editor.cmd examples\widgets.json
>>"%R%" echo   run-editor.cmd examples\widgets.json out.png     (render to a PNG)
>>"%R%" echo.
>>"%R%" echo   F5 switches design and play mode, Ctrl+S saves, Ctrl+Q quits.
>>"%R%" echo   F5-play makes the widgets live: buttons fire, sliders drag, checkboxes
>>"%R%" echo   animate, keybinds capture the next key, tooltips show on hover.
>>"%R%" echo.
>>"%R%" echo   New/Open/Save work on the projects in data\projects\. The in-game designer uses
>>"%R%" echo   the mod's config folder instead; to share one project list, start the editor
>>"%R%" echo   with  --data "%%APPDATA%%\.minecraft\config\hackli-gui-studio".
>>"%R%" echo.
>>"%R%" echo CHECK IT WORKS
>>"%R%" echo.
>>"%R%" echo   run-selftest.cmd
>>"%R%" echo.
>>"%R%" echo   Runs 160 assertions over the document model, Meteor layout, painter
>>"%R%" echo   geometry, widget maths and both exporters. No window, no GPU needed.
>>"%R%" echo.
>>"%R%" echo ICONS - IMPORTANT
>>"%R%" echo.
>>"%R%" echo   This package contains NO Minecraft artwork. assets\ holds a GENERATED
>>"%R%" echo   icon set (icon-atlas.png + icon-atlas.tsv): one synthetic 16x16 icon per
>>"%R%" echo   vanilla item id, coloured by hashing the id. It is my own work, released
>>"%R%" echo   as CC0-1.0, and it is only meant to keep the editor readable offline.
>>"%R%" echo.
>>"%R%" echo   To design against the real item sprites, extract them from a Minecraft
>>"%R%" echo   client jar you already own - locally, for your own use:
>>"%R%" echo.
>>"%R%" echo     gradle :core:extractMcAssets -PclientJar="^<path to 1.21.11.jar^>"
>>"%R%" echo.
>>"%R%" echo   ...then point the editor at that folder with -Dhackli.assets=^<dir^>.
>>"%R%" echo   Extracted textures take priority over the generated icon set.
>>"%R%" echo.
>>"%R%" echo STATUS - WHAT IS NOT DONE
>>"%R%" echo.
>>"%R%" echo   The multi-select list widget (a scrollable list of icons, names and
>>"%R%" echo   checkboxes with grouping and search) and its two-column popup picker are
>>"%R%" echo   not implemented yet. The setting ROW that opens it is: the palette's
>>"%R%" echo   "Settings Row" preset builds [label][Select (N selected)][item][reset].
>>"%R%" echo   See docs\PLAN-WIDGETS.md for the remaining work.
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
if not errorlevel 1 set "GRADLE_CMD=gradle"
exit /b 0
