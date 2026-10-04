@echo off
rem Builds the standalone Windows version of the game, with its own small copy
rem of Java inside (players don't need Java installed), into release\:
rem
rem   PetsaDePeligro-<version>-setup.exe     installer: setup wizard, Start-menu
rem                                         and desktop shortcuts, uninstaller
rem   PetsaDePeligro-<version>-portable.zip  no install: unzip, double-click
rem                                         "Petsa de Peligro.exe"
rem
rem Double-click this file to run it. It needs a JDK 14 or newer (it uses the
rem one that comes with NetBeans unless JDK is set to another) and, for the
rem installer only, WiX Toolset 3.14 (without it, just the zip is made).

setlocal
set VERSION=1.1.0
set APP_NAME=Petsa de Peligro
rem Never change this: it's how Windows knows a new version's installer
rem should replace the old one instead of installing a second copy.
set UPGRADE_UUID=7ae4d0ef-7548-4513-9531-09b10eff0faf
if not defined JDK set "JDK=C:\Program Files\Apache NetBeans\jdk"
if not defined WIX set "WIX=C:\Program Files (x86)\WiX Toolset v3.14\"

cd /d "%~dp0.."
set "PROJECT=%CD%"
rem Built outside OneDrive: OneDrive makes synced folders read-only, which breaks rebuilding.
set "WORK=%LOCALAPPDATA%\PetsaDePeligro2\app-build"
set "RELEASE=%PROJECT%\release"
set "ZIP=%RELEASE%\PetsaDePeligro-%VERSION%-portable.zip"
set "SETUP=%RELEASE%\PetsaDePeligro-%VERSION%-setup.exe"

if not exist "%JDK%\bin\jpackage.exe" (
    echo Can't find jpackage in "%JDK%". Set JDK to a JDK 14 or newer.
    goto failed
)
if exist "%WORK%" rmdir /s /q "%WORK%"
mkdir "%WORK%\classes" "%WORK%\jar"
if not exist "%RELEASE%" mkdir "%RELEASE%"

echo [1/5] Compiling the game...
dir /s /b "%PROJECT%\src\*.java" > "%WORK%\sources.txt"
"%JDK%\bin\javac.exe" -encoding UTF-8 -nowarn -d "%WORK%\classes" @"%WORK%\sources.txt"
if errorlevel 1 goto failed
robocopy "%PROJECT%\src" "%WORK%\classes" /E /XF *.java desktop.ini /NFL /NDL /NJH /NJS /NP > nul
if errorlevel 8 goto failed

echo [2/5] Making the jar...
"%JDK%\bin\jar.exe" --create --file "%WORK%\jar\PetsaDePeligro.jar" --main-class petsa.ui.GameApp -C "%WORK%\classes" .
if errorlevel 1 goto failed

echo [3/5] Packaging the app with its own Java...
"%JDK%\bin\jpackage.exe" --type app-image --name "%APP_NAME%" --app-version %VERSION% ^
    --description "A 30-day student budget survival game" --vendor "%APP_NAME%" ^
    --input "%WORK%\jar" --main-jar PetsaDePeligro.jar --main-class petsa.ui.GameApp ^
    --icon "%PROJECT%\tools\app-icon.ico" ^
    --add-modules java.base,java.desktop ^
    --jlink-options "--strip-debug --no-header-files --no-man-pages --strip-native-commands" ^
    --dest "%WORK%\app"
if errorlevel 1 goto failed

echo [4/5] Zipping the portable version...
if exist "%ZIP%" del "%ZIP%"
pushd "%WORK%\app"
"%SystemRoot%\System32\tar.exe" -a -c -f "%ZIP%" "%APP_NAME%"
if errorlevel 1 (
    popd
    goto failed
)
popd

echo [5/5] Making the installer...
if not exist "%WIX%bin\candle.exe" (
    echo WiX Toolset 3.14 isn't installed, so no installer this time - the zip is ready.
    goto done
)
set "PATH=%WIX%bin;%PATH%"
rem Installs for the current user only (in their AppData), so it needs no admin rights.
"%JDK%\bin\jpackage.exe" --type exe --app-image "%WORK%\app\%APP_NAME%" ^
    --name "%APP_NAME%" --app-version %VERSION% ^
    --description "A 30-day student budget survival game" --vendor "%APP_NAME%" ^
    --win-per-user-install --win-dir-chooser ^
    --win-menu --win-menu-group "%APP_NAME%" --win-shortcut --win-shortcut-prompt ^
    --win-upgrade-uuid %UPGRADE_UUID% ^
    --dest "%WORK%\installer"
if errorlevel 1 goto failed
if exist "%SETUP%" del "%SETUP%"
move "%WORK%\installer\%APP_NAME%-%VERSION%.exe" "%SETUP%" > nul
if errorlevel 1 goto failed

:done
echo.
echo Done - in %RELEASE%:
dir /b "%RELEASE%\PetsaDePeligro-%VERSION%-*"
goto end

:failed
echo.
echo Build failed - see the messages above.

:end
pause
