const { ethers } = require("hardhat");
const fs = require("fs");
const path = require("path");

async function main() {
  const [deployer] = await ethers.getSigners();
  console.log("==================================================");
  console.log(" Deploying ColdChainLedger Smart Contract");
  console.log(" Deployer address:", deployer.address);
  const balance = await ethers.provider.getBalance(deployer.address);
  console.log(" Deployer balance:", ethers.formatEther(balance), "ETH/MATIC");
  console.log(" Network:", (await ethers.provider.getNetwork()).name, "(chainId:", (await ethers.provider.getNetwork()).chainId.toString(), ")");
  console.log("==================================================\n");

  const ColdChainLedger = await ethers.getContractFactory("ColdChainLedger");
  const ledger = await ColdChainLedger.deploy(deployer.address);
  await ledger.waitForDeployment();

  const contractAddress = await ledger.getAddress();

  console.log("✔ ColdChainLedger successfully deployed on Polygon Amoy!");
  console.log("--------------------------------------------------");
  console.log(" CONTRACT ADDRESS :", contractAddress);
  console.log(" POLYGONSCAN URL  : https://amoy.polygonscan.com/address/" + contractAddress);
  console.log("--------------------------------------------------\n");


  // Output deployment artifact for backend integration
  const deploymentInfo = {
    address: contractAddress,
    deployer: deployer.address,
    network: (await ethers.provider.getNetwork()).name,
    chainId: (await ethers.provider.getNetwork()).chainId.toString(),
    timestamp: new Date().toISOString(),
  };

  const deployOutPath = path.join(__dirname, "../deployment.json");
  fs.writeFileSync(deployOutPath, JSON.stringify(deploymentInfo, null, 2));
  console.log("Deployment info written to:", deployOutPath);

  return contractAddress;
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error("Deployment failed:", error);
    process.exit(1);
  });
