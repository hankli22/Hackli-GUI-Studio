@echo off
rem ============================================================
rem  Hackli GUI Studio - OpenGL "comet" editor + simulator
rem    run-gl.cmd                       live editor, examples/welcome.json
rem    run-gl.cmd <design.json>         live editor with your design
rem    run-gl.cmd <design.json> play    start in play mode (live widgets)
rem    run-gl.cmd <design.json> <out.png>   render offscreen to PNG
rem
rem  In the live window (auto-sized to your monitor, DPI aware):
rem    palette on the left adds widgets / templates
rem    tree + inspector on the right, every property editable
rem    drag = reorder in a flow panel, alt+drag = free placement
rem    8 resize handles, edge snapping, marquee + ctrl multi-select
rem    double-click = edit text, Space = check box, [ ] = slider value
rem    arrows nudge, Ctrl+D duplicate, Del remove
rem    Ctrl+scroll = zoom canvas, scroll = pan, middle-drag = pan
rem    UI - / UI + resize the editor chrome (also --ui-scale N)
rem    Align menu (8 ops), Export menu (Java / Meteor / JSON)
rem    New / Open / Save / Save As, Ctrl+Z / Ctrl+Y, Ctrl+S
rem    F5 toggles design <-> play (widgets behave like the real click GUI)

rem  Esc closes menus / cancels editing; Ctrl+Q or the window X quits.
rem ============================================================
setlocal EnableExtensions
cd /d "%~dp0"

call :find_gradle
if not defined GRADLE_CMD (
    echo [ERROR] No Gradle found.
    pause
    exit /b 1
)

call "%GRADLE_CMD%" :simulator-gl:fatJar -q
if errorlevel 1 (
    echo [ERROR] Build failed.
    pause
    exit /b 1
)

set "JAR=simulator-gl\build\libs\simulator-gl-0.2.0-all.jar"
set "DESIGN=%~1"
if "%DESIGN%"=="" (
    rem Never edit the shipped example: work on a scratch copy instead.
    if not exist "build" mkdir "build"
    if not exist "build\welcome-edit.json" copy /y "examples\welcome.json" "build\welcome-edit.json" >nul
    set "DESIGN=build\welcome-edit.json"
)

rem Projects, layouts, templates and exports live in data\ next to this script, so
rem the whole checkout can be copied (or put on a USB stick) with its designs.
rem Note: no outer quotes on the set command, otherwise the value keeps the
rem inner double quotes and Java would reject the path.
set DATA=-Dhackli.home="%~dp0data"

if /i "%~2"=="play" (
    echo === OpenGL editor: %DESIGN% ^(play mode, F5 = design, Ctrl+Q quits^) ===
    java %DATA% -cp "%JAR%" com.hackli.guidesigner.gl.GlSimulator "%DESIGN%" --play
) else if "%~2"=="" (
    echo === OpenGL editor: %DESIGN% ^(F5 = play, Ctrl+Q quits^) ===
    java %DATA% -cp "%JAR%" com.hackli.guidesigner.gl.GlSimulator "%DESIGN%"
) else (
    echo === Rendering %DESIGN% -^> %~2 ===
    java %DATA% -cp "%JAR%" com.hackli.guidesigner.gl.GlSimulator --render "%DESIGN%" "%~2"
    echo.
    pause
)

endlocal
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
