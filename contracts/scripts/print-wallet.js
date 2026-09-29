'use strict';

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

// Standalone Keccak-256
function pureKeccak256(data) {
  const RC = [
    0x00000001n, 0x00008082n, 0x8000808an, 0x80008000n,
    0x0000808bn, 0x00000001n, 0x80008081n, 0x80008009n,
    0x0000008an, 0x00000088n, 0x80008009n, 0x0000000an,
    0x0000808bn, 0x0000008bn, 0x80008089n, 0x80008003n,
    0x80008002n, 0x00000080n, 0x0000800an, 0x8000000an,
    0x80008081n, 0x00008080n, 0x00000001n, 0x80008008n
  ];
  const RHO = [
    0, 1, 62, 28, 27, 36, 44, 6, 55, 20, 3, 10, 43, 25, 39, 41, 45, 15,
    21, 8, 18, 2, 61, 56, 14
  ];
  const rate = 136;
  const state = new BigUint64Array(25);
  const paddingLen = rate - (data.length % rate);
  const padded = Buffer.alloc(data.length + paddingLen);
  data.copy(padded, 0);
  if (paddingLen === 1) padded[data.length] = 0x81;
  else {
    padded[data.length] = 0x01;
    padded[data.length + paddingLen - 1] = 0x80;
  }
  for (let offset = 0; offset < padded.length; offset += rate) {
    for (let i = 0; i < rate / 8; i++) {
      state[i] ^= padded.readBigUInt64LE(offset + i * 8);
    }
    for (let round = 0; round < 24; round++) {
      const C = new BigUint64Array(5);
      for (let x = 0; x < 5; x++) C[x] = state[x] ^ state[x + 5] ^ state[x + 10] ^ state[x + 15] ^ state[x + 20];
      const D = new BigUint64Array(5);
      for (let x = 0; x < 5; x++) {
        const left = (C[(x + 1) % 5] << 1n) | (C[(x + 1) % 5] >> 63n);
        D[x] = C[(x + 4) % 5] ^ left;
      }
      for (let i = 0; i < 25; i++) state[i] ^= D[i % 5];
      const B = new BigUint64Array(25);
      for (let x = 0; x < 5; x++) {
        for (let y = 0; y < 5; y++) {
          const idx = x + 5 * y;
          const shift = BigInt(RHO[idx]);
          const rot = shift === 0n ? state[idx] : (state[idx] << shift) | (state[idx] >> (64n - shift));
          B[y + 5 * ((2 * x + 3 * y) % 5)] = rot;
        }
      }
      for (let x = 0; x < 5; x++) {
        for (let y = 0; y < 5; y++) {
          const idx = x + 5 * y;
          state[idx] = B[idx] ^ ((~B[((x + 1) % 5) + 5 * y]) & B[((x + 2) % 5) + 5 * y]);
        }
      }
      state[0] ^= RC[round];
    }
  }
  const out = Buffer.alloc(32);
  for (let i = 0; i < 4; i++) out.writeBigUInt64LE(state[i], i * 8);
  return out;
}

function toChecksumAddress(address) {
  const clean = address.toLowerCase().replace(/^0x/, '');
  const hash = pureKeccak256(Buffer.from(clean, 'utf8')).toString('hex');
  let ret = '0x';
  for (let i = 0; i < clean.length; i++) {
    ret += parseInt(hash[i], 16) >= 8 ? clean[i].toUpperCase() : clean[i];
  }
  return ret;
}

function deriveFromPrivateKey(privKeyHex) {
  const privBytes = Buffer.from(privKeyHex.replace(/^0x/, ''), 'hex');
  const ecdh = crypto.createECDH('secp256k1');
  ecdh.setPrivateKey(privBytes);
  const pubKey = ecdh.getPublicKey(); // 65 bytes (04 + X + Y)
  const uncompressedXY = pubKey.slice(1); // 64 bytes
  const hash = pureKeccak256(uncompressedXY);
  return toChecksumAddress('0x' + hash.slice(12, 32).toString('hex'));
}

// Generate new random private key
const privKey = '0x' + crypto.randomBytes(32).toString('hex');
const deployerAddress = deriveFromPrivateKey(privKey);

const envText = `# ─── Polygon Amoy Testnet Deployment Configuration ───────────
AMOY_RPC_URL=https://rpc-amoy.polygon.technology
PRIVATE_KEY=${privKey}
DEPLOYER_ADDRESS=${deployerAddress}
`;

fs.writeFileSync(path.resolve(__dirname, '../.env'), envText, 'utf8');

const outputInfo = {
  address: deployerAddress,
  privateKey: privKey,
};

fs.writeFileSync(path.resolve(__dirname, '../deployer_wallet.json'), JSON.stringify(outputInfo, null, 2), 'utf8');
