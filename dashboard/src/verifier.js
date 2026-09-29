/**
 * Cold-Chain Inspection & Cryptographic Verifier Engine — Phase 10
 * ────────────────────────────────────────────────────────────────
 * Comprehensive Verification across all batches & security pillars:
 * 1. Multi-Batch On-Chain Merkle Root Integrity (Client recomputed root == On-Chain root for each batch)
 * 2. Temperature Safety Window (2.0°C <= Temp <= 8.0°C)
 * 3. On-Chain Tamper Violation Flags
 * 4. Multi-Vector Physical Tamper Sensors (Lid/Door, SD card, Shell Cut, Light Breach, Manual Switch)
 * 5. Device Silicon ID Binding & Substitution Detection
 * 6. Hardware Anti-Rollback Monotonic Counter Check
 */

import { computeClientMerkleRoot } from './merkle.js';

export const DEFAULT_TEMP_MIN = 2.0;
export const DEFAULT_TEMP_MAX = 8.0;

/**
 * Verify multi-batch shipment integrity against blockchain records.
 *
 * @param {object} params
 * @param {object|Map} params.onChainRecords — Map or object of onChainKey -> onChainRecord (or single onChainRecord)
 * @param {object} params.backendData        — GET /shipment/:id response object
 * @param {Array}  [params.records]          — Fallback flat records array
 * @param {number} [params.tempMin]          — Min safe temp (°C)
 * @param {number} [params.tempMax]          — Max safe temp (°C)
 * @returns {Promise<object>} Comprehensive Verification Report
 */
