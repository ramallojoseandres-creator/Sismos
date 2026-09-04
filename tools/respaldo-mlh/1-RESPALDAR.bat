@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

echo ============================================================
echo   MLH — PASO 1: RESPALDAR datos de la app ACTUAL
echo ============================================================
echo.
echo   Deje la app vieja INSTALADA. No la borre todavia.
echo   Cable USB conectado. Depuracion USB activada.
echo.

call :find_adb
if errorlevel 1 goto :no_adb

echo [OK] adb: %ADB%
echo.
"%ADB%" start-server >nul 2>&1
"%ADB%" devices
echo.
echo Si arriba no aparece un dispositivo "device", revise el cable
echo y acepte el aviso de depuracion en la tablet.
echo.
pause

set PKG=com.mlh.skinanalyzer
set OUT=%~dp0copia
if not exist "%OUT%" mkdir "%OUT%"

echo.
echo Probando acceso a la app (%PKG%)...
"%ADB%" shell "run-as %PKG% ls" >nul 2>&1
if errorlevel 1 (
  echo.
  echo No se pudo usar run-as. Probando modo B (adb backup)...
  goto :mode_b
)

echo [OK] Acceso a datos de la app.
echo Copiando base de datos, fotos e informes al PC...
echo.

"%ADB%" exec-out "run-as %PKG% tar -c databases files shared_prefs 2>/dev/null" > "%OUT%\mlh_datos.tar"
if errorlevel 1 (
  echo ERROR al copiar. Intentando modo B...
  goto :mode_b
)

for %%A in ("%OUT%\mlh_datos.tar") do set SZ=%%~zA
if "%SZ%"=="0" (
  echo El archivo salio vacio. Intentando modo B...
  goto :mode_b
)

echo.
echo ============================================================
echo   RESPALDO LISTO
echo   Archivo: %OUT%\mlh_datos.tar
echo   Tamaño:  %SZ% bytes
echo.
echo   Siguiente paso: 2-INSTALAR-NUEVA.bat
echo ============================================================
pause
exit /b 0

:mode_b
echo.
echo === MODO B: adb backup ===
echo En la TABLET le saldra una pantalla. Pulse aceptar / hacer copia.
echo Deje la contraseña en blanco si se lo pide.
echo.
"%ADB%" backup -f "%OUT%\mlh_datos.ab" -noapk %PKG%
if not exist "%OUT%\mlh_datos.ab" (
  echo ERROR: no se creo el respaldo.
  pause
  exit /b 1
)
for %%A in ("%OUT%\mlh_datos.ab") do set SZ=%%~zA
if "%SZ%"=="0" (
  echo ERROR: respaldo vacio. No desinstale la app.
  pause
  exit /b 1
)
echo.
echo RESPALDO LISTO (modo B): %OUT%\mlh_datos.ab  (%SZ% bytes)
echo Siguiente: 2-INSTALAR-NUEVA.bat
pause
exit /b 0

:find_adb
where adb >nul 2>&1
if not errorlevel 1 (
  set ADB=adb
  exit /b 0
)
if exist "%~dp0adb.exe" (
  set ADB=%~dp0adb.exe
  exit /b 0
)
if exist "%~dp0platform-tools\adb.exe" (
  set ADB=%~dp0platform-tools\adb.exe
  exit /b 0
)
exit /b 1

:no_adb
echo.
echo NO se encontro adb.
echo 1. Descargue Platform Tools:
echo    https://developer.android.com/tools/releases/platform-tools
echo 2. Descomprima y copie adb.exe (y las DLL) a esta carpeta:
echo    %~dp0
echo 3. Vuelva a ejecutar este archivo.
echo.
pause
exit /b 1
