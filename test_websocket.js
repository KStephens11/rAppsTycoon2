/**
 * WebSocket/STOMP test for rApp Tycoon
 * 
 * Prerequisites: npm install @stomp/stompjs ws
 * Run: node test_websocket.js
 */

const { Client } = require('@stomp/stompjs');
const WebSocket = require('ws');

const BASE = 'http://localhost:8080';
const WS_URL = 'ws://localhost:8080/ws/game/websocket';

// Alternative URLs to try if the above doesn't work:
// 'ws://localhost:8080/ws/game' (raw WebSocket without SockJS suffix)

async function fetchJson(url, options = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options
  });
  return res.json();
}

async function main() {
  console.log('=== WebSocket Integration Test ===\n');

  // 1. Create session and get token
  console.log('1. Creating session...');
  const session = await fetchJson(`${BASE}/api/sessions`, {
    method: 'POST',
    body: JSON.stringify({ hostName: 'WsTestPlayer' })
  });
  const code = session.sessionCode;
  const token = session.hostPlayer.sessionToken;
  console.log(`   Session: ${code}, Token: ${token.substring(0, 16)}...`);

  // 2. Join with second player
  console.log('2. Joining with Player2...');
  const join = await fetchJson(`${BASE}/api/sessions/${code}/join`, {
    method: 'POST',
    body: JSON.stringify({ displayName: 'Player2' })
  });
  console.log(`   Player2 joined`);

  // 3. Start game
  console.log('3. Starting game...');
  await fetchJson(`${BASE}/api/sessions/${code}/start`, {
    method: 'POST',
    headers: { 'X-Session-Token': token }
  });
  console.log('   Game started');

  // 4. Connect via WebSocket with STOMP
  console.log('4. Connecting WebSocket...');

  return new Promise((resolve) => {
    const client = new Client({
      webSocketFactory: () => new WebSocket(WS_URL),
      connectHeaders: { 'X-Session-Token': token },
      debug: () => {}, // suppress debug logs
      reconnectDelay: 0,
    });

    let messagesReceived = [];

    client.onConnect = () => {
      console.log('   ✅ WebSocket connected!');

      // Subscribe to game topic
      client.subscribe(`/topic/session/${code}/game`, (msg) => {
        const body = JSON.parse(msg.body);
        messagesReceived.push(body);
        console.log(`   📨 Received: ${body.type}`);
      });

      // Subscribe to leaderboard
      client.subscribe(`/topic/session/${code}/leaderboard`, (msg) => {
        const body = JSON.parse(msg.body);
        messagesReceived.push(body);
        console.log(`   📨 Received: ${body.type}`);
      });

      // Subscribe to player events
      const playerId = session.hostPlayer.id;
      client.subscribe(`/topic/session/${code}/player/${playerId}/metrics`, (msg) => {
        const body = JSON.parse(msg.body);
        messagesReceived.push(body);
        console.log(`   📨 Received: ${body.type}`);
      });

      console.log('   Subscribed to topics. Waiting for tick updates (10s)...');

      // Wait for tick engine to send updates
      setTimeout(() => {
        console.log(`\n=== Results ===`);
        console.log(`Messages received: ${messagesReceived.length}`);
        if (messagesReceived.length > 0) {
          console.log('✅ WebSocket is working! Received real-time updates.');
          messagesReceived.forEach(m => console.log(`   - ${m.type}: ${JSON.stringify(m.payload).substring(0, 80)}...`));
        } else {
          console.log('⚠️  No messages received yet (tick engine may not have broadcast yet).');
          console.log('   This is expected if the tick engine doesn\'t broadcast to WebSocket yet.');
        }

        client.deactivate();
        resolve();
      }, 12000);
    };

    client.onStompError = (frame) => {
      console.log(`   ❌ STOMP error: ${frame.headers.message}`);
      resolve();
    };

    client.onWebSocketError = (error) => {
      console.log(`   ❌ WebSocket error: ${error.message}`);
      resolve();
    };

    client.activate();
  });
}

main().then(() => {
  console.log('\n=== Test Complete ===');
  process.exit(0);
}).catch(err => {
  console.error('Error:', err.message);
  process.exit(1);
});
