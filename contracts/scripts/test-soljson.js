'use strict';

const fs = require('fs');
const path = require('path');

const solcFile = path.resolve(__dirname, '../cache/soljson-v0.8.20+commit.a1b79de6.js');
console.log('Loading soljson...');
const soljson = require(solcFile);

console.log('soljson keys:');
const exportsList = Object.keys(soljson).filter(k => typeof soljson[k] === 'function');
console.log('Function exports:', exportsList);

// Check standard compile methods
const compileFn = soljson.cwrap ? soljson.cwrap('solidity_compile', 'string', ['string']) : null;
const standardFn = soljson.cwrap ? soljson.cwrap('compileStandard', 'string', ['string']) : null;

console.log('cwrap solidity_compile:', typeof compileFn);
console.log('cwrap compileStandard :', typeof standardFn);

const solSource = fs.readFileSync(path.resolve(__dirname, '../contracts/ColdChainLedger.sol'), 'utf8');
const input = {
  language: 'Solidity',
  sources: { 'ColdChainLedger.sol': { content: solSource } },
  settings: {
    optimizer: { enabled: true, runs: 200 },
    outputSelection: { '*': { '*': ['abi', 'evm.bytecode', 'evm.deployedBytecode'] } },
  },
};

let res;
if (standardFn) {
  res = standardFn(JSON.stringify(input));
} else if (compileFn) {
  res = compileFn(JSON.stringify(input));
} else if (soljson._solidity_compile) {
  // raw C call
  const str = JSON.stringify(input);
  const ptr = soljson._malloc(Buffer.byteLength(str) + 1);
  soljson.stringToUTF8(str, ptr, Buffer.byteLength(str) + 1);
  const resPtr = soljson._solidity_compile(ptr);
  res = soljson.UTF8ToString(resPtr);
}

const parsed = JSON.parse(res);
const contract = parsed.contracts['ColdChainLedger.sol']['ColdChainLedger'];
console.log('✔ Compiled successfully!');
console.log('Bytecode length:', contract.evm.bytecode.object.length);

// Save artifact
const artifactDir = path.resolve(__dirname, '../artifacts/contracts/ColdChainLedger.sol');
fs.mkdirSync(artifactDir, { recursive: true });
const artifactData = {
  _format: 'hh-sol-artifact-1',
  contractName: 'ColdChainLedger',
  sourceName: 'contracts/ColdChainLedger.sol',
  abi: contract.abi,
  bytecode: '0x' + contract.evm.bytecode.object,
  deployedBytecode: '0x' + contract.evm.deployedBytecode.object,
};
fs.writeFileSync(path.join(artifactDir, 'ColdChainLedger.json'), JSON.stringify(artifactData, null, 2), 'utf8');
console.log('✔ Saved artifact to ColdChainLedger.json');
