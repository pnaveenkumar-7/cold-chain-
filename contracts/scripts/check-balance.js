'use strict';

const https = require('https');
const url = require('url');
const fs = require('fs');
const path = require('path');

const envFile = fs.readFileSync(path.resolve(__dirname, '../.env'), 'utf8');
const addressMatch = envFile.match(/DEPLOYER_ADDRESS=(0x[0-9a-fA-F]{40})/);
const address = addressMatch ? addressMatch[1] : '0x0B8789FFAA74C0636614Cd6DF2903f827C14629D';

console.log('Checking balance for Deployer Address on Polygon Amoy: ' + address);

const payload = JSON.stringify({
  jsonrpc: '2.0',
  method: 'eth_getBalance',
  params: [address, 'latest'],
  id: 1,
});

const RPC_URL = 'https://polygon-amoy-bor-rpc.publicnode.com';

const req = https.request(RPC_URL, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(payload),
  },
  timeout: 10000,
}, (res) => {
  let body = '';
  res.on('data', chunk => body += chunk);
  res.on('end', () => {
    try {
      const data = JSON.parse(body);
      const weiHex = data.result || '0x0';
      const wei = BigInt(weiHex);
      const matic = Number(wei) / 1e18;
      console.log('Balance in Wei  : ' + wei.toString());
      console.log('Balance in POL  : ' + matic + ' POL/MATIC');
      if (wei > 0n) {
        console.log('✔ WALLET IS FUNDED! Ready for contract deployment.');
      } else {
        console.log('⏳ Wallet balance is currently 0 POL. Awaiting faucet funding.');
      }
    } catch (e) {
      console.error('Error parsing response:', e.message);
    }
  });
});

req.on('error', err => console.error('RPC request failed:', err.message));
req.write(payload);
req.end();
