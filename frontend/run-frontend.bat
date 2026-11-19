@echo off
cd /d "%~dp0"
echo Dang khoi dong CHIA Dental Frontend (http://localhost:5173)...
where python >nul 2>nul
if %errorlevel%==0 (
    python -m http.server 5173
) else (
    npx --yes serve . -l 5173
)
pause

