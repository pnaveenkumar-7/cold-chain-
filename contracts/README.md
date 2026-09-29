# Cold-Chain Smart Contracts — Phase 2

Smart contracts and deployment scripts for the cold-chain decentralized ledger.

## Contract: `ColdChainLedger.sol`
- **Immutability**: Records once committed for a `shipmentId` cannot be updated, deleted, or overwritten. Duplicate commits are strictly rejected.
- **Access Control**: Only the contract owner (backend signer) can call `commitRecord`.
- **Public Audit**: Anyone can call `getRecord(shipmentId)` to retrieve the 32-byte Merkle root, URI reference to raw sensor data, commit timestamp, and tamper flag.

---

## Setup & Installation

```bash
cd contracts
npm install
```

---

## Configuration

Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```

Set `AMOY_RPC_URL` and `PRIVATE_KEY` for Polygon Amoy testnet deployment.

---

## Compiling Contracts

```bash
npx hardhat compile
```

---

## Running Unit Tests

Runs the Chai test suite covering deployment, record commits, hash retrieval, duplicate rejection, and access control:

```bash
npx hardhat test
```

---

## Deploying to Local Hardhat Node

1. Start a local node:
```bash
npx hardhat node
```

2. In another terminal, deploy:
```bash
npx hardhat run scripts/deploy.js --network localhost
```

---

## Deploying to Polygon Amoy Testnet

Make sure your wallet has testnet POL from the faucet (e.g. https://faucet.polygon.technology/).

```bash
npx hardhat run scripts/deploy.js --network amoy
```

The script will print the deployed contract address and write `deployment.json`.
