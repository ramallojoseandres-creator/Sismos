@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

echo ============================================================
echo   MLH — PASO 2: Desinstalar vieja e instalar APK nueva
echo ============================================================
echo.
echo   IMPORTANTE: Ejecute ANTES 1-RESPALDAR.bat y verifique
echo   que exista la carpeta "copia" con mlh_datos.tar o .ab
echo.

set OUT=%~dp0copia
if exist "%OUT%\mlh_datos.tar" goto :have_backup
if exist "%OUT%\mlh_datos.ab" goto :have_backup
echo ERROR: No hay respaldo en %OUT%
echo Ejecute primero 1-RESPALDAR.bat
pause
exit /b 1

:have_backup
call :find_adb
if errorlevel 1 goto :no_adb

set PKG=com.mlh.skinanalyzer
echo.
set /p APK=Arrastre aqui el APK nuevo y pulse Enter: 
set APK=%APK:"=%
if not exist "%APK%" (
  echo No existe el archivo: %APK%
  pause
  exit /b 1
)

echo.
echo 1) Desinstalando app vieja (%PKG%)...
"%ADB%" uninstall %PKG%
echo.
echo 2) Instalando APK nuevo...
"%ADB%" install -r "%APK%"
if errorlevel 1 (
  echo ERROR al instalar. Revise el APK.
  pause
  exit /b 1
)

echo.
echo ============================================================
echo   App nueva instalada.
echo   Siguiente paso: 3-RESTAURAR.bat
echo ============================================================
pause
exit /b 0

:find_adb
where adb >nul 2>&1
if not errorlevel 1 ( set ADB=adb & exit /b 0 )
if exist "%~dp0adb.exe" ( set ADB=%~dp0adb.exe & exit /b 0 )
if exist "%~dp0platform-tools\adb.exe" ( set ADB=%~dp0platform-tools\adb.exe & exit /b 0 )
exit /b 1

:no_adb
echo No se encontro adb. Vea LEEME.txt
pause
exit /b 1
