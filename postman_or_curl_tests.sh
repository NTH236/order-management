#!/usr/bin/env bash
# ==============================================================================
#   SOA ORDER MANAGEMENT SYSTEM - AUTOMATED INTEGRATION TEST SUITE
#   Tests end-to-end flow across Auth Service, Product Service & Order Service
# ==============================================================================

set -e

# Configuration
AUTH_URL="http://localhost:8081"
PRODUCT_URL="http://localhost:8082"
ORDER_URL="http://localhost:8083"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

PASSED_COUNT=0
FAILED_COUNT=0

assert_test() {
    local test_name="$1"
    local expected="$2"
    local actual="$3"

    if [ "$expected" == "$actual" ]; then
        echo -e "  [${GREEN}PASS${NC}] ${test_name} (Expected: '${expected}', Got: '${actual}')"
        PASSED_COUNT=$((PASSED_COUNT + 1))
    else
        echo -e "  [${RED}FAIL${NC}] ${test_name} (Expected: '${expected}', Got: '${actual}')"
        FAILED_COUNT=$((FAILED_COUNT + 1))
    fi
}

echo -e "${BLUE}===============================================================================${NC}"
echo -e "${BOLD}${CYAN}   AUTOMATED INTEGRATION & CONTRACT TEST SUITE FOR SOA ORDER SYSTEM   ${NC}"
echo -e "${BLUE}===============================================================================${NC}\n"

# ------------------------------------------------------------------------------
# STEP 1: Verify All Services are Online
# ------------------------------------------------------------------------------
echo -e "${BOLD}[STEP 1] Checking Service Health Endpoints...${NC}"

AUTH_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "${AUTH_URL}/api/auth/health" 2>/dev/null || true)
assert_test "Auth Service Health (Port 8081)" "200" "${AUTH_HEALTH}"

PRODUCT_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "${PRODUCT_URL}/products/health" 2>/dev/null || true)
assert_test "Product Service Health (Port 8082)" "200" "${PRODUCT_HEALTH}"

ORDER_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "${ORDER_URL}/orders/health" 2>/dev/null || true)
assert_test "Order Service Health (Port 8083)" "200" "${ORDER_HEALTH}"

if [ "${FAILED_COUNT}" -gt 0 ]; then
    echo -e "\n${RED}[ERROR] One or more services are not running. Please start services first via ./run_all.sh${NC}"
    exit 1
fi

# ------------------------------------------------------------------------------
# STEP 2: Test Security & Authentication Enforcement on Order Service
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[STEP 2] Verifying Security Enforcement (Bearer Token Protection)...${NC}"

# Request without Authorization Header
NO_TOKEN_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${ORDER_URL}/orders")
assert_test "Request without token rejected with 401 Unauthorized" "401" "${NO_TOKEN_STATUS}"

# Request with Bogus Token
BAD_TOKEN_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer invalid.jwt.token" "${ORDER_URL}/orders")
assert_test "Request with invalid token rejected with 401 Unauthorized" "401" "${BAD_TOKEN_STATUS}"

# ------------------------------------------------------------------------------
# STEP 3: Authenticate with Auth Service and Obtain JWT
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[STEP 3] Authenticating with Auth Service (POST /api/auth/login)...${NC}"

