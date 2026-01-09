@echo off
setlocal enabledelayedexpansion

REM 繁體中文註解：Android 品質裁判（Lint + Unit Test），讓 AI 產出的結論可驗證
echo == Lint ==
call gradlew.bat lintDebug || exit /b 1

echo == Unit Tests ==
call gradlew.bat testDebugUnitTest || exit /b 1

REM 繁體中文註解：如果你有 detekt/ktlint 就自動跑，沒有就略過
call gradlew.bat tasks --all | findstr /R /C:"^detekt" >nul
if %errorlevel%==0 (
  echo == Detekt ==
  call gradlew.bat detekt || exit /b 1
)

call gradlew.bat tasks --all | findstr /R /C:"^ktlintCheck" >nul
if %errorlevel%==0 (
  echo == Ktlint ==
  call gradlew.bat ktlintCheck || exit /b 1
)

echo == Done ==
exit /b 0
