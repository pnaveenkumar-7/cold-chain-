/**
 * Client-Side Binary Merkle Tree Root Computation
 * ────────────────────────────────────────────────
 * Computes exact SHA-256 binary Merkle tree root over leaf hashes.
 * Duplicates last node at odd levels (matching /backend/src/merkle.js).
 */

export async function sha256Hex(str) {
  const encoder = new TextEncoder();
  const data = encoder.encode(str);
  if (typeof crypto !== 'undefined' && crypto.subtle) {
    const hashBuffer = await crypto.subtle.digest('SHA-256', data);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
  } else {
    // Node.js fallback
    const nodeCrypto = await import('crypto');
    return nodeCrypto.createHash('sha256').update(str).digest('hex');
  }
}

/**
 * Recompute Merkle root over an array of record hash strings.
 * @param {string[]} leafHashes — array of record SHA-256 hashes
 * @returns {Promise<string>}   — 0x-prefixed 32-byte hex Merkle root
 */
export async function computeClientMerkleRoot(leafHashes) {
  if (!leafHashes || leafHashes.length === 0) return null;

  let level = [];
  for (const h of leafHashes) {
    level.push(await sha256Hex(h));
  }

  while (level.length > 1) {
    const next = [];
    for (let i = 0; i < level.length; i += 2) {
      const left = level[i];
      const right = i + 1 < level.length ? level[i + 1] : level[i];
      next.push(await sha256Hex(left + right));
    }
    level = next;
  }

  const rawRoot = level[0];
  return rawRoot.startsWith('0x') ? rawRoot.toLowerCase() : '0x' + rawRoot.toLowerCase();
}

/**
 * Format tamper flags canonically for client-side hash recomputation.
 */
export function formatClientTamperFlags(record) {
  if (typeof record.tamperFlags === 'string') return record.tamperFlags;
  if (typeof record.tamperFlags === 'object' && record.tamperFlags !== null) {
    return JSON.stringify(record.tamperFlags);
  }
  if (typeof record.tamper_flags === 'object' && record.tamper_flags !== null) {
    return JSON.stringify(record.tamper_flags);
  }
  if (typeof record.tamper_flags === 'string') return record.tamper_flags;
  return record.tamperState || record.tamper_state || 'NORMAL';
}

/**
 * Recompute a single record's expected SHA-256 hash on client side.
 * Phase 10 Canonical Formula:
 *   `${index}|${timestamp}|${temp}|${humidity}|${gas}|${tamperFlags}|${deviceSiliconID}|${previousHash}`
 */
export async function computeClientRecordHash(record) {
  const index = record.index ?? record.rec_index ?? 0;
  const timestamp = record.timestamp || '';
  const temp = record.temp;
  const humidity = record.humidity;
  const gas = record.gas;
  const tamperFlags = formatClientTamperFlags(record);
  const siliconId = record.deviceSiliconID || record.device_silicon_id || record.deviceSiliconId || '';
  const previousHash = record.previousHash || record.previous_hash || '0'.repeat(64);

  const payload = siliconId
    ? [index, timestamp, temp, humidity, gas, tamperFlags, siliconId, previousHash].join('|')
    : [index, timestamp, temp, humidity, gas, tamperFlags, previousHash].join('|');

  return await sha256Hex(payload);
}
