#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
CONCURRENT_USERS="${CONCURRENT_USERS:-10}"
REQUESTS_PER_USER="${REQUESTS_PER_USER:-100}"
SENDER_ACCOUNT="${SENDER_ACCOUNT:-8686a341-25a0-43b4-bf3e-2ed5f554452b}"
RECEIVER_ACCOUNT="${RECEIVER_ACCOUNT:-41aee2de-014c-48d4-b0e0-b50a708f5250}"

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

echo "=== Payment Service Stress Test ==="
echo "URL: $BASE_URL"
echo "Concurrent users: $CONCURRENT_USERS"
echo "Requests per user: $REQUESTS_PER_USER"
echo "Total requests: $((CONCURRENT_USERS * REQUESTS_PER_USER))"
echo ""

if ! curl -s "$BASE_URL/actuator/health" | grep -q "UP"; then
    echo -e "${RED}Service is not healthy${NC}"
    exit 1
fi

RESULTS_DIR=$(mktemp -d)
trap "rm -rf $RESULTS_DIR" EXIT

send_payments() {
    local user_id=$1
    local success=0
    local failed=0
    local total_time=0

    for i in $(seq 1 $REQUESTS_PER_USER); do
        idempotency_key="stress-${user_id}-${i}-$(date +%s%N)"
        amount=$(( (RANDOM % 100) + 1 ))

        start_time=$(date +%s%N)

        echo "User $user_id sending payment $i: $amount EUR with Idempotency-Key $idempotency_key"

        response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/payments" \
            -H "Content-Type: application/json" \
            -H "Idempotency-Key: $idempotency_key" \
            -d "{
                \"senderAccountId\": \"$SENDER_ACCOUNT\",
                \"receiverAccountId\": \"$RECEIVER_ACCOUNT\",
                \"amount\": \"$amount\",
                \"currency\": \"EUR\"
            }" 2>/dev/null)

        end_time=$(date +%s%N)
        duration=$(( (end_time - start_time) / 1000000 ))
        total_time=$((total_time + duration))

        http_code=$(echo "$response" | tail -n1)

        echo "User $user_id payment $i completed in ${duration}ms with HTTP code $http_code"
        if [[ "$http_code" == "202" ]]; then
            ((success++))
        else
            ((failed++))
        fi
    done

    echo "$success $failed $total_time" > "$RESULTS_DIR/user_$user_id.txt"
}

echo "Starting stress test..."
start=$(date +%s)

for user in $(seq 1 $CONCURRENT_USERS); do
    send_payments $user &
done

wait

end=$(date +%s)
duration=$((end - start))

total_success=0
total_failed=0
total_time=0

for f in "$RESULTS_DIR"/user_*.txt; do
    read success failed time < "$f"
    total_success=$((total_success + success))
    total_failed=$((total_failed + failed))
    total_time=$((total_time + time))
done

total_requests=$((total_success + total_failed))
avg_time=$((total_time / total_requests))
throughput=$(echo "scale=2; $total_requests / $duration" | bc)

echo ""
echo "=== Results ==="
echo -e "Total requests:  $total_requests"
echo -e "Successful:      ${GREEN}$total_success${NC}"
echo -e "Failed:          ${RED}$total_failed${NC}"
echo -e "Duration:        ${duration}s"
echo -e "Avg response:    ${avg_time}ms"
echo -e "Throughput:      ${throughput} req/s"
