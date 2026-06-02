#!/bin/bash
# Full API test script for rApp Tycoon

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
    echo "     Got: $actual"
    FAIL=$((FAIL+1))
  fi
}

echo "=== 1. Create Session ==="
CREATE=$(curl -s -X POST $BASE/api/sessions -H "Content-Type: application/json" -d '{"hostName":"Alice"}')
CODE=$(echo $CREATE | grep -o '"sessionCode":"[^"]*"' | cut -d'"' -f4)
TOKEN=$(echo $CREATE | grep -o '"sessionToken":"[^"]*"' | head -1 | cut -d'"' -f4)
check "Session created" "LOBBY" "$CREATE"
check "Has session code" "$CODE" "$CODE"
echo "  Session: $CODE"

echo ""
echo "=== 2. Join Session ==="
JOIN=$(curl -s -X POST $BASE/api/sessions/$CODE/join -H "Content-Type: application/json" -d '{"displayName":"Bob"}')
BOB_TOKEN=$(echo $JOIN | grep -o '"sessionToken":"[^"]*"' | head -1 | cut -d'"' -f4)
check "Bob joined" "Bob" "$JOIN"

echo ""
echo "=== 3. Start Game ==="
START=$(curl -s -X POST $BASE/api/sessions/$CODE/start -H "X-Session-Token: $TOKEN")
check "Game started" "ACTIVE" "$START"

echo ""
echo "=== 4. Get Basestations ==="
BS=$(curl -s $BASE/api/sessions/$CODE/basestations -H "X-Session-Token: $TOKEN")
BS_ID=$(echo $BS | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
check "Has 3 basestations" "BS-Gamma" "$BS"
check "Metrics at 100" "100.00" "$BS"
echo "  First BS ID: $BS_ID"

echo ""
echo "=== 5. Get Catalogue ==="
CAT=$(curl -s $BASE/api/rapps/catalogue -H "X-Session-Token: $TOKEN")
check "Has 7 rApps" "Alarm Noise Reducer" "$CAT"
check "Has Energy Saver" "Energy Saver" "$CAT"

echo ""
echo "=== 6. Deploy rApp ==="
DEPLOY=$(curl -s -X POST $BASE/api/sessions/$CODE/rapps/deploy -H "X-Session-Token: $TOKEN" -H "Content-Type: application/json" -d "{\"templateId\":1,\"basestationId\":$BS_ID}")
DEPLOY_ID=$(echo $DEPLOY | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
check "Deploy status DEPLOYING" "DEPLOYING" "$DEPLOY"
check "Deploy has config" "aggressiveness" "$DEPLOY"
echo "  Deployment ID: $DEPLOY_ID"

echo ""
echo "=== 7. Internal API - Get Active Sessions ==="
ACTIVE=$(curl -s $BASE/api/internal/sessions/active -H "X-Internal-Key: $INTERNAL_KEY")
check "Active sessions returned" "$CODE" "$ACTIVE"
check "Has playerCount" "playerCount" "$ACTIVE"

echo ""
echo "=== 8. Internal API - Push Event ==="
EVENT=$(curl -s -X POST $BASE/api/internal/sessions/$CODE/events -H "X-Internal-Key: $INTERNAL_KEY" -H "Content-Type: application/json" -d "{\"basestationId\":$BS_ID,\"eventType\":\"POWER_OUTAGE\",\"severity\":\"HIGH\",\"description\":\"Power failure at basestation\",\"impact\":{\"health\":-15.0,\"customerExperience\":-10.0,\"cost\":25.0,\"energyEfficiency\":-20.0,\"automationReliability\":-5.0,\"slaCompliance\":-12.0}}")
check "Event created" "POWER_OUTAGE" "$EVENT"
check "Event not resolved" "false" "$EVENT"
check "Escalation level 0" "\"escalationLevel\":0" "$EVENT"

echo ""
echo "=== 9. Verify Basestations Show Event ==="
BS2=$(curl -s $BASE/api/sessions/$CODE/basestations -H "X-Session-Token: $TOKEN")
check "Event visible on basestation" "POWER_OUTAGE" "$BS2"
check "Event shows HIGH severity" "HIGH" "$BS2"

echo ""
echo "=== 12. Error Cases ==="
NOKEY=$(curl -s $BASE/api/internal/sessions/active)
check "Rejected without key" "UNAUTHORIZED" "$NOKEY"

echo ""
echo "=== 11. Internal API - Wrong Key (should fail) ==="
WRONGKEY=$(curl -s $BASE/api/internal/sessions/active -H "X-Internal-Key: wrong-key")
check "Rejected with wrong key" "UNAUTHORIZED" "$WRONGKEY"

echo ""
echo "=== 12. Error Cases ==="
NOTOKEN=$(curl -s $BASE/api/sessions/$CODE/basestations)
check "401 without token" "UNAUTHORIZED" "$NOTOKEN"

BADSESSION=$(curl -s -X POST $BASE/api/sessions/INVALID1/join -H "Content-Type: application/json" -d '{"displayName":"Eve"}')
check "404 invalid session" "SESSION_NOT_FOUND" "$BADSESSION"

BLANKNAME=$(curl -s -X POST $BASE/api/sessions -H "Content-Type: application/json" -d '{"hostName":""}')
check "400 blank name" "VALIDATION_ERROR" "$BLANKNAME"

echo ""
echo "==============================="
echo "Results: $PASS passed, $FAIL failed"
echo "==============================="
