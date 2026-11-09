@echo off
REM ==============================================================================
REM   SOA ORDER MANAGEMENT SYSTEM - WINDOWS LAUNCHER SCRIPT
REM   Starts Auth Service (8081), Product Service (8082), Order Service (8083)
REM ==============================================================================
title SOA Order Management Launcher
color 0A

echo ===============================================================================
echo     STARTING SERVICE-ORIENTED ARCHITECTURE (SOA) ORDER MANAGEMENT SYSTEM
echo ===============================================================================
echo.

REM Verify Java installation
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java 21 is not found in PATH! Please install Java 21 LTS.
    pause
    exit /b 1
)

REM Verify Maven installation
mvn -v >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven is not found in PATH! Please install Apache Maven.
    pause
    exit /b 1
)

set BASE_DIR=%~dp0

REM Build JAR files if not already built
if not exist "%BASE_DIR%auth-service\target\auth-service-1.0.0.jar" (
    echo [INFO] Building executable JAR files for all services...
    call mvn clean package -DskipTests
)

echo [1/3] Launching Auth Service (Port: 8081)...
start "Auth-Service (Port 8081)" cmd /k "cd /d "%BASE_DIR%auth-service" && java -jar target\auth-service-1.0.0.jar"
timeout /t 3 /nobreak >nul

echo [2/3] Launching Product Service (Port: 8082)...
start "Product-Service (Port 8082)" cmd /k "cd /d "%BASE_DIR%product-service" && java -jar target\product-service-1.0.0.jar"
timeout /t 3 /nobreak >nul

echo [3/3] Launching Order Service (Port: 8083)...
start "Order-Service (Port 8083)" cmd /k "cd /d "%BASE_DIR%order-service" && java -jar target\order-service-1.0.0.jar"

echo.
echo ===============================================================================
echo  All 3 services have been initiated in separate Command Prompt windows!
echo ===============================================================================
echo  - Auth Service Swagger UI:    http://localhost:8081/swagger-ui.html
echo  - Product Service Swagger UI: http://localhost:8082/swagger-ui.html
echo  - Order Service Swagger UI:   http://localhost:8083/swagger-ui.html
echo  - Order Service H2 Console:   http://localhost:8083/h2-console
echo.
echo  To run the automated test suite, open Git Bash or WSL and run:
echo    bash postman_or_curl_tests.sh
echo ===============================================================================
pause
