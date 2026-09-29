'use strict';

const fs = require('fs');
const path = require('path');

let ethers;
try {
  ethers = require('ethers');
} catch {
  ethers = require(path.resolve(__dirname, '../../dashboard/node_modules/ethers'));
}

const wallet = ethers.Wallet.createRandom();

console.log('========================================================================');
console.log('  FRESH POLYGON AMOY TESTNET DEPLOYER WALLET GENERATED');
console.log('========================================================================');
console.log('  Public Address : ' + wallet.address);
console.log('  Private Key    : ' + wallet.privateKey);
console.log('========================================================================\n');

const envContent = `# ─── Polygon Amoy Testnet Deployment Configuration ───────────
AMOY_RPC_URL=https://polygon-amoy-bor-rpc.publicnode.com
PRIVATE_KEY=${wallet.privateKey}
DEPLOYER_ADDRESS=${wallet.address}
`;

fs.writeFileSync(path.resolve(__dirname, '../.env'), envContent, 'utf8');
fs.writeFileSync(
  path.resolve(__dirname, '../deployer_wallet.json'),
  JSON.stringify({ address: wallet.address, privateKey: wallet.privateKey }, null, 2),
  'utf8'
);

console.log('✔ Stored credentials in contracts/.env and contracts/deployer_wallet.json\n');
process.exit(0);
