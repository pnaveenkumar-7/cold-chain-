'use strict';

const fs = require('fs');
const path = require('path');

const solSource = fs.readFileSync(path.resolve(__dirname, '../contracts/ColdChainLedger.sol'), 'utf8');
const solcFile = path.resolve(__dirname, '../cache/soljson-v0.8.20+commit.a1b79de6.js');

console.log('Loading Solidity v0.8.20 compiler...');
const soljson = require(solcFile);

// Wrap solidity_compile with correct signature: (string, number) -> string
const compileStandard = soljson.cwrap('solidity_compile', 'string', ['string', 'number']);

const input = {
  language: 'Solidity',
  sources: {
    'ColdChainLedger.sol': {
      content: solSource,
    },
  },
  settings: {
    optimizer: {
      enabled: true,
      runs: 200,
    },
    outputSelection: {
      '*': {
        '*': ['abi', 'evm.bytecode', 'evm.deployedBytecode'],
      },
    },
  },
};

console.log('Compiling ColdChainLedger.sol with 200 optimizer runs...');
const outputJson = compileStandard(JSON.stringify(input), 0);
const output = JSON.parse(outputJson);

if (output.errors) {
  let hasError = false;
  output.errors.forEach((err) => {
    console.log(err.formattedMessage || err.message);
    if (err.severity === 'error') hasError = true;
  });
  if (hasError) throw new Error('Compilation failed');
}

const contract = output.contracts['ColdChainLedger.sol']['ColdChainLedger'];
const abi = contract.abi;
const bytecode = '0x' + contract.evm.bytecode.object;

console.log('✔ ColdChainLedger.sol compiled successfully!');
console.log('  Bytecode size: ' + bytecode.length / 2 + ' bytes');
console.log('  ABI methods  : ' + abi.filter(a => a.type === 'function').length + ' functions\n');

// Save artifact
const artifactDir = path.resolve(__dirname, '../artifacts/contracts/ColdChainLedger.sol');
fs.mkdirSync(artifactDir, { recursive: true });

const artifactData = {
  _format: 'hh-sol-artifact-1',
  contractName: 'ColdChainLedger',
  sourceName: 'contracts/ColdChainLedger.sol',
  abi,
  bytecode,
  deployedBytecode: '0x' + contract.evm.deployedBytecode.object,
};

const artifactPath = path.join(artifactDir, 'ColdChainLedger.json');
fs.writeFileSync(artifactPath, JSON.stringify(artifactData, null, 2), 'utf8');
console.log('✔ Saved contract artifact to: ' + artifactPath + '\n');
