@rem Gradle 启动脚本（Windows）
@if "%DEBUG%"=="" @echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute
echo. 1>&2
echo 错误: 未找到 Java。请先安装 JDK 17 并设置 JAVA_HOME。 1>&2
goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe
if exist "%JAVA_EXE%" goto execute
echo. 1>&2
echo 错误: JAVA_HOME 指向无效路径: %JAVA_HOME% 1>&2
goto fail

:execute
"%JAVA_EXE%" -Xmx64m -Xms64m "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
exit /b 1

:mainEnd
endlocal
