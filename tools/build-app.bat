@echo off
rem Builds the standalone Windows version of the game: a "Petsa de Peligro"
rem folder with Petsa de Peligro.exe and its own small copy of Java inside,
rem zipped up as release\Petsa de Peligro-<version>.zip. Players unzip it and
rem double-click the exe - they don't need Java installed.
rem
rem Double-click this file to run it. It needs a JDK 14 or newer (it uses the
rem one that comes with NetBeans unless JDK is set to another).

setlocal
set VERSION=1.0.0
set APP_NAME=Petsa de Peligro
if not defined JDK set "JDK=C:\Program Files\Apache NetBeans\jdk"

cd /d "%~dp0.."
set "PROJECT=%CD%"
rem Built outside OneDrive: OneDrive makes synced folders read-only, which breaks rebuilding.
set "WORK=%LOCALAPPDATA%\PetsaDePeligro2\app-build"
set "ZIP=%PROJECT%\release\%APP_NAME%-%VERSION%.zip"

if not exist "%JDK%\bin\jpackage.exe" (
    echo Can't find jpackage in "%JDK%". Set JDK to a JDK 14 or newer.
    goto failed
)
if exist "%WORK%" rmdir /s /q "%WORK%"
mkdir "%WORK%\classes" "%WORK%\jar"

echo [1/4] Compiling the game...
dir /s /b "%PROJECT%\src\*.java" > "%WORK%\sources.txt"
"%JDK%\bin\javac.exe" -encoding UTF-8 -nowarn -d "%WORK%\classes" @"%WORK%\sources.txt"
if errorlevel 1 goto failed
robocopy "%PROJECT%\src" "%WORK%\classes" /E /XF *.java desktop.ini /NFL /NDL /NJH /NJS /NP > nul
if errorlevel 8 goto failed

echo [2/4] Making the jar...
"%JDK%\bin\jar.exe" --create --file "%WORK%\jar\PetsaDePeligro.jar" --main-class petsa.ui.GameApp -C "%WORK%\classes" .
if errorlevel 1 goto failed

echo [3/4] Packaging the app with its own Java (this takes a minute)...
"%JDK%\bin\jpackage.exe" --type app-image --name "%APP_NAME%" --app-version %VERSION% ^
    --description "A 30-day student budget survival game" --vendor "%APP_NAME%" ^
    --input "%WORK%\jar" --main-jar PetsaDePeligro.jar --main-class petsa.ui.GameApp ^
    --icon "%PROJECT%\tools\app-icon.ico" ^
    --add-modules java.base,java.desktop ^
    --jlink-options "--strip-debug --no-header-files --no-man-pages --strip-native-commands" ^
    --dest "%WORK%\app"
if errorlevel 1 goto failed

echo [4/4] Zipping...
if not exist "%PROJECT%\release" mkdir "%PROJECT%\release"
if exist "%ZIP%" del "%ZIP%"
pushd "%WORK%\app"
"%SystemRoot%\System32\tar.exe" -a -c -f "%ZIP%" "%APP_NAME%"
if errorlevel 1 (
    popd
    goto failed
)
popd

echo.
echo Done: %ZIP%
echo (unzipped copy to try right away: %WORK%\app\%APP_NAME%\%APP_NAME%.exe)
goto end

:failed
echo.
echo Build failed - see the messages above.

:end
pause
