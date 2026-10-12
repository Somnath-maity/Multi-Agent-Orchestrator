@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM Begin all REM://
@echo off
@REM set title of command window
title %0
@REM enable extensions
setlocal enableextensions enabledelayedexpansion

set MAVEN_PROJECTBASEDIR=%~dp0
set MAVEN_WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties

@REM Default Maven version
set DEFAULT_MVN_VERSION=3.9.9

@REM Read distributionUrl from maven-wrapper.properties
set DOWNLOAD_URL=
if exist "%MAVEN_WRAPPER_PROPERTIES%" (
    for /f "tokens=1,2 delims==" %%A in (%MAVEN_WRAPPER_PROPERTIES%) do (
        if "%%A"=="distributionUrl" set DOWNLOAD_URL=%%B
    )
)

if "%DOWNLOAD_URL%"=="" (
    set DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%DEFAULT_MVN_VERSION%/apache-maven-%DEFAULT_MVN_VERSION%-bin.zip
)

@REM Compute Maven home directory
set MAVEN_HOME_DIR=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%DEFAULT_MVN_VERSION%

@REM Check if Maven is already downloaded
if exist "%MAVEN_HOME_DIR%\bin\mvn.cmd" goto runMaven

@REM Download and extract Maven
echo Downloading Maven from %DOWNLOAD_URL%...
mkdir "%MAVEN_HOME_DIR%" 2>nul

set MAVEN_ZIP=%TEMP%\apache-maven-%DEFAULT_MVN_VERSION%-bin.zip

@REM Use PowerShell to download
powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%MAVEN_ZIP%' }"

if not exist "%MAVEN_ZIP%" (
    echo ERROR: Failed to download Maven.
    exit /b 1
)

@REM Extract using PowerShell
echo Extracting Maven...
powershell -Command "& { Expand-Archive -Path '%MAVEN_ZIP%' -DestinationPath '%USERPROFILE%\.m2\wrapper\dists' -Force }"
del "%MAVEN_ZIP%" 2>nul

if not exist "%MAVEN_HOME_DIR%\bin\mvn.cmd" (
    echo ERROR: Maven extraction failed.
    exit /b 1
)

echo Maven %DEFAULT_MVN_VERSION% installed to %MAVEN_HOME_DIR%

:runMaven
set MAVEN_CMD="%MAVEN_HOME_DIR%\bin\mvn.cmd"

@REM Pass along all command-line arguments
%MAVEN_CMD% %*
