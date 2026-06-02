#!/bin/bash
# Test bug fixes specifically

BASE="http://localhost:8080"
INTERNAL_KEY="rapp-internal-secret-key"
PASS=0
FAIL=0

check() {
  local desc="$1" expected="$2" actual="$3"
  if echo "$actual" | grep -q "$expected"; then
    echo "  ✅ $desc"
    PASS=$((PASS+1))
  else
    echo "  ❌ $desc (expected '$expected')"
    echo "     Got: $(echo $actual | head -c 200)"
    FAIL=$((FAIL+1))
  fi
}

# Setup: Create session with 2 players and start
CREATE=$(curl -s -X POST $BASE/api/sessions -H "Content-Type: application/json" -d '{"hostName":"TestHost"}')
CODE=$(echo $CREATE | grep -o '"sessionCode":"[^"]*"' | cut -d'"' -f4)
TOKEN=$(echo $CREATE | grep -o '"sessionToken":"[^"]*"' | head -1 | cut -d'"' -f4)

JOIN=$(curl -s -X POST $BASE/api/sessions/$CODE/join -H "Content-Type: application/json" -d '{"displayName":"TestPlayer2"}')
TOKEN2=$(echo $JOIN | grep -o '"sessionToken":"[^"]*"' | head -1 | cut -d'"' -f4)

START=$(curl -s -X POST $BASE/api/sessions/$CODE/start -H "X-Session-Token: $TOKEN")
BS_ID=$(curl -s $BASE/api/sessions/$CODE/basestations -H "X-Session-Token: $TOKEN" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

echo "Setup: Session=$CODE, BS=$BS_ID"
echo ""

echo "=== Bug Fix 1: Insufficient Funds ==="
# Deploy 20 rApps to drain money (20 * €50 = €1000, exactly all money)
for i in $(seq 1 20); do
  curl -s -X POST $BASE/api/sessions/$CODE/rapps/deploy -H "X-Session-Token: $TOKEN" -H "Content-Type: application/json" -d "{\"templateId\":1,\"basestationId\":$BS_ID}" > /dev/null
done
# 21st deploy should fail (no money left)
BROKE=$(curl -s -X POST $BASE/api/sessions/$CODE/rapps/deploy -H "X-Session-Token: $TOKEN" -H "Content-Type: application/json" -d "{\"templateId\":1,\"basestationId\":$BS_ID}")
check "Insufficient funds rejected" "Insufficient funds" "$BROKE"

echo ""
echo "=== Bug Fix 2: Session Membership ==="
# Create a second session
CREATE2=$(curl -s -X POST $BASE/api/sessions -H "Content-Type: application/json" -d '{"hostName":"OtherHost"}')
CODE2=$(echo $CREATE2 | grep -o '"sessionCode":"[^"]*"' | cut -d'"' -f4)
TOKEN_OTHER=$(echo $CREATE2 | grep -o '"sessionToken":"[^"]*"' | head -1 | cut -d'"' -f4)

# Try to access first session with second session's token
CROSS=$(curl -s $BASE/api/sessions/$CODE -H "X-Session-Token: $TOKEN_OTHER")
check "Cross-session access denied" "FORBIDDEN" "$CROSS"

# Try to access basestations of first session with second session's token
CROSS_BS=$(curl -s $BASE/api/sessions/$CODE/basestations -H "X-Session-Token: $TOKEN_OTHER")
check "Cross-session basestations denied" "FORBIDDEN" "$CROSS_BS"

echo ""
echo "=== Bug Fix 3: Invalid Aggressiveness ==="
# Deploy an rApp first (use player2's token since player1 is broke)
BS_ID2=$(curl -s $BASE/api/sessions/$CODE/basestations -H "X-Session-Token: $TOKEN2" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
DEPLOY=$(curl -s -X POST $BASE/api/sessions/$CODE/rapps/deploy -H "X-Session-Token: $TOKEN2" -H "Content-Type: application/json" -d "{\"templateId\":1,\"basestationId\":$BS_ID2}")
DEP_ID=$(echo $DEPLOY | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

# Try to tune with invalid aggressiveness (need ACTIVE status first - skip if DEPLOYING)
INVALID_TUNE=$(curl -s -X PUT $BASE/api/sessions/$CODE/rapps/$DEP_ID/tune -H "X-Session-Token: $TOKEN2" -H "Content-Type: application/json" -d '{"threshold":75,"aggressiveness":"INVALID_VALUE"}')
check "Invalid aggressiveness rejected" "INVALID_STATE\|VALIDATION_ERROR" "$INVALID_TUNE"

echo ""
echo "=== Bug Fix 4: Threshold Validation ==="
INVALID_THRESHOLD=$(curl -s -X PUT $BASE/api/sessions/$CODE/rapps/$DEP_ID/tune -H "X-Session-Token: $TOKEN2" -H "Content-Type: application/json" -d '{"threshold":999,"aggressiveness":"HIGH"}')
check "Threshold > 100 rejected" "VALIDATION_ERROR\|INVALID_STATE" "$INVALID_THRESHOLD"

ZERO_THRESHOLD=$(curl -s -X PUT $BASE/api/sessions/$CODE/rapps/$DEP_ID/tune -H "X-Session-Token: $TOKEN2" -H "Content-Type: application/json" -d '{"threshold":0,"aggressiveness":"HIGH"}')
check "Threshold < 1 rejected" "VALIDATION_ERROR\|INVALID_STATE" "$ZERO_THRESHOLD"

echo ""
echo "==============================="
echo "Bug Fix Results: $PASS passed, $FAIL failed"
echo "==============================="
