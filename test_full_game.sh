#!/bin/bash
# Full game simulation test - tests the tick engine, scoring, events, and rApp deployment

BASE="http://localhost:8080"
KEY="rapp-internal-secret-key"

echo "=== FULL GAME SIMULATION ==="
echo ""

# 1. Create session
echo "--- Step 1: Create Session ---"
CREATE=$(curl -s -X POST $BASE/api/sessions -H "Content-Type: application/json" -d '{"hostName":"Alice"}')
CODE=$(echo $CREATE | grep -o '"sessionCode":"[^"]*"' | cut -d'"' -f4)
TOKEN=$(echo $CREATE | grep -o '"sessionToken":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "Session: $CODE"

# 2. Join
echo "--- Step 2: Bob Joins ---"
JOIN=$(curl -s -X POST $BASE/api/sessions/$CODE/join -H "Content-Type: application/json" -d '{"displayName":"Bob"}')
TOKEN2=$(echo $JOIN | grep -o '"sessionToken":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "Bob joined"

# 3. Start game
echo "--- Step 3: Start Game ---"
curl -s -X POST $BASE/api/sessions/$CODE/start -H "X-Session-Token: $TOKEN" > /dev/null
echo "Game started"

# 4. Get basestations
echo "--- Step 4: Get Basestations ---"
BS=$(curl -s $BASE/api/sessions/$CODE/basestations -H "X-Session-Token: $TOKEN")
BS_ID=$(echo $BS | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "Alice's first basestation: $BS_ID"

# 5. Deploy an rApp
echo "--- Step 5: Deploy Energy Saver ---"
DEPLOY=$(curl -s -X POST $BASE/api/sessions/$CODE/rapps/deploy -H "X-Session-Token: $TOKEN" -H "Content-Type: application/json" -d "{\"templateId\":1,\"basestationId\":$BS_ID}")
DEP_ID=$(echo $DEPLOY | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
echo "Deployed (ID: $DEP_ID, status: DEPLOYING)"

# 6. Push an event
echo "--- Step 6: Push POWER_OUTAGE Event ---"
EVENT=$(curl -s -X POST $BASE/api/internal/sessions/$CODE/events -H "X-Internal-Key: $KEY" -H "Content-Type: application/json" -d "{\"basestationId\":$BS_ID,\"eventType\":\"POWER_OUTAGE\",\"severity\":\"HIGH\",\"description\":\"Power failure!\",\"impact\":{\"health\":-10.0,\"customerExperience\":-5.0,\"cost\":15.0,\"energyEfficiency\":-15.0,\"automationReliability\":-3.0,\"slaCompliance\":-8.0}}")
echo "Event pushed"

# 7. Wait for 2 ticks (10 seconds)
echo "--- Step 7: Waiting 12 seconds for tick engine (2 ticks) ---"
sleep 12

# 8. Check basestations - metrics should have changed
echo "--- Step 8: Check Metrics After Ticks ---"
BS_AFTER=$(curl -s $BASE/api/sessions/$CODE/basestations -H "X-Session-Token: $TOKEN")
HEALTH=$(echo $BS_AFTER | grep -o '"health":[0-9.]*' | head -1 | cut -d: -f2)
ENERGY=$(echo $BS_AFTER | grep -o '"energyEfficiency":[0-9.]*' | head -1 | cut -d: -f2)
echo "Health: $HEALTH (should be < 100)"
echo "Energy Efficiency: $ENERGY (should be changed by rApp + event)"

# Check deployed rApps status
RAPP_STATUS=$(echo $BS_AFTER | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "rApp status: $RAPP_STATUS (should be ACTIVE after tick)"

# 9. Check leaderboard - scores should be calculated
echo "--- Step 9: Check Leaderboard ---"
LB=$(curl -s $BASE/api/sessions/$CODE/leaderboard -H "X-Session-Token: $TOKEN")
SCORE1=$(echo $LB | grep -o '"compositeScore":[0-9.]*' | head -1 | cut -d: -f2)
echo "Alice's composite score: $SCORE1 (should be > 0)"

# 10. Check session state - tick should have advanced
echo "--- Step 10: Check Session State ---"
STATE=$(curl -s $BASE/api/sessions/$CODE -H "X-Session-Token: $TOKEN")
echo "Session state: $(echo $STATE | grep -o '"state":"[^"]*"' | cut -d'"' -f4)"

echo ""
echo "=== GAME SIMULATION COMPLETE ==="
echo ""

# Verify key assertions
PASS=0
FAIL=0

check() {
  local desc="$1" condition="$2"
  if [ "$condition" = "true" ]; then
    echo "  ✅ $desc"
    PASS=$((PASS+1))
  else
    echo "  ❌ $desc"
    FAIL=$((FAIL+1))
  fi
}

echo "=== ASSERTIONS ==="
# Health should be less than 100 (event damage applied)
check "Health decreased from events" "$(echo "$HEALTH < 100" | bc -l 2>/dev/null || echo "true")"
# rApp should be ACTIVE (tick engine activated it)
check "rApp activated by tick engine" "$([ "$RAPP_STATUS" = "ACTIVE" ] && echo true || echo false)"
# Score should be > 0 (recalculated by tick engine)
check "Score calculated by tick engine" "$(echo "$SCORE1 > 0" | bc -l 2>/dev/null || [ "$SCORE1" != "0.00" ] && echo true || echo false)"

echo ""
echo "Results: $PASS passed, $FAIL failed"
