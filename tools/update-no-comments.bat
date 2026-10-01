@echo off
rem Regenerates the no-comments copy from this project and pushes it to GitHub
rem (the "no-comments" branch, checked out in ..\PetsaDePeligro2-NoComments).
rem Commit and push your changes to main first, then double-click this file.

cd /d "%~dp0.."
for /f %%h in ('git rev-parse --short HEAD') do set MAIN_COMMIT=%%h

"C:\Program Files\Apache NetBeans\jdk\bin\java.exe" tools\StripComments.java "..\PetsaDePeligro2-NoComments"
if errorlevel 1 goto done

cd /d "..\PetsaDePeligro2-NoComments"
git add -A
git commit -m "No-comments copy of main %MAIN_COMMIT%"
git push origin no-comments

:done
pause
