@echo off
rem Build ReCatch prototype APK (Windows). Output: app\build\outputs\apk\debug\app-debug.apk
rem Defaults to the E:\bt toolchain on this PC. Elsewhere set JAVA_HOME / ANDROID_HOME / GRADLE first.
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME set "JAVA_HOME=E:\bt\jdk17"
if not defined ANDROID_HOME set "ANDROID_HOME=E:\bt\sdk"
if not defined GRADLE_USER_HOME if exist "E:\bt\gradle-home" set "GRADLE_USER_HOME=E:\bt\gradle-home"
if not defined GRADLE (if exist "E:\bt\gradle\bin\gradle.bat" (set "GRADLE=E:\bt\gradle\bin\gradle.bat") else (set "GRADLE=gradle"))
if not exist local.properties (
  >local.properties echo sdk.dir=%ANDROID_HOME:\=/%
)
call "%GRADLE%" --no-daemon assembleDebug %*
