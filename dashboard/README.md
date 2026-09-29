# Cold-Chain Inspection Web Dashboard — Phase 3

A React + Vite inspector dashboard that verifies the cryptographic provenance, blockchain immutability, and sensor telemetry safety of cold-chain shipments.

## Key Features
- **QR Code Scanner**: Integrated camera scanner via `html5-qrcode` to scan crate/shipment barcodes.
- **On-Chain Audit**: Read-only `ethers.js` queries to `ColdChainLedger.getRecord(shipmentId)` directly on Polygon Amoy (no wallet required).
- **Client-Side Merkle Proof**: Recomputes binary Merkle roots client-side and validates against on-chain roots to detect database tampering.
- **Trust Badges**:
  - 🟢 **Certified Fresh**: Merkle roots match, temperature within safe threshold ($2.0^\circ\text{C} – 8.0^\circ\text{C}$), zero tamper violation.
  - 🔴 **Fraud / Spoilage Warning**: Highlights exact breach (Merkle mismatch, out-of-range temperature, or hardware tamper flag) with violation timestamp and record index.
- **Telemetry Table**: Sensor reading timeline (temperature, humidity, gas ppm, tamper state, and validity).

---

## Setup & Running

```bash
cd dashboard
npm install
npm run dev
```

Open `http://localhost:5173` in your browser.

---

## Configuration (`.env`)

| Variable | Description | Default |
|---|---|---|
| `VITE_CONTRACT_ADDRESS` | Deployed `ColdChainLedger` address | `0x1058b997fe82d40425472e981559c97d680335ca` |
| `VITE_RPC_URL` | Polygon Amoy testnet RPC endpoint | `https://rpc-amoy.polygon.technology` |
| `VITE_BACKEND_URL` | Phase 1 Ingestion Backend root URL | `http://localhost:3001` |
| `VITE_TEMP_MIN` | Minimum safe cold-chain temperature | `2.0` |
| `VITE_TEMP_MAX` | Maximum safe cold-chain temperature | `8.0` |
