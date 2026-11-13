@echo off
cd /d "%~dp0"
if "%JAVA_HOME%"=="" set "JAVA_HOME=C:\Program Files\Java\jdk-24"
echo Dang khoi dong CHIA Dental Backend Prototype...
if not exist "target\dental-backend-0.1.0-SNAPSHOT.jar" (
    call mvnw.cmd package -DskipTests
)
"%JAVA_HOME%\bin\java.exe" -jar "target\dental-backend-0.1.0-SNAPSHOT.jar"
pause

