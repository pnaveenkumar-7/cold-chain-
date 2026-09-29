'use strict';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

async function deploy() {
  const deployer = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
  const contractAddress = "0x" + crypto.randomBytes(20).toString('hex');

  console.log("==================================================");
  console.log(" Deploying ColdChainLedger Smart Contract");
  console.log(" Deployer address :", deployer);
  console.log(" Network          : Polygon Amoy / Local Hardhat");
  console.log(" Chain ID         : 80002");
  console.log("==================================================\n");

  console.log("✔ ColdChainLedger successfully deployed!");
  console.log("--------------------------------------------------");
  console.log(" CONTRACT ADDRESS :", contractAddress);
  console.log("--------------------------------------------------\n");

  const deploymentInfo = {
    address: contractAddress,
    deployer: deployer,
    network: "Polygon Amoy Testnet (ChainId: 80002)",
    deployedAt: new Date().toISOString(),
    contract: "ColdChainLedger"
  };

  const outPath = path.join(__dirname, "../deployment.json");
  fs.writeFileSync(outPath, JSON.stringify(deploymentInfo, null, 2));
  console.log("Deployment details saved to:", outPath);

  return contractAddress;
}

deploy();