export async function verifyShipmentIntegrity({
  onChainRecords,
  onChainRecord,
  backendData,
  records: fallbackRecords,
  tempMin = DEFAULT_TEMP_MIN,
  tempMax = DEFAULT_TEMP_MAX,
}) {
  const violations = [];
  const batches = backendData?.batches && backendData.batches.length > 0
    ? backendData.batches
    : [{ id: 1, on_chain_key: backendData?.shipmentId || 'default', records: fallbackRecords || [] }];

  // Standardize onChainRecords lookup
  const getOnChainForBatch = (batchKey) => {
    if (!batchKey) return onChainRecord || null;
    if (onChainRecords && onChainRecords[batchKey]) return onChainRecords[batchKey];
    if (onChainRecord && (onChainRecord.shipmentId === batchKey || onChainRecord.found)) return onChainRecord;
    return null;
  };

  const boundDeviceSiliconId = backendData?.deviceSiliconId || backendData?.shipment?.device_silicon_id;
  const batchReports = [];
  const allRecords = [];
  let highestSeenSequence = -1;

  // ── 1. Batch-by-Batch Verification ──────────────────────────────────────────
  for (let bIdx = 0; bIdx < batches.length; bIdx++) {
    const batch = batches[bIdx];
    const batchRecords = batch.records || [];
    allRecords.push(...batchRecords);

    const onChainKey = batch.on_chain_key || (bIdx === 0 ? backendData?.shipmentId : `${backendData?.shipmentId}-B${batch.id}`);
    const onChain = getOnChainForBatch(onChainKey);

    const batchViolations = [];

    // (A) Merkle Root Integrity
    const leafHashes = batchRecords.map((r) => r.hash);
    const clientMerkleRoot = await computeClientMerkleRoot(leafHashes);
    const onChainRoot = typeof onChain?.merkleRoot === 'string' ? onChain.merkleRoot.toLowerCase() : '';
    const merkleMatch = onChainRoot ? clientMerkleRoot === onChainRoot : true;

    if (onChainRoot && !merkleMatch) {
      const v = {
        type: 'MERKLE_MISMATCH',
        title: `Batch #${batch.id} Merkle Root Mismatch`,
        detail: `Batch #${batch.id} client root (${clientMerkleRoot?.slice(0, 18)}...) does not match on-chain root (${onChainRoot.slice(0, 18)}...). Records may have been altered after commitment.`,
        batchId: batch.id,
        onChainKey,
        clientMerkleRoot,
        onChainRoot,
      };
      violations.push(v);
      batchViolations.push(v);
    }

    // (B) On-Chain Tamper Violation Flag
    if (onChain?.tamperViolation) {
      const v = {
        type: 'ON_CHAIN_TAMPER_FLAG',
        title: `Batch #${batch.id} Flagged on Blockchain`,
        detail: `The immutable blockchain record for "${onChainKey}" has the tamper violation flag set to TRUE.`,
        batchId: batch.id,
        onChainKey,
      };
      violations.push(v);
      batchViolations.push(v);
    }

    // (C) Device Silicon ID Binding Check
    const batchSiliconId = batch.device_silicon_id || batchRecords[0]?.device_silicon_id || batchRecords[0]?.deviceSiliconID;
    if (boundDeviceSiliconId && batchSiliconId && batchSiliconId !== boundDeviceSiliconId) {
      const v = {
        type: 'DEVICE_SUBSTITUTION',
        title: `Device Substitution Detected (Batch #${batch.id})`,
        detail: `Batch #${batch.id} was transmitted by unauthorized device "${batchSiliconId}" (expected bound device "${boundDeviceSiliconId}").`,
        batchId: batch.id,
        expectedDevice: boundDeviceSiliconId,
        actualDevice: batchSiliconId,
      };
      violations.push(v);
      batchViolations.push(v);
    }

    // (D) Multi-Vector Physical Tamper Checks
    batchRecords.forEach((rec, rIdx) => {
      const idx = rec.rec_index ?? rec.index ?? rIdx;
      const ts = rec.timestamp || 'Unknown Time';

      if (rec.door_opened || rec.tamper_flags?.doorOpened) {
        const v = {
          type: 'DOOR_OPENED',
          title: 'Container Door / Lid Breach',
          detail: `Container lid/door opened at record #${idx} (${ts}).`,
          timestamp: ts,
          recordIndex: idx,
          batchId: batch.id,
        };
        violations.push(v);
        batchViolations.push(v);
      }

      if (rec.sd_removed || rec.tamper_flags?.sdRemoved) {
        const v = {
          type: 'SD_REMOVED',
          title: 'SD Storage Card Ejected',
          detail: `Local cryptographic storage ejected / SD removed at record #${idx} (${ts}).`,
          timestamp: ts,
          recordIndex: idx,
          batchId: batch.id,
        };
        violations.push(v);
        batchViolations.push(v);
      }

      if (rec.shell_cut || rec.tamper_flags?.shellCut) {
        const v = {
          type: 'SHELL_CUT',
          title: 'Chassis / Shell Penetration',
          detail: `Enclosure shell cut or casing pierced at record #${idx} (${ts}).`,
          timestamp: ts,
          recordIndex: idx,
          batchId: batch.id,
        };
        violations.push(v);
        batchViolations.push(v);
      }

      if (rec.light_breach || rec.tamper_flags?.lightBreach) {
        const v = {
          type: 'LIGHT_BREACH',
          title: 'Enclosure Light Sensor Alert',
          detail: `Optical breach / light exposure detected at record #${idx} (${ts}).`,
          timestamp: ts,
          recordIndex: idx,
          batchId: batch.id,
        };
        violations.push(v);
        batchViolations.push(v);
      }

      if (rec.manual_switch || rec.tamper_flags?.manualSwitch) {
        const v = {
          type: 'MANUAL_SWITCH',
          title: 'Manual Anti-Tamper Switch Tripped',
          detail: `Hardware tamper microswitch triggered at record #${idx} (${ts}).`,
          timestamp: ts,
          recordIndex: idx,
          batchId: batch.id,
        };
        violations.push(v);
        batchViolations.push(v);
      }

      // (E) Anti-Rollback Monotonic Counter Check
      const seq = rec.device_sequence_index ?? rec.deviceSequenceIndex;
      if (seq !== undefined && seq !== null) {
        const numSeq = Number(seq);
        if (highestSeenSequence >= 0 && numSeq <= highestSeenSequence) {
          const v = {
            type: 'ROLLBACK_ATTEMPT',
            title: `Anti-Rollback Counter Violation (Batch #${batch.id})`,
            detail: `Device sequence counter regressed at record #${idx}: sequence #${numSeq} received after #${highestSeenSequence}. Firmware rollback or replay attempt suspected.`,
            timestamp: ts,
            recordIndex: idx,
            sequence: numSeq,
            previousHighest: highestSeenSequence,
            batchId: batch.id,
          };
          violations.push(v);
          batchViolations.push(v);
        } else {
          highestSeenSequence = Math.max(highestSeenSequence, numSeq);
        }
      }
    });

    batchReports.push({
      batchId: batch.id,
      onChainKey,
      recordCount: batchRecords.length,
      clientMerkleRoot,
      onChainRoot,
      merkleMatch,
      tamperDetected: batch.tamper_detected || batchViolations.length > 0,
      violations: batchViolations,
      onChainRecord: onChain,
    });
  }

  // ── 2. Sensor Reading Temperature & Local Chain Continuity Checks ──────────
  const tempViolations = [];
  const chainViolations = [];

  allRecords.forEach((rec, idx) => {
    const recIdx = rec.rec_index ?? rec.index ?? idx;
    // Temperature limit check
    if (rec.temp < tempMin || rec.temp > tempMax) {
      tempViolations.push({
        index: recIdx,
        timestamp: rec.timestamp,
        temp: rec.temp,
        detail: `Record #${recIdx} temp ${rec.temp}°C outside safe range [${tempMin}°C - ${tempMax}°C]`,
      });
    }

    // Individual record chain validity
    if (rec.chain_valid === false || rec.chain_valid === 0) {
      chainViolations.push({
        index: recIdx,
        timestamp: rec.timestamp,
        detail: `Record #${recIdx} failed local cryptographic hash chain validation`,
      });
    }
  });

  if (tempViolations.length > 0) {
    violations.push({
      type: 'TEMPERATURE_BREACH',
      title: 'Cold-Chain Temperature Breach',
      detail: `${tempViolations.length} reading(s) violated the safe cold-chain window (${tempMin}°C – ${tempMax}°C). First breach at index #${tempViolations[0].index} (${tempViolations[0].temp}°C at ${tempViolations[0].timestamp}).`,
      breaches: tempViolations,
      firstViolation: tempViolations[0],
    });
  }

  if (chainViolations.length > 0) {
    violations.push({
      type: 'LOCAL_CHAIN_CORRUPTED',
      title: 'Sensor Hash-Chain Link Corrupted',
      detail: `Record index #${chainViolations[0].index} at ${chainViolations[0].timestamp} has an invalid cryptographic hash signature.`,
      firstViolation: chainViolations[0],
    });
  }

  // ── 3. Predictive Anomaly & Drift Warning Detection ──────────────────────────
  let latestAnomalyWarning = null;
  for (const b of batches) {
    if (b.anomalyWarning?.active || b.anomaly_warning?.active) {
      latestAnomalyWarning = b.anomalyWarning || b.anomaly_warning;
    }
    for (const r of (b.records || [])) {
      if (r.anomalyWarning?.active || r.anomaly_warning?.active) {
        latestAnomalyWarning = r.anomalyWarning || r.anomaly_warning;
      }
    }
  }

  const isCertifiedFresh = violations.length === 0;
  const hasDriftWarning = isCertifiedFresh && !!(latestAnomalyWarning && latestAnomalyWarning.active);

  let badge = 'FRAUD_WARNING';
  let badgeLabel = 'Fraud / Spoilage Warning';
  let badgeColor = 'red';

  if (isCertifiedFresh) {
    if (hasDriftWarning) {
      badge = 'ANOMALY_DRIFT_WARNING';
      badgeLabel = 'Trending Toward Temperature Breach';
      badgeColor = 'amber';
    } else {
      badge = 'CERTIFIED_FRESH';
      badgeLabel = 'Certified Fresh';
      badgeColor = 'green';
    }
  }

  return {
    isCertifiedFresh,
    hasDriftWarning,
    driftWarning: latestAnomalyWarning,
    badge,
    badgeLabel,
    badgeColor,
    boundDeviceSiliconId,
    totalBatches: batches.length,
    totalRecords: allRecords.length,
    highestSequence: highestSeenSequence,
    batchReports,
    violations,
    checkedAt: new Date().toISOString(),
  };
}
