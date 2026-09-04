@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

echo ============================================================
echo   MLH — PASO 3: RESTAURAR pacientes e informes
echo ============================================================
echo.

call :find_adb
if errorlevel 1 goto :no_adb

set PKG=com.mlh.skinanalyzer
set OUT=%~dp0copia

"%ADB%" shell "pm path %PKG%" >nul 2>&1
if errorlevel 1 (
  echo La app nueva NO esta instalada. Ejecute 2-INSTALAR-NUEVA.bat
  pause
  exit /b 1
)

if exist "%OUT%\mlh_datos.tar" goto :restore_tar
if exist "%OUT%\mlh_datos.ab" goto :restore_ab
echo No hay respaldo en %OUT%
pause
exit /b 1

:restore_tar
echo Cerrando la app si esta abierta...
"%ADB%" shell am force-stop %PKG%
echo Subiendo respaldo a la tablet...
"%ADB%" push "%OUT%\mlh_datos.tar" /data/local/tmp/mlh_datos.tar
if errorlevel 1 (
  echo ERROR al subir el archivo.
  pause
  exit /b 1
)
echo Extrayendo datos dentro de la app...
"%ADB%" shell "run-as %PKG% sh -c 'cd /data/data/%PKG% && tar -xf /data/local/tmp/mlh_datos.tar'"
if errorlevel 1 (
  echo.
  echo FALLO run-as. Abra la app nueva UNA vez en la tablet,
  echo ciierrela por completo, y vuelva a ejecutar este archivo.
  pause
  exit /b 1
)
"%ADB%" shell "rm /data/local/tmp/mlh_datos.tar" >nul 2>&1
echo.
echo ============================================================
echo   RESTAURADO. Abra la app y revise Pacientes.
echo ============================================================
pause
exit /b 0

:restore_ab
echo Restaurando desde mlh_datos.ab (modo B)...
echo En la TABLET pulse Restaurar / Restore cuando aparezca.
"%ADB%" restore "%OUT%\mlh_datos.ab"
echo.
echo Listo. Abra la app y compruebe Pacientes.
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
