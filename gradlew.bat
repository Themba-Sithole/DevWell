@ECHO OFF
SETLOCAL
SET "APP_HOME=%~dp0"
SET "JAVA_EXE=java.exe"
IF DEFINED JAVA_HOME SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
EXIT /B %ERRORLEVEL%
