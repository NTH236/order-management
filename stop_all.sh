#!/usr/bin/env bash
# ==============================================================================
#   SOA ORDER MANAGEMENT SYSTEM - STOP ALL SERVICES SCRIPT
# ==============================================================================

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"${BASE_DIR}/run_all.sh" stop

