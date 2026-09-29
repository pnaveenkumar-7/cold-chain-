/**
 * Read-Only Smart Contract Query Client (ethers.js)
 * ─────────────────────────────────────────────────
 * Queries getRecord(shipmentId) directly from ColdChainLedger on-chain.
 * Requires no wallet or private key.
 */

import { ethers } from 'ethers';

const ABI = [
  "function getRecord(string shipmentId) external view returns (bytes32 merkleRoot, string dataRef, uint256 timestamp, bool tamperViolation)",
  "function recordExists(string shipmentId) external view returns (bool)",
  "function owner() external view returns (address)"
];

/**
 * Fetch on-chain record for a shipment ID.
 * Returns null if record does not exist.
 */
export async function fetchOnChainRecord(shipmentId, contractAddress, rpcUrl) {
  const address = contractAddress || import.meta.env?.VITE_CONTRACT_ADDRESS || '0x1058b997fe82d40425472e981559c97d680335ca';
  const rpc = rpcUrl || import.meta.env?.VITE_RPC_URL || 'https://rpc-amoy.polygon.technology';

  try {
    const provider = new ethers.JsonRpcProvider(rpc);
    const contract = new ethers.Contract(address, ABI, provider);

    // Check if record exists first to prevent noisy reverts
    let exists = false;
    try {
      exists = await contract.recordExists(shipmentId);
    } catch {
      exists = true; // Fall back to direct getRecord if recordExists is not available
    }

    if (!exists) {
      return { found: false, reason: "Shipment ID not found on-chain" };
    }

    const res = await contract.getRecord(shipmentId);
    return {
      found: true,
      shipmentId,
      merkleRoot: res[0].toLowerCase(),
      dataRef: res[1],
      timestamp: Number(res[2]),
      tamperViolation: Boolean(res[3]),
      contractAddress: address,
      rpcUrl: rpc,
    };
  } catch (err) {
    // If contract reverts with "Record does not exist" or network failure
    if (err.message && (err.message.includes('Record does not exist') || err.message.includes('revert'))) {
      return { found: false, reason: "Not found / not yet certified on blockchain" };
    }
    console.error(`[Ledger] Error querying contract for ${shipmentId}:`, err);
    return { found: false, error: err.message, reason: "Blockchain RPC query failed" };
  }
}

/**
 * Fetch all on-chain batch records for a shipment.
 * @param {string[]} keys - List of on-chain keys (e.g., [shipmentId, `${shipmentId}-B2`, ...])
 * @returns {Promise<Object>} Map of key -> onChainRecord
 */
export async function fetchMultipleOnChainRecords(keys, contractAddress, rpcUrl) {
  const results = {};
  if (!Array.isArray(keys) || keys.length === 0) return results;

  const queries = keys.map(async (key) => {
    const rec = await fetchOnChainRecord(key, contractAddress, rpcUrl);
    results[key] = rec;
  });

  await Promise.all(queries);
  return results;
}
