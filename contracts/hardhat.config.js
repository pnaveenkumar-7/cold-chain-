require("@nomicfoundation/hardhat-toolbox");
require("dotenv").config();

const SEPOLIA_RPC_URL = process.env.SEPOLIA_RPC_URL || "https://ethereum-sepolia-rpc.publicnode.com";
const AMOY_RPC_URL = process.env.AMOY_RPC_URL || "https://polygon-amoy-bor-rpc.publicnode.com";
const PRIVATE_KEY = process.env.PRIVATE_KEY || "0x0ac224b49325536da30c380101b5ee1821898f0e078b71b4afe8ab9065f70231";


/** @type import('hardhat/config').HardhatUserConfig */
module.exports = {
  solidity: {
    version: "0.8.20",
    settings: {
      optimizer: {
        enabled: true,
        runs: 200,
      },
    },
  },
  networks: {
    hardhat: {
      chainId: 31337,
    },
    localhost: {
      url: "http://127.0.0.1:8545",
      chainId: 31337,
    },
    sepolia: {
      url: SEPOLIA_RPC_URL,
      accounts: PRIVATE_KEY && !PRIVATE_KEY.startsWith("your_") ? [PRIVATE_KEY] : [],
      chainId: 11155111,
    },
    amoy: {
      url: AMOY_RPC_URL,
      accounts: PRIVATE_KEY && !PRIVATE_KEY.startsWith("your_") ? [PRIVATE_KEY] : [],
      chainId: 80002,
    },
  },
  etherscan: {
    apiKey: {
      sepolia: process.env.ETHERSCAN_API_KEY || "YOUR_ETHERSCAN_API_KEY",
      polygonAmoy: process.env.POLYGONSCAN_API_KEY || "YOUR_POLYGONSCAN_API_KEY",
    },
    customChains: [
      {
        network: "polygonAmoy",
        chainId: 80002,
        urls: {
          apiURL: "https://api-amoy.polygonscan.com/api",
          browserURL: "https://amoy.polygonscan.com",
        },
      },
    ],
  },
  paths: {
    sources: "./contracts",
    tests: "./test",
    cache: "./cache",
    artifacts: "./artifacts",
  },

};
