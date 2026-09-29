'use strict';

const https = require('https');
const url = require('url');

const rpcEndpoints = [
  'https://polygon-amoy-bor-rpc.publicnode.com',
  'https://amoy.drpc.org',
  'https://rpc.ankr.com/polygon_amoy',
  'https://80002.rpc.thirdweb.com',
  'https://polygon-amoy.drpc.org',
  'https://rpc-amoy.polygon.technology',
];

const address = '0x0B8789FFAA74C0636614Cd6DF2903f827C14629D';

async function checkRpc(rpc) {
  return new Promise((resolve) => {
    const payload = JSON.stringify({
      jsonrpc: '2.0',
      method: 'eth_getBalance',
      params: [address, 'latest'],
      id: 1,
    });

    const parsed = url.parse(rpc);
    const start = Date.now();

    const req = https.request({
      hostname: parsed.hostname,
      port: parsed.port || 443,
      path: parsed.path || '/',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload),
        'User-Agent': 'Mozilla/5.0',
      },
      timeout: 8000,
    }, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          const wei = BigInt(json.result || '0x0');
          const elapsed = Date.now() - start;
          resolve({ rpc, success: true, wei, matic: Number(wei) / 1e18, elapsed });
        } catch (e) {
          resolve({ rpc, success: false, error: 'JSON parse error: ' + e.message });
        }
      });
    });

    req.on('error', (err) => {
      resolve({ rpc, success: false, error: err.message });
    });
    req.on('timeout', () => {
      req.destroy();
      resolve({ rpc, success: false, error: 'Timeout (>8s)' });
    });

    req.write(payload);
    req.end();
  });
}

async function main() {
  console.log('Testing Polygon Amoy RPC Endpoints for ' + address + '...\n');
  const results = await Promise.all(rpcEndpoints.map(checkRpc));
  results.forEach(r => {
    if (r.success) {
      console.log(`✔ [${r.elapsed}ms] ${r.rpc} -> Balance: ${r.matic} POL (Wei: ${r.wei})`);
    } else {
      console.log(`✘ ${r.rpc} -> ${r.error}`);
    }
  });

  const working = results.filter(r => r.success);
  if (working.length > 0) {
    console.log(`\nFastest working RPC: ${working[0].rpc}`);
  }
}

main();
