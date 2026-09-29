'use strict';

const fs = require('fs');
const path = require('path');

// 1. Load contracts/.env
const envPath = path.resolve(__dirname, '../.env');
const envLines = fs.readFileSync(envPath, 'utf8').split('\n');
const env = {};
for (const line of envLines) {
  const t = line.trim();
  if (!t || t.startsWith('#')) continue;
  const eq = t.indexOf('=');
  if (eq > 0) env[t.slice(0, eq).trim()] = t.slice(eq + 1).trim();
}

const RPC_URL = env.SEPOLIA_RPC_URL || 'https://ethereum-sepolia-rpc.publicnode.com';
const PRIVATE_KEY = env.PRIVATE_KEY;

if (!PRIVATE_KEY) {
  console.error('✘ Error: PRIVATE_KEY not found in contracts/.env');
  process.exit(1);
}

async function deploy() {
  console.log('========================================================================');
  console.log('  DEPLOYING COLDCHAINLEDGER TO ETHEREUM SEPOLIA TESTNET (LIVE CHAIN)');
  console.log('========================================================================\n');

  // Dynamically resolve ethers from dashboard or local
  let ethers;
  try {
    ethers = require('ethers');
  } catch {
    ethers = require(path.resolve(__dirname, '../../dashboard/node_modules/ethers'));
  }

  const provider = new ethers.JsonRpcProvider(RPC_URL, 11155111);
  const wallet = new ethers.Wallet(PRIVATE_KEY, provider);

  console.log('Deployer Address   : ' + wallet.address);
  const balance = await provider.getBalance(wallet.address);
  console.log('Deployer Balance   : ' + ethers.formatEther(balance) + ' ETH');
  console.log('RPC Endpoint       : ' + RPC_URL + '\n');

  if (balance === 0n) {
    console.error('✘ Error: Deployer balance is 0 ETH. Please fund the wallet before deploying.');
    process.exit(1);
  }

  // Load compiled artifact
  const artifactPath = path.resolve(__dirname, '../artifacts/contracts/ColdChainLedger.sol/ColdChainLedger.json');
  if (!fs.existsSync(artifactPath)) {
    console.error('✘ Error: Contract artifact not found at ' + artifactPath + '. Please run compile first.');
    process.exit(1);
  }

  const artifact = JSON.parse(fs.readFileSync(artifactPath, 'utf8'));
  const factory = new ethers.ContractFactory(artifact.abi, artifact.bytecode, wallet);

  console.log('Broadcasting deployment transaction to Ethereum Sepolia...');
  const contract = await factory.deploy(wallet.address);

  console.log('Transaction Hash   : ' + contract.deploymentTransaction().hash);
  console.log('Waiting for block confirmation on Sepolia...\n');

  await contract.waitForDeployment();
  const contractAddress = await contract.getAddress();

  console.log('========================================================================');
  console.log('✔ CONTRACT SUCCESSFULLY DEPLOYED ON ETHEREUM SEPOLIA TESTNET!');
  console.log('========================================================================');
  console.log('  Contract Address : ' + contractAddress);
  console.log('  Transaction Hash : ' + contract.deploymentTransaction().hash);
  console.log('  Deployer / Owner : ' + wallet.address);
  console.log('  Block Explorer   : https://sepolia.etherscan.io/address/' + contractAddress);
  console.log('  Tx Explorer      : https://sepolia.etherscan.io/tx/' + contract.deploymentTransaction().hash);
  console.log('========================================================================\n');

  // Save deployment artifact
  const deploymentInfo = {
    address: contractAddress,
    deployer: wallet.address,
    network: 'Ethereum Sepolia Testnet (ChainId: 11155111)',
    rpcUrl: RPC_URL,
    txHash: contract.deploymentTransaction().hash,
    deployedAt: new Date().toISOString(),
    contractName: 'ColdChainLedger',
    abi: artifact.abi,
  };

  const deployOutPath = path.resolve(__dirname, '../deployment.json');
  fs.writeFileSync(deployOutPath, JSON.stringify(deploymentInfo, null, 2), 'utf8');
  console.log('✔ Saved deployment details to ' + deployOutPath);

  // Update backend/.env
  const backendEnvPath = path.resolve(__dirname, '../../backend/.env');
  if (fs.existsSync(backendEnvPath)) {
    let backendEnv = fs.readFileSync(backendEnvPath, 'utf8');
    backendEnv = backendEnv.replace(/LEDGER_CONTRACT_ADDRESS=.*/, `LEDGER_CONTRACT_ADDRESS=${contractAddress}`);
    backendEnv = backendEnv.replace(/RPC_URL=.*/, `RPC_URL=${RPC_URL}`);
    backendEnv = backendEnv.replace(/CHAIN_ID=.*/, `CHAIN_ID=11155111`);
    fs.writeFileSync(backendEnvPath, backendEnv, 'utf8');
    console.log('✔ Updated backend/.env with live Sepolia contract address and RPC');
  }

  // Update dashboard/.env
  const dashboardEnvPath = path.resolve(__dirname, '../../dashboard/.env');
  if (fs.existsSync(dashboardEnvPath)) {
    let dashboardEnv = fs.readFileSync(dashboardEnvPath, 'utf8');
    dashboardEnv = dashboardEnv.replace(/VITE_CONTRACT_ADDRESS=.*/, `VITE_CONTRACT_ADDRESS=${contractAddress}`);
    dashboardEnv = dashboardEnv.replace(/VITE_RPC_URL=.*/, `VITE_RPC_URL=${RPC_URL}`);
    dashboardEnv = dashboardEnv.replace(/VITE_CHAIN_ID=.*/, `VITE_CHAIN_ID=11155111`);
    fs.writeFileSync(dashboardEnvPath, dashboardEnv, 'utf8');
    console.log('✔ Updated dashboard/.env with live Sepolia contract address and RPC');
  }

  console.log('\nDeployment Complete!');
  process.exit(0);
}

deploy().catch((err) => {
  console.error('✘ Deployment Failed:', err);
  process.exit(1);
});

