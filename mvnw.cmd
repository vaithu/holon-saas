@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one or more
@REM contributor license agreements. See the NOTICE file distributed with this
@REM work for additional information regarding copyright ownership. The ASF
@REM licenses this file to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM http://www.apache.org/licenses/LICENSE-2.0
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
@REM WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
@REM License for the specific language governing permissions and limitations under
@REM the License.
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET ___MVNW_GSON__=
@SETLOCAL

@SET WRAPPER_PROPERTIES=.mvn\wrapper\maven-wrapper.properties
@SET MAVEN_USER_HOME=%USERPROFILE%\.m2\wrapper\dists

@FOR /F "usebackq tokens=2 delims==" %%i IN (`findstr /b "distributionUrl" %WRAPPER_PROPERTIES%`) DO @SET DISTRIBUTION_URL=%%i
@SET DISTRIBUTION_URL=%DISTRIBUTION_URL: =%

@FOR %%i IN ("%DISTRIBUTION_URL%") DO @SET DIST_FILENAME=%%~ni
@REM NOTE: use a private variable name here (not MAVEN_HOME) so we don't leak a
@REM stale/incorrect MAVEN_HOME into the environment of the real mvn.cmd we invoke below.
@SET MVNW_DIST_DIR=%MAVEN_USER_HOME%\%DIST_FILENAME%
@SET MAVEN_HOME=

@IF NOT EXIST "%MVNW_DIST_DIR%" (
    @echo Downloading Maven from %DISTRIBUTION_URL%
    @IF NOT EXIST "%MAVEN_USER_HOME%" mkdir "%MAVEN_USER_HOME%"
    @powershell -Command "Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%TEMP%\maven.zip'"
    @powershell -Command "Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath '%MVNW_DIST_DIR%' -Force"
    @del /q "%TEMP%\maven.zip"
)

@FOR /R "%MVNW_DIST_DIR%" %%f IN (mvn.cmd) DO @IF EXIST "%%f" (SET "MVN_EXEC=%%f"&GOTO :found)
:found
@CALL "%MVN_EXEC%" %*
@ENDLOCAL & EXIT /B %ERRORLEVEL%







