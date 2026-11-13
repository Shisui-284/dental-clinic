@echo off
if "%JAVA_HOME%"=="" set "JAVA_HOME=C:\Program Files\Java\jdk-24"
set "MAVEN_HOME=%USERPROFILE%\.m2\apache-maven-3.9.9"
call "%MAVEN_HOME%\bin\mvn.cmd" %*
