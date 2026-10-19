@echo off
setlocal
cd /d "%~dp0"

rem Pastikan package dari folder src dapat ditemukan.
set "PYTHONPATH=%CD%\src;%PYTHONPATH%"

rem Jalankan memakai Python Launcher jika tersedia, lalu fallback ke python.
where py >nul 2>&1
if %errorlevel% equ 0 (
    py -3 -m aethera_quest %*
) else (
    python -m aethera_quest %*
)

set "AETHERA_QUEST_EXIT=%errorlevel%"
if not "%AETHERA_QUEST_EXIT%"=="0" (
    echo.
    echo [ERROR] Aethera Quest berhenti dengan kode %AETHERA_QUEST_EXIT%.
    pause
)
exit /b %AETHERA_QUEST_EXIT%
