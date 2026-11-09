#!/usr/bin/env bash
# ==============================================================================
#   SOA ORDER MANAGEMENT SYSTEM - LINUX / MACOS LAUNCHER SCRIPT
#   Starts Auth Service (8081), Product Service (8082), Order Service (8083)
# ==============================================================================

set -e

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${BASE_DIR}/logs"
PID_FILE="${BASE_DIR}/.running_pids"

mkdir -p "${LOG_DIR}"

# Color codes for pretty terminal output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}===============================================================================${NC}"
echo -e "${GREEN}     STARTING SERVICE-ORIENTED ARCHITECTURE (SOA) ORDER MANAGEMENT SYSTEM     ${NC}"
echo -e "${BLUE}===============================================================================${NC}"

# Handle "stop" argument
if [ "$1" == "stop" ]; then
    echo -e "${YELLOW}Stopping all running services...${NC}"
    if [ -f "${PID_FILE}" ]; then
        while read -r pid; do
            if [ -n "${pid}" ] && kill -0 "${pid}" 2>/dev/null; then
                kill "${pid}" 2>/dev/null && echo -e "Killed process PID: ${pid}"
            fi
        done < "${PID_FILE}"
        rm -f "${PID_FILE}"
    fi
    # Also free ports if any are still occupied
    for port in 8081 8082 8083; do
        pid=$(lsof -ti tcp:${port} 2>/dev/null || true)
        if [ -n "${pid}" ]; then
            kill -9 ${pid} 2>/dev/null || true
            echo -e "Freed port ${port} (PID: ${pid})"
        fi
    done
    echo -e "${GREEN}All services stopped successfully.${NC}"
    exit 0
fi

# Function to check and free port
free_port_if_occupied() {
    local port=$1
    local pid=$(lsof -ti tcp:${port} 2>/dev/null || true)
    if [ -n "${pid}" ]; then
        echo -e "${YELLOW}Port ${port} is currently in use by PID ${pid}. Terminating process...${NC}"
        kill -9 "${pid}" 2>/dev/null || true
        sleep 1
    fi
}

echo -e "\n[0/4] Checking and freeing required ports (8081, 8082, 8083)..."
free_port_if_occupied 8081
free_port_if_occupied 8082
free_port_if_occupied 8083

> "${PID_FILE}"

# Build JARs if any are missing
if [ ! -f "${BASE_DIR}/auth-service/target/auth-service-1.0.0.jar" ] || \
   [ ! -f "${BASE_DIR}/product-service/target/product-service-1.0.0.jar" ] || \
   [ ! -f "${BASE_DIR}/order-service/target/order-service-1.0.0.jar" ]; then
    echo -e "${YELLOW}Building executable JARs for all services...${NC}"
    mvn clean package -DskipTests
fi

# Function to wait for service readiness
wait_for_service() {
    local service_name=$1
    local port=$2
    local health_url=$3
    local max_retries=60
    local retry_count=0

    echo -ne "Waiting for ${service_name} on port ${port} "
    while [ ${retry_count} -lt ${max_retries} ]; do
        if curl -s -f -o /dev/null "${health_url}" 2>/dev/null; then
            echo -e " -> ${GREEN}[ONLINE - READY]${NC}"
            return 0
        fi
        echo -ne "."
        sleep 1
        retry_count=$((retry_count + 1))
    done

    echo -e " -> ${RED}[FAILED / TIMEOUT]${NC}"
    echo -e "${RED}Error: ${service_name} did not become ready in ${max_retries} seconds.${NC}"
    echo -e "${YELLOW}Check log at: ${LOG_DIR}/${service_name}.log${NC}"
    return 1
}

# 1. Start Auth Service (8081)
echo -e "\n${BLUE}[1/3] Starting Auth Service on port 8081...${NC}"
nohup java -jar "${BASE_DIR}/auth-service/target/auth-service-1.0.0.jar" > "${LOG_DIR}/auth-service.log" 2>&1 &
AUTH_PID=$!
echo "${AUTH_PID}" >> "${PID_FILE}"
echo "Auth Service background PID: ${AUTH_PID} (Logs: ${LOG_DIR}/auth-service.log)"

# 2. Start Product Service (8082)
echo -e "\n${BLUE}[2/3] Starting Product Service on port 8082...${NC}"
nohup java -jar "${BASE_DIR}/product-service/target/product-service-1.0.0.jar" > "${LOG_DIR}/product-service.log" 2>&1 &
PRODUCT_PID=$!
echo "${PRODUCT_PID}" >> "${PID_FILE}"
echo "Product Service background PID: ${PRODUCT_PID} (Logs: ${LOG_DIR}/product-service.log)"

# 3. Start Order Service (8083)
echo -e "\n${BLUE}[3/3] Starting Order Service on port 8083...${NC}"
nohup java -jar "${BASE_DIR}/order-service/target/order-service-1.0.0.jar" > "${LOG_DIR}/order-service.log" 2>&1 &
ORDER_PID=$!
echo "${ORDER_PID}" >> "${PID_FILE}"
echo "Order Service background PID: ${ORDER_PID} (Logs: ${LOG_DIR}/order-service.log)"

echo -e "\n${YELLOW}[4/4] Verifying health and connectivity of all services...${NC}"
wait_for_service "Auth-Service" 8081 "http://localhost:8081/api/auth/health"
wait_for_service "Product-Service" 8082 "http://localhost:8082/products/health"
wait_for_service "Order-Service" 8083 "http://localhost:8083/orders/health"

echo -e "\n${GREEN}===============================================================================${NC}"
echo -e "${GREEN}  ALL 3 SERVICES ARE ONLINE AND READY FOR SOA INTERACTION!                     ${NC}"
echo -e "${GREEN}===============================================================================${NC}"
echo -e "  - Auth Service Swagger UI:    ${BLUE}http://localhost:8081/swagger-ui.html${NC}"
echo -e "  - Product Service Swagger UI: ${BLUE}http://localhost:8082/swagger-ui.html${NC}"
echo -e "  - Order Service Swagger UI:   ${BLUE}http://localhost:8083/swagger-ui.html${NC}"
echo -e "  - Order Service H2 Console:   ${BLUE}http://localhost:8083/h2-console${NC} (JDBC URL: jdbc:h2:mem:order_db)"
echo -e "\n  To execute automated end-to-end integration tests, run:"
echo -e "    ${GREEN}./postman_or_curl_tests.sh${NC}"
echo -e "\n  To stop all running services, run:"
echo -e "    ${YELLOW}./run_all.sh stop${NC}  or  ${YELLOW}./stop_all.sh${NC}"
echo -e "${GREEN}===============================================================================${NC}"