LOGIN_RESPONSE=$(curl -s -X POST "${AUTH_URL}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}')

JWT_TOKEN=$(echo "${LOGIN_RESPONSE}" | jq -r '.data.token')
TOKEN_TYPE=$(echo "${LOGIN_RESPONSE}" | jq -r '.data.type')

if [ -n "${JWT_TOKEN}" ] && [ "${JWT_TOKEN}" != "null" ]; then
    assert_test "JWT Token generation for user 'admin'" "Bearer" "${TOKEN_TYPE}"
else
    echo -e "  [${RED}FAIL${NC}] Failed to obtain JWT token from Auth Service. Response: ${LOGIN_RESPONSE}"
    exit 1
fi

# Validate token via Auth Service Inter-service endpoint
VALIDATE_RES=$(curl -s "${AUTH_URL}/api/auth/validate" -H "Authorization: Bearer ${JWT_TOKEN}")
IS_VALID=$(echo "${VALIDATE_RES}" | jq -r '.data.valid')
assert_test "Token verification via Auth Service /api/auth/validate" "true" "${IS_VALID}"

AUTH_HEADER="Authorization: Bearer ${JWT_TOKEN}"

# ------------------------------------------------------------------------------
# STEP 4: Inspect Initial Inventory from Product Service
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[STEP 4] Querying Product Catalog & Inventory (GET /products)...${NC}"

PROD1_RES=$(curl -s "${PRODUCT_URL}/products/1")
PROD1_NAME=$(echo "${PROD1_RES}" | jq -r '.data.name')
PROD1_PRICE=$(echo "${PROD1_RES}" | jq -r '.data.price')
PROD1_STOCK=$(echo "${PROD1_RES}" | jq -r '.data.stockQuantity')

assert_test "Product 1 exists and is 'Laptop Dell XPS 15'" "Laptop Dell XPS 15" "${PROD1_NAME}"
echo -e "         Initial Laptop Dell XPS 15 -> Unit Price: $${PROD1_PRICE}, Stock: ${PROD1_STOCK}"

# ------------------------------------------------------------------------------
# STEP 5: Create Order with Sufficient Inventory
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[STEP 5] Creating New Order via Order Service (POST /orders)...${NC}"

ORDER_PAYLOAD=$(cat <<EOF
{
  "customerName": "Nguyen Van An",
  "customerEmail": "an.nguyen@example.com",
  "items": [
    {
      "productId": 1,
      "quantity": 2
    }
  ]
}
EOF
)

CREATE_ORDER_RES=$(curl -s -X POST "${ORDER_URL}/orders" \
    -H "Content-Type: application/json" \
    -H "${AUTH_HEADER}" \
    -d "${ORDER_PAYLOAD}")

ORDER_STATUS_CODE=$(echo "${CREATE_ORDER_RES}" | jq -r '.status')
ORDER_ID=$(echo "${CREATE_ORDER_RES}" | jq -r '.data.id')
ORDER_TOTAL=$(echo "${CREATE_ORDER_RES}" | jq -r '.data.totalAmount')
ORDER_STATE=$(echo "${CREATE_ORDER_RES}" | jq -r '.data.status')

assert_test "Order creation response status code" "201" "${ORDER_STATUS_CODE}"
assert_test "Order state initialized to 'pending'" "pending" "${ORDER_STATE}"
assert_test "Calculated total amount (2 x 1500.00 = 3000.00)" "3000.00" "${ORDER_TOTAL}"
echo -e "         Created Order ID: #${ORDER_ID}"

# Verify stock deducted in Product Service (10 - 2 = 8)
NEW_PROD1_STOCK=$(curl -s "${PRODUCT_URL}/products/1" | jq -r '.data.stockQuantity')
assert_test "Product 1 inventory deducted from 10 to 8" "8" "${NEW_PROD1_STOCK}"

# ------------------------------------------------------------------------------
# STEP 6: Attempt Order with Insufficient Stock (Negative Test Case)
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[STEP 6] Testing Insufficient Stock Rejection (Negative Scenario)...${NC}"

OUT_OF_STOCK_PAYLOAD=$(cat <<EOF
{
  "customerName": "Tran Thi Binh",
  "customerEmail": "binh.tran@example.com",
  "items": [
    {
      "productId": 5,
      "quantity": 1
    }
  ]
}
EOF
)

OUT_OF_STOCK_RES=$(curl -s -X POST "${ORDER_URL}/orders" \
    -H "Content-Type: application/json" \
    -H "${AUTH_HEADER}" \
    -d "${OUT_OF_STOCK_PAYLOAD}")

OUT_STATUS=$(echo "${OUT_OF_STOCK_RES}" | jq -r '.status')
assert_test "Order with out-of-stock item rejected with 400 Bad Request" "400" "${OUT_STATUS}"

# ------------------------------------------------------------------------------
# STEP 7: Retrieve Order Details and List All Orders
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[STEP 7] Querying Orders (GET /orders, GET /orders/{id})...${NC}"

GET_ORDER_RES=$(curl -s "${ORDER_URL}/orders/${ORDER_ID}" -H "${AUTH_HEADER}")
GET_ORDER_CUST=$(echo "${GET_ORDER_RES}" | jq -r '.data.customerName')
assert_test "Retrieve order #${ORDER_ID} by ID" "Nguyen Van An" "${GET_ORDER_CUST}"

ALL_ORDERS_RES=$(curl -s "${ORDER_URL}/orders" -H "${AUTH_HEADER}")
ORDER_COUNT=$(echo "${ALL_ORDERS_RES}" | jq -r '.data | length')
[ "${ORDER_COUNT}" -ge 1 ] && ORDER_COUNT_RESULT="ge_1" || ORDER_COUNT_RESULT="0"
assert_test "Retrieve all orders returns array" "ge_1" "${ORDER_COUNT_RESULT}"

# ------------------------------------------------------------------------------
# STEP 8: Manage Order Items (POST, GET, PUT, DELETE /order_items)
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[STEP 8] Managing Order Items...${NC}"

# 8.1 Add New Item: Product 3 (Mechanical Keyboard Keychron Q1, price: 180, quantity: 1)
ADD_ITEM_PAYLOAD=$(cat <<EOF
{
  "orderId": ${ORDER_ID},
  "productId": 3,
  "quantity": 1
}
EOF
)

ADD_ITEM_RES=$(curl -s -X POST "${ORDER_URL}/order_items" \
    -H "Content-Type: application/json" \
    -H "${AUTH_HEADER}" \
    -d "${ADD_ITEM_PAYLOAD}")

ITEM_ID=$(echo "${ADD_ITEM_RES}" | jq -r '.data.id')
ITEM_PROD_NAME=$(echo "${ADD_ITEM_RES}" | jq -r '.data.productName')
assert_test "Add order item (Keychron Q1)" "Mechanical Keyboard Keychron Q1" "${ITEM_PROD_NAME}"

# Verify parent order total updated: 3000 + 180 = 3180.00
UPDATED_ORDER_TOTAL=$(curl -s "${ORDER_URL}/orders/${ORDER_ID}" -H "${AUTH_HEADER}" | jq -r '.data.totalAmount')
assert_test "Parent order total updated after adding item (3180.00)" "3180.00" "${UPDATED_ORDER_TOTAL}"

# 8.2 Update Item Quantity: Increase to 2 (2 x 180 = 360)
UPDATE_ITEM_RES=$(curl -s -X PUT "${ORDER_URL}/order_items/${ITEM_ID}" \
    -H "Content-Type: application/json" \
    -H "${AUTH_HEADER}" \
    -d '{"quantity": 2}')

ITEM_NEW_QTY=$(echo "${UPDATE_ITEM_RES}" | jq -r '.data.quantity')
ITEM_NEW_TOTAL=$(echo "${UPDATE_ITEM_RES}" | jq -r '.data.totalPrice')
assert_test "Update item quantity to 2" "2" "${ITEM_NEW_QTY}"
assert_test "Item total price recalculated (360.00)" "360.00" "${ITEM_NEW_TOTAL}"

UPDATED_ORDER_TOTAL2=$(curl -s "${ORDER_URL}/orders/${ORDER_ID}" -H "${AUTH_HEADER}" | jq -r '.data.totalAmount')
assert_test "Parent order total updated after quantity increase (3360.00)" "3360.00" "${UPDATED_ORDER_TOTAL2}"

# 8.3 Delete Order Item: removes keyboard item, restores stock to Product Service
INITIAL_PROD3_STOCK=$(curl -s "${PRODUCT_URL}/products/3" | jq -r '.data.stockQuantity')
DELETE_ITEM_RES=$(curl -s -X DELETE "${ORDER_URL}/order_items/${ITEM_ID}" -H "${AUTH_HEADER}")
DELETE_ITEM_STATUS=$(echo "${DELETE_ITEM_RES}" | jq -r '.status')
assert_test "Delete order item response code" "200" "${DELETE_ITEM_STATUS}"

# Verify stock restored in Product Service (was 23, restored +2 -> 25)
RESTORED_PROD3_STOCK=$(curl -s "${PRODUCT_URL}/products/3" | jq -r '.data.stockQuantity')
assert_test "Product 3 inventory restored to 25" "25" "${RESTORED_PROD3_STOCK}"

FINAL_ORDER_TOTAL=$(curl -s "${ORDER_URL}/orders/${ORDER_ID}" -H "${AUTH_HEADER}" | jq -r '.data.totalAmount')
assert_test "Parent order total updated back to 3000.00" "3000.00" "${FINAL_ORDER_TOTAL}"

# ------------------------------------------------------------------------------
# STEP 9: Update Order Status
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[STEP 9] Updating Order Status (PUT /orders/{id})...${NC}"

UPDATE_STATUS_RES=$(curl -s -X PUT "${ORDER_URL}/orders/${ORDER_ID}" \
    -H "Content-Type: application/json" \
    -H "${AUTH_HEADER}" \
    -d '{"status": "completed"}')

UPDATED_STATUS=$(echo "${UPDATE_STATUS_RES}" | jq -r '.data.status')
assert_test "Order status transitioned to 'completed'" "completed" "${UPDATED_STATUS}"

# ------------------------------------------------------------------------------
# STEP 10: Delete Order
# ------------------------------------------------------------------------------
echo -e "\n${BOLD}[STEP 10] Deleting Order (DELETE /orders/{id})...${NC}"

DELETE_ORDER_RES=$(curl -s -X DELETE "${ORDER_URL}/orders/${ORDER_ID}" -H "${AUTH_HEADER}")
DELETE_STATUS=$(echo "${DELETE_ORDER_RES}" | jq -r '.status')
assert_test "Delete order response code" "200" "${DELETE_STATUS}"

VERIFY_DELETED=$(curl -s -o /dev/null -w "%{http_code}" "${ORDER_URL}/orders/${ORDER_ID}" -H "${AUTH_HEADER}")
assert_test "Confirm order is deleted (404 Not Found)" "404" "${VERIFY_DELETED}"

# ------------------------------------------------------------------------------
# FINAL REPORT
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}===============================================================================${NC}"
echo -e "${BOLD}TEST EXECUTION SUMMARY:${NC}"
echo -e "  Total Passed Assertions: ${GREEN}${PASSED_COUNT}${NC}"
echo -e "  Total Failed Assertions: ${RED}${FAILED_COUNT}${NC}"
echo -e "${BLUE}===============================================================================${NC}"

if [ "${FAILED_COUNT}" -eq 0 ]; then
    echo -e "\n${GREEN}${BOLD}✓ ALL INTEGRATION TESTS PASSED PERFECTLY! ARCHITECTURE & BUSINESS RULES VERIFIED.${NC}\n"
    exit 0
else
    echo -e "\n${RED}${BOLD}✗ SOME INTEGRATION TESTS FAILED. PLEASE REVIEW LOGS.${NC}\n"
    exit 1
fi
