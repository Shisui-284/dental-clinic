@echo off
cd /d "%~dp0"
echo ====================================================
echo  Khoi dong CHIA Dental Clinic (Backend + Frontend)
echo ====================================================
start "CHIA Backend (8080)" cmd /k "cd /d "%~dp0backend" && call run-backend.bat"
start "CHIA Frontend (5173)" cmd /k "cd /d "%~dp0frontend" && call run-frontend.bat"
echo Dang cho Backend san sang tren cong 8080...
timeout /t 10 /nobreak >nul
start "" "http://localhost:5173/?mode=live&api=http://localhost:8080#/k1"
echo Da mo trinh duyet: http://localhost:5173/?mode=live&api=http://localhost:8080#/k1
echo Cong nhan su (Le tan / Bac si / Quan ly): http://localhost:5173/?mode=live&api=http://localhost:8080#/login

