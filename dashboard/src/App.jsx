import React, { useState, useEffect, useRef } from 'react';
import { Html5QrcodeScanner } from 'html5-qrcode';
import { fetchOnChainRecord, fetchMultipleOnChainRecords } from './ledger';
import { verifyShipmentIntegrity, DEFAULT_TEMP_MIN, DEFAULT_TEMP_MAX } from './verifier';
import SenderDashboard from './components/SenderDashboard';
import AdminDashboard from './components/AdminDashboard';

const BACKEND_URL = (import.meta.env?.VITE_BACKEND_URL || 'https://frostledger-backend.onrender.com').replace(/\/+$/, '');
const CONTRACT_ADDRESS = import.meta.env?.VITE_CONTRACT_ADDRESS || '0xdeFE5922Dfd6af89571DfbD7c6559F01734E0C85';
const RPC_URL = import.meta.env?.VITE_RPC_URL || 'https://ethereum-sepolia-rpc.publicnode.com';

export default function App() {
  const [activeTab, setActiveTab] = useState('public'); // 'public' | 'sender' | 'admin'
  const [shipmentId, setShipmentId] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [onChainMap, setOnChainMap] = useState({});
  const [backendData, setBackendData] = useState(null);
  const [verification, setVerification] = useState(null);
  const [selectedBatchIdx, setSelectedBatchIdx] = useState(0);
  const [showScanner, setShowScanner] = useState(false);
  const [tempMin, setTempMin] = useState(DEFAULT_TEMP_MIN);
  const [tempMax, setTempMax] = useState(DEFAULT_TEMP_MAX);

  const scannerRef = useRef(null);

  // Initialize or teardown HTML5 QR Scanner
  useEffect(() => {
    if (showScanner) {
      const qrScanner = new Html5QrcodeScanner(
        "qr-reader",
        { fps: 10, qrbox: { width: 250, height: 250 } },
        false
      );

      qrScanner.render(
        (decodedText) => {
          setShipmentId(decodedText);
          setShowScanner(false);
          qrScanner.clear();
          inspectShipment(decodedText);
        },
        (err) => {
          // Scanner frame error ignored
        }
      );

      scannerRef.current = qrScanner;

      return () => {
        try {
          qrScanner.clear();
        } catch {}
      };
    }
  }, [showScanner]);

  async function inspectShipment(idToInspect) {
    const targetId = (idToInspect || shipmentId).trim();
    if (!targetId) return;

    setLoading(true);
    setError(null);
    setNotFound(false);
    setOnChainMap({});
    setBackendData(null);
    setVerification(null);
    setSelectedBatchIdx(0);

    try {
      // 1. Fetch Shipment & Multi-Batch Telemetry from Backend
      console.log(`[Dashboard] Fetching telemetry for shipment "${targetId}" from ${BACKEND_URL}/shipment/${targetId}...`);
      let data = null;
      try {
        const res = await fetch(`${BACKEND_URL}/shipment/${targetId}`);
        if (res.ok) {
          data = await res.json();
        }
      } catch (e) {
        console.warn('[Dashboard] Could not fetch directly from backend API:', e.message);
      }

      if (!data || (!data.batches && !data.shipment)) {
        // Fallback: try fetching on-chain directly
        const onChainSingle = await fetchOnChainRecord(targetId, CONTRACT_ADDRESS, RPC_URL);
        if (!onChainSingle || !onChainSingle.found) {
          setNotFound(true);
          setLoading(false);
          return;
        }
      }

      setBackendData(data);

      // 2. Determine all On-Chain keys to verify (base key + multi-batch keys)
      const batches = data?.batches || [];
      const onChainKeys = new Set([targetId]);
      batches.forEach((b) => {
        if (b.on_chain_key) onChainKeys.add(b.on_chain_key);
      });

      console.log(`[Dashboard] Querying smart contract on Sepolia for keys:`, Array.from(onChainKeys));
      const onChainResults = await fetchMultipleOnChainRecords(Array.from(onChainKeys), CONTRACT_ADDRESS, RPC_URL);
      setOnChainMap(onChainResults);

      const hasAnyOnChain = Object.values(onChainResults).some((r) => r && r.found);
      if (!hasAnyOnChain && (!batches || batches.length === 0)) {
        setNotFound(true);
        setLoading(false);
        return;
      }

      // 3. Cryptographic, Multi-Vector, and Multi-Batch Verification
      console.log(`[Dashboard] Running Phase 10 multi-vector verification engine...`);
      const report = await verifyShipmentIntegrity({
        onChainRecords: onChainResults,
        onChainRecord: onChainResults[targetId],
        backendData: data,
        tempMin: Number(tempMin),
        tempMax: Number(tempMax),
      });

      setVerification(report);
    } catch (err) {
      console.error("[Dashboard] Inspection error:", err);
      setError(err.message || "Failed to inspect shipment.");
    } finally {
      setLoading(false);
    }
  }

  async function downloadPdfReport() {
    const targetId = (backendData?.shipment?.shipment_id || backendData?.batches?.[0]?.shipment_id || shipmentId).trim();
    if (!targetId) return;

    const token = localStorage.getItem('token') || localStorage.getItem('jwt') || localStorage.getItem('authToken');
    if (!token) {
      alert('Please log in as Sender or Admin to download the authenticated cryptographic audit report.');
      return;
    }

    try {
      const res = await fetch(`${BACKEND_URL}/shipment/${targetId}/report.pdf`, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });

      if (!res.ok) {
        const err = await res.json().catch(() => ({ error: 'Download failed' }));
        alert(`Report download error: ${err.error || res.statusText}`);
        return;
      }

      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `FrostLedger_Report_${targetId}.pdf`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
    } catch (err) {
      alert(`Failed to download report: ${err.message}`);
    }
  }

  return (
    <div style={styles.appContainer}>
      {/* Top Header */}
      <header style={styles.header}>
        <div style={styles.headerInner}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <span style={{ fontSize: '28px' }}>❄️</span>
            <div>
              <h1 style={styles.title}>Cold-Chain Trust Inspector</h1>
              <p style={styles.subtitle}>Decentralized Cryptographic Provenance & Telemetry Verification</p>
            </div>
          </div>
          <div style={styles.networkBadge}>
            <span style={styles.networkDot}></span>
            <span>Polygon Amoy (80002)</span>
          </div>
        </div>

        {/* Global Navigation Tabs */}
        <div style={styles.navBar}>
          <button
            onClick={() => setActiveTab('public')}
            style={{
              ...styles.navTab,
              ...(activeTab === 'public' ? styles.navTabActive : {}),
            }}
          >
            🔍 Public Verification
          </button>
          <button
            onClick={() => setActiveTab('sender')}
            style={{
              ...styles.navTab,
              ...(activeTab === 'sender' ? styles.navTabActive : {}),
            }}
          >
            📦 Sender Portal
          </button>
          <button
            onClick={() => setActiveTab('admin')}
            style={{
              ...styles.navTab,
              ...(activeTab === 'admin' ? styles.navTabActive : {}),
            }}
          >
            🛡️ Admin Portal
          </button>
        </div>
      </header>

      {activeTab === 'sender' ? (
        <SenderDashboard
          backendUrl={BACKEND_URL}
          onInspectShipment={(id) => {
            setShipmentId(id);
            setActiveTab('public');
            inspectShipment(id);
          }}
        />
      ) : activeTab === 'admin' ? (
        <AdminDashboard
          backendUrl={BACKEND_URL}
          onInspectShipment={(id) => {
            setShipmentId(id);
            setActiveTab('public');
            inspectShipment(id);
          }}
        />
      ) : (
        <main style={styles.main}>

        {/* Search & Scanner Panel */}
        <div style={styles.card}>
          <h2 style={styles.cardTitle}>🔍 Inspect Shipment / Crate</h2>
          <p style={styles.cardDesc}>Scan the crate QR code or enter a shipment ID to verify on-chain authenticity and sensor chain integrity.</p>

          <div style={styles.searchRow}>
            <input
              type="text"
              placeholder="e.g. SHIP-1788605055696"
              value={shipmentId}
              onChange={(e) => setShipmentId(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && inspectShipment()}
              style={styles.input}
            />
            <button
              onClick={() => inspectShipment()}
              disabled={loading || !shipmentId.trim()}
              style={{ ...styles.btnPrimary, opacity: loading || !shipmentId.trim() ? 0.6 : 1 }}
            >
              {loading ? 'Inspecting...' : 'Verify Shipment'}
            </button>
            <button
              onClick={() => setShowScanner(!showScanner)}
              style={styles.btnSecondary}
            >
              📷 {showScanner ? 'Close Scanner' : 'Scan QR'}
            </button>
          </div>

          {/* Quick Demo Test Buttons */}
          <div style={styles.quickButtonsRow}>
            <span style={{ fontSize: '12px', color: '#64748b' }}>Quick Test Cases:</span>
            <button
              onClick={() => {
                setShipmentId('SHIP-1788605055696');
                inspectShipment('SHIP-1788605055696');
              }}
              style={styles.quickBtnGreen}
            >
              ✔ Clean Batch (Certified Fresh)
            </button>
            <button
              onClick={() => {
                setShipmentId('SHIP-1788605152399');
                inspectShipment('SHIP-1788605152399');
              }}
              style={styles.quickBtnRed}
            >
              ⚠ Tampered Batch (Fraud Warning)
            </button>
          </div>

          {/* QR Scanner Container */}
          {showScanner && (
            <div style={styles.qrContainer}>
              <div id="qr-reader" style={{ width: '100%', maxWidth: '400px', margin: '0 auto' }}></div>
            </div>
          )}
        </div>

        {/* Loading Spinner */}
        {loading && (
          <div style={styles.loadingBox}>
            <div style={styles.spinner}></div>
            <p style={{ marginTop: '14px', color: '#475569', fontWeight: '500' }}>
              Querying Polygon ledger & recomputing Merkle tree client-side...
            </p>
          </div>
        )}

        {/* Not Found / Not Certified State */}
        {notFound && !loading && (
          <div style={styles.notFoundBox}>
            <span style={{ fontSize: '40px' }}>📦</span>
            <h3 style={{ margin: '10px 0 6px', color: '#1e293b' }}>Shipment Not Found On-Chain</h3>
            <p style={{ color: '#64748b', maxWidth: '460px', margin: '0 auto' }}>
              Shipment ID <code style={styles.code}>{shipmentId}</code> has not been committed to the decentralized ledger yet or has not completed verification.
            </p>
          </div>
        )}

        {/* Error Alert */}
        {error && !loading && (
          <div style={styles.errorBox}>
            <strong>Error:</strong> {error}
          </div>
        )}

        {/* Verification Results Panel */}
        {verification && !loading && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            {/* Primary Trust Badge */}
            <div style={
              !verification.isCertifiedFresh
                ? styles.badgeBoxRed
                : verification.hasDriftWarning
                ? styles.badgeBoxAmber
                : styles.badgeBoxGreen
            }>
              <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                <span style={{ fontSize: '48px' }}>
                  {!verification.isCertifiedFresh ? '🚨' : verification.hasDriftWarning ? '⚠️' : '✅'}
                </span>
                <div>
                  <div style={styles.badgePill(verification.isCertifiedFresh, verification.hasDriftWarning)}>
                    {!verification.isCertifiedFresh
                      ? 'CRITICAL INTEGRITY / SECURITY VIOLATION'
                      : verification.hasDriftWarning
                      ? 'PREDICTIVE ANOMALY ALERT — DRIFT DETECTED'
                      : 'CRYPTOGRAPHICALLY VERIFIED & AUDITED'}
                  </div>
                  <h2 style={styles.badgeHeadline(verification.isCertifiedFresh, verification.hasDriftWarning)}>
                    {verification.badgeLabel}
                  </h2>
                  <p style={{
                    margin: '4px 0 0',
                    color: !verification.isCertifiedFresh ? '#991b1b' : verification.hasDriftWarning ? '#92400e' : '#065f46',
                    fontSize: '14px',
                  }}>
                    {!verification.isCertifiedFresh
                      ? 'Cold-chain integrity check failed! Cryptographic fraud, hardware breach, or spoilage detected.'
                      : verification.hasDriftWarning
                      ? `Statistical drift warning: ${verification.driftWarning?.warningMessage || `Temperature trending toward limit (${verification.driftWarning?.rateOfChange > 0 ? '+' : ''}${verification.driftWarning?.rateOfChange}°C/min). Projected breach in ~${verification.driftWarning?.projectedMinutesToBreach} mins.`}`
                      : `All ${verification.totalBatches} batch(es) and ${verification.totalRecords} sensor readings are authenticated across Ethereum Sepolia. Zero tamper vectors tripped.`}
                  </p>
                  <div style={{ marginTop: '12px' }}>
                    <button
                      onClick={downloadPdfReport}
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: '6px',
                        backgroundColor: '#0f172a',
                        color: '#ffffff',
                        border: 'none',
                        borderRadius: '6px',
                        padding: '8px 14px',
                        fontSize: '13px',
                        fontWeight: '600',
                        cursor: 'pointer',
                        boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
                      }}
                    >
                      📄 Download PDF Audit Report
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {/* Hardware Security & Provenance Banner */}
            <div style={styles.hardwareSecurityBanner}>
              <div style={styles.securityPill}>
                <span style={{ fontSize: '16px' }}>🔒</span>
                <div>
                  <div style={{ fontSize: '11px', color: '#64748b', fontWeight: '600' }}>BOUND DEVICE SILICON ID</div>
                  <div style={{ fontSize: '13px', fontWeight: '700', color: '#0f172a' }}>
                    {verification.boundDeviceSiliconId || 'Unbound / Legacy'}
                  </div>
                </div>
              </div>

              <div style={styles.securityPill}>
                <span style={{ fontSize: '16px' }}>🛡️</span>
                <div>
                  <div style={{ fontSize: '11px', color: '#64748b', fontWeight: '600' }}>ANTI-ROLLBACK COUNTER</div>
                  <div style={{ fontSize: '13px', fontWeight: '700', color: verification.violations.some(v => v.type === 'ROLLBACK_ATTEMPT') ? '#dc2626' : '#059669' }}>
                    {verification.violations.some(v => v.type === 'ROLLBACK_ATTEMPT') ? '🚨 REGRESSION DETECTED' : `Monotonic (Latest #${verification.highestSequence >= 0 ? verification.highestSequence : 'N/A'})`}
                  </div>
                </div>
              </div>

              <div style={styles.securityPill}>
                <span style={{ fontSize: '16px' }}>⚡</span>
                <div>
                  <div style={{ fontSize: '11px', color: '#64748b', fontWeight: '600' }}>MULTI-VECTOR SENSORS</div>
                  <div style={{ fontSize: '13px', fontWeight: '700', color: verification.violations.some(v => ['DOOR_OPENED', 'SD_REMOVED', 'SHELL_CUT', 'LIGHT_BREACH', 'MANUAL_SWITCH'].includes(v.type)) ? '#dc2626' : '#059669' }}>
                    {verification.violations.some(v => ['DOOR_OPENED', 'SD_REMOVED', 'SHELL_CUT', 'LIGHT_BREACH', 'MANUAL_SWITCH'].includes(v.type)) ? '⚠️ BREACH TRIPPED' : '5/5 Vectors Secure'}
                  </div>
                </div>
              </div>

              <div style={styles.securityPill}>
                <span style={{ fontSize: '16px' }}>📦</span>
                <div>
                  <div style={{ fontSize: '11px', color: '#64748b', fontWeight: '600' }}>TOTAL SYNC BATCHES</div>
                  <div style={{ fontSize: '13px', fontWeight: '700', color: '#0f172a' }}>
                    {verification.totalBatches} ({verification.totalRecords} readings)
                  </div>
                </div>
              </div>
            </div>

            {/* Multi-Batch Selector Tabs */}
            {verification.batchReports && verification.batchReports.length > 1 && (
              <div style={styles.batchTabRow}>
                <span style={{ fontSize: '13px', fontWeight: '600', color: '#475569', alignSelf: 'center' }}>Batches:</span>
                {verification.batchReports.map((b, idx) => (
                  <button
                    key={idx}
                    onClick={() => setSelectedBatchIdx(idx)}
                    style={{
                      ...styles.batchTabBtn,
                      ...(selectedBatchIdx === idx ? styles.batchTabBtnActive : {}),
                      borderColor: b.tamperDetected ? '#fca5a5' : '#cbd5e1',
                    }}
                  >
                    {b.tamperDetected ? '⚠️ ' : '✔ '}
                    Batch #{b.batchId} ({b.onChainKey})
                  </button>
                ))}
              </div>
            )}

            {/* Integrity Audit Breakdown for Selected Batch */}
            {(() => {
              const currentBatchReport = verification.batchReports?.[selectedBatchIdx] || verification.batchReports?.[0];
              const currentBatchOnChain = currentBatchReport?.onChainRecord;

              return (
                <div style={styles.grid2}>
                  {/* On-Chain Ledger Proof Card */}
                  <div style={styles.card}>
                    <h3 style={styles.sectionHeader}>🔗 Decentralized Ledger Proof (Batch #{currentBatchReport?.batchId})</h3>
                    <table style={styles.infoTable}>
                      <tbody>
                        <tr>
                          <td style={styles.tableLabel}>On-Chain Key</td>
                          <td style={styles.tableValue}><code style={styles.code}>{currentBatchReport?.onChainKey}</code></td>
                        </tr>
                        <tr>
                          <td style={styles.tableLabel}>On-Chain Merkle Root</td>
                          <td style={styles.tableValue}><code style={styles.code}>{currentBatchOnChain?.merkleRoot || 'Pending / Not on-chain'}</code></td>
                        </tr>
                        <tr>
                          <td style={styles.tableLabel}>Client Recomputed Root</td>
                          <td style={styles.tableValue}>
                            <code style={{ ...styles.code, color: currentBatchReport?.merkleMatch ? '#059669' : '#dc2626' }}>
                              {currentBatchReport?.clientMerkleRoot}
                            </code>
                          </td>
                        </tr>
                        <tr>
                          <td style={styles.tableLabel}>Merkle Match</td>
                          <td style={styles.tableValue}>
                            {currentBatchReport?.merkleMatch ? '✔ Exact Match (32 bytes)' : '✘ MISMATCH'}
                          </td>
                        </tr>
                        <tr>
                          <td style={styles.tableLabel}>Tamper Violation Flag</td>
                          <td style={styles.tableValue}>
                            <span style={currentBatchOnChain?.tamperViolation ? styles.tagRed : styles.tagGreen}>
                              {currentBatchOnChain?.tamperViolation ? 'TRUE (TAMPERED)' : 'FALSE (CLEAN)'}
                            </span>
                          </td>
                        </tr>
                        <tr>
                          <td style={styles.tableLabel}>Commit Timestamp</td>
                          <td style={styles.tableValue}>
                            {currentBatchOnChain?.timestamp ? new Date(currentBatchOnChain.timestamp * 1000).toLocaleString() : 'N/A'}
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>

                  {/* Safety & Thresholds Card */}
                  <div style={styles.card}>
                    <h3 style={styles.sectionHeader}>🌡️ Physical Cold-Chain & Multi-Vector Checklist</h3>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                      <div style={styles.checklistItem(currentBatchReport?.merkleMatch)}>
                        <span>{currentBatchReport?.merkleMatch ? '✔' : '✘'}</span>
                        <div>
                          <strong>Cryptographic Merkle Proof:</strong>{' '}
                          {currentBatchReport?.merkleMatch ? 'Verified against on-chain root' : 'FAILED — Data mismatch'}
                        </div>
                      </div>

                      <div style={styles.checklistItem(!currentBatchOnChain?.tamperViolation)}>
                        <span>{!currentBatchOnChain?.tamperViolation ? '✔' : '✘'}</span>
                        <div>
                          <strong>On-Chain Tamper Sensor:</strong>{' '}
                          {!currentBatchOnChain?.tamperViolation ? 'No hardware breach detected' : 'FAILED — Tamper flagged on ledger'}
                        </div>
                      </div>

                      <div style={styles.checklistItem(!verification.violations.some(v => v.type === 'TEMPERATURE_BREACH'))}>
                        <span>{!verification.violations.some(v => v.type === 'TEMPERATURE_BREACH') ? '✔' : '✘'}</span>
                        <div>
                          <strong>Temperature Compliance ({tempMin}°C – {tempMax}°C):</strong>{' '}
                          {!verification.violations.some(v => v.type === 'TEMPERATURE_BREACH') ? 'All readings in safe range' : 'FAILED — Temperature excursion'}
                        </div>
                      </div>

                      <div style={styles.checklistItem(!verification.violations.some(v => v.type === 'DEVICE_SUBSTITUTION'))}>
                        <span>{!verification.violations.some(v => v.type === 'DEVICE_SUBSTITUTION') ? '✔' : '✘'}</span>
                        <div>
                          <strong>Device Silicon ID Integrity:</strong>{' '}
                          {!verification.violations.some(v => v.type === 'DEVICE_SUBSTITUTION') ? 'Device silicon identity matches registered shipment' : 'FAILED — Device substitution detected'}
                        </div>
                      </div>

                      <div style={styles.checklistItem(!verification.violations.some(v => v.type === 'ROLLBACK_ATTEMPT'))}>
                        <span>{!verification.violations.some(v => v.type === 'ROLLBACK_ATTEMPT') ? '✔' : '✘'}</span>
                        <div>
                          <strong>Anti-Rollback Counter:</strong>{' '}
                          {!verification.violations.some(v => v.type === 'ROLLBACK_ATTEMPT') ? 'Monotonically increasing sequence index verified' : 'FAILED — Counter regression detected'}
                        </div>
                      </div>
                    </div>

                    {/* Specific Multi-Vector Violation Details */}
                    {verification.violations.length > 0 && (
                      <div style={styles.violationBox}>
                        <h4 style={{ margin: '0 0 8px', color: '#b91c1c' }}>🚨 Violation & Tamper Vector Breakdown:</h4>
                        {verification.violations.map((v, i) => (
                          <div key={i} style={{ marginBottom: '8px', fontSize: '13px' }}>
                            <strong>{v.title}:</strong> {v.detail}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              );
            })()}

            {/* Sensor Telemetry Readings Table */}
            {(() => {
              const currentBatch = backendData?.batches?.[selectedBatchIdx] || backendData?.batches?.[0];
              const recordsToShow = currentBatch?.records || [];

              return (
                <div style={styles.card}>
                  <h3 style={styles.sectionHeader}>
                    📊 Sensor Telemetry Readings — Batch #{currentBatch?.id || 1} ({recordsToShow.length} records)
                  </h3>
                  <div style={{ overflowX: 'auto' }}>
                    <table style={styles.dataTable}>
                      <thead>
                        <tr style={styles.tableHeaderRow}>
                          <th style={styles.th}>Log Idx</th>
                          <th style={styles.th}>Seq #</th>
                          <th style={styles.th}>Silicon ID</th>
                          <th style={styles.th}>Timestamp</th>
                          <th style={styles.th}>Temp (°C)</th>
                          <th style={styles.th}>Humidity (%)</th>
                          <th style={styles.th}>Gas (ppm)</th>
                          <th style={styles.th}>Multi-Vector Tamper</th>
                          <th style={styles.th}>Predictive Anomaly</th>
                          <th style={styles.th}>Location</th>
                          <th style={styles.th}>Hash Chain</th>
                          <th style={styles.th}>Record Hash</th>
                        </tr>
                      </thead>
                      <tbody>
                        {recordsToShow.map((r, idx) => {
                          const tempOut = r.temp < tempMin || r.temp > tempMax;
                          const chainBad = !r.chain_valid;
                          const hasTamper = r.any_tamper || r.door_opened || r.sd_removed || r.shell_cut || r.light_breach || r.manual_switch;
                          const anomaly = r.anomaly_warning || r.anomalyWarning;

                          return (
                            <tr key={idx} style={tempOut || chainBad || hasTamper ? styles.trViolation : styles.trNormal}>
                              <td style={styles.td}>#{r.rec_index ?? idx}</td>
                              <td style={styles.td}>
                                <code style={{ fontSize: '11px', color: '#334155' }}>
                                  {r.device_sequence_index !== null && r.device_sequence_index !== undefined ? `#${r.device_sequence_index}` : 'N/A'}
                                </code>
                              </td>
                              <td style={styles.td}>
                                <code style={{ fontSize: '11px', color: '#0284c7' }}>
                                  {r.device_silicon_id ? r.device_silicon_id.slice(0, 12) + '...' : 'N/A'}
                                </code>
                              </td>
                              <td style={styles.td}>{new Date(r.timestamp).toLocaleTimeString()}</td>
                              <td style={{ ...styles.td, fontWeight: 'bold', color: tempOut ? '#dc2626' : '#059669' }}>
                                {r.temp}°C {tempOut && '⚠'}
                              </td>
                              <td style={styles.td}>{r.humidity}%</td>
                              <td style={styles.td}>{r.gas} ppm</td>
                              <td style={styles.td}>
                                {hasTamper ? (
                                  <span style={styles.tagRed}>
                                    {[
                                      r.door_opened && 'LID_OPEN',
                                      r.sd_removed && 'SD_EJECT',
                                      r.shell_cut && 'SHELL_CUT',
                                      r.light_breach && 'LIGHT_ALERT',
                                      r.manual_switch && 'SWITCH_TRIPPED',
                                    ].filter(Boolean).join(', ') || 'TAMPERED'}
                                  </span>
                                ) : (
                                  <span style={styles.tagGreen}>NORMAL</span>
                                )}
                              </td>
                              <td style={styles.td}>
                                {anomaly?.active ? (
                                  <span style={styles.tagAmber}>
                                    ⚠️ {anomaly.direction === 'rising' ? '↗ Rising' : '↘ Falling'} ({anomaly.rateOfChange > 0 ? '+' : ''}{anomaly.rateOfChange}°C/m, ~{anomaly.projectedMinutesToBreach}m)
                                  </span>
                                ) : (
                                  <span style={{ fontSize: '11px', color: '#94a3b8' }}>Stable</span>
                                )}
                              </td>
                              <td style={styles.td}>
                                {r.latitude !== null && r.latitude !== undefined && r.longitude !== null && r.longitude !== undefined ? (
                                  <span style={{ fontSize: '11px', color: '#0284c7', fontFamily: 'monospace' }}>
                                    📍 {Number(r.latitude).toFixed(3)}, {Number(r.longitude).toFixed(3)}
                                  </span>
                                ) : (
                                  <span style={{ fontSize: '11px', color: '#94a3b8' }}>Transit GPS</span>
                                )}
                              </td>
                              <td style={styles.td}>
                                <span style={r.chain_valid ? styles.tagGreen : styles.tagRed}>
                                  {r.chain_valid ? 'VALID' : 'CORRUPTED'}
                                </span>
                              </td>
                              <td style={styles.td}>
                                <code style={{ fontSize: '11px', color: '#64748b' }}>
                                  {r.hash ? r.hash.slice(0, 14) + '...' : 'N/A'}
                                </code>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </div>
              );
            })()}
          </div>
        )}
      </main>
      )}
    </div>
  );
}

// ── Modern Clean Styles ───────────────────────────────────────────────────────
const styles = {
  appContainer: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu, sans-serif',
    background: '#f8fafc',
    minHeight: '100vh',
    color: '#0f172a',
  },
  header: {
    background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
    color: '#fff',
    padding: '20px 24px 0 24px',
    boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1)',
  },
  headerInner: {
    maxWidth: '1200px',
    margin: '0 auto 16px auto',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: '12px',
  },
  navBar: {
    maxWidth: '1200px',
    margin: '0 auto',
    display: 'flex',
    gap: '8px',
  },
  navTab: {
    padding: '10px 18px',
    background: 'transparent',
    border: 'none',
    borderBottom: '3px solid transparent',
    color: '#94a3b8',
    fontSize: '14px',
    fontWeight: '600',
    cursor: 'pointer',
    transition: 'all 0.2s',
  },
  navTabActive: {
    color: '#38bdf8',
    borderBottom: '3px solid #38bdf8',
  },

  title: { margin: 0, fontSize: '22px', fontWeight: '700', letterSpacing: '-0.5px' },
  subtitle: { margin: '2px 0 0', fontSize: '13px', color: '#94a3b8' },
  networkBadge: {
    background: 'rgba(255,255,255,0.08)',
    border: '1px solid rgba(255,255,255,0.15)',
    borderRadius: '20px',
    padding: '6px 14px',
    fontSize: '12px',
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
  },
  networkDot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
    background: '#10b981',
    boxShadow: '0 0 8px #10b981',
  },
  main: {
    maxWidth: '1100px',
    margin: '24px auto',
    padding: '0 16px 40px',
    display: 'flex',
    flexDirection: 'column',
    gap: '20px',
  },
  card: {
    background: '#fff',
    borderRadius: '12px',
    padding: '24px',
    border: '1px solid #e2e8f0',
    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
  },
  cardTitle: { margin: '0 0 6px', fontSize: '18px', fontWeight: '600' },
  cardDesc: { margin: '0 0 18px', fontSize: '14px', color: '#64748b' },
  searchRow: {
    display: 'flex',
    gap: '10px',
    flexWrap: 'wrap',
  },
  input: {
    flex: '1 1 260px',
    padding: '12px 16px',
    fontSize: '15px',
    borderRadius: '8px',
    border: '1px solid #cbd5e1',
    outline: 'none',
  },
  btnPrimary: {
    background: '#2563eb',
    color: '#fff',
    border: 'none',
    borderRadius: '8px',
    padding: '12px 22px',
    fontWeight: '600',
    cursor: 'pointer',
  },
  btnSecondary: {
    background: '#f1f5f9',
    color: '#334155',
    border: '1px solid #cbd5e1',
    borderRadius: '8px',
    padding: '12px 18px',
    fontWeight: '600',
    cursor: 'pointer',
  },
  quickButtonsRow: {
    marginTop: '14px',
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
    flexWrap: 'wrap',
  },
  quickBtnGreen: {
    background: '#ecfdf5',
    color: '#047857',
    border: '1px solid #a7f3d0',
    borderRadius: '6px',
    padding: '6px 12px',
    fontSize: '12px',
    fontWeight: '600',
    cursor: 'pointer',
  },
  quickBtnRed: {
    background: '#fef2f2',
    color: '#b91c1c',
    border: '1px solid #fecaca',
    borderRadius: '6px',
    padding: '6px 12px',
    fontSize: '12px',
    fontWeight: '600',
    cursor: 'pointer',
  },
  qrContainer: {
    marginTop: '20px',
    padding: '16px',
    background: '#f8fafc',
    borderRadius: '8px',
    border: '1px dashed #cbd5e1',
  },
  loadingBox: {
    background: '#fff',
    borderRadius: '12px',
    padding: '40px',
    textAlign: 'center',
    border: '1px solid #e2e8f0',
  },
  spinner: {
    width: '36px',
    height: '36px',
    border: '4px solid #e2e8f0',
    borderTopColor: '#2563eb',
    borderRadius: '50%',
    margin: '0 auto',
    animation: 'spin 1s linear infinite',
  },
  notFoundBox: {
    background: '#fff',
    borderRadius: '12px',
    padding: '40px',
    textAlign: 'center',
    border: '1px solid #e2e8f0',
  },
  errorBox: {
    background: '#fef2f2',
    color: '#991b1b',
    padding: '16px 20px',
    borderRadius: '8px',
    border: '1px solid #fecaca',
  },
  badgeBoxGreen: {
    background: '#f0fdf4',
    border: '2px solid #22c55e',
    borderRadius: '12px',
    padding: '24px',
    boxShadow: '0 4px 12px rgba(34,197,94,0.12)',
  },
  badgeBoxAmber: {
    background: '#fffbeb',
    border: '2px solid #f59e0b',
    borderRadius: '12px',
    padding: '24px',
    boxShadow: '0 4px 12px rgba(245,158,11,0.12)',
  },
  badgeBoxRed: {
    background: '#fef2f2',
    border: '2px solid #ef4444',
    borderRadius: '12px',
    padding: '24px',
    boxShadow: '0 4px 12px rgba(239,68,68,0.12)',
  },
  badgePill: (green, amber) => ({
    display: 'inline-block',
    fontSize: '11px',
    fontWeight: '700',
    letterSpacing: '0.5px',
    textTransform: 'uppercase',
    padding: '3px 8px',
    borderRadius: '4px',
    background: !green ? '#fee2e2' : amber ? '#fef3c7' : '#dcfce7',
    color: !green ? '#b91c1c' : amber ? '#92400e' : '#15803d',
    marginBottom: '4px',
  }),
  badgeHeadline: (green, amber) => ({
    margin: '0',
    fontSize: '26px',
    fontWeight: '800',
    letterSpacing: '-0.5px',
    color: !green ? '#b91c1c' : amber ? '#b45309' : '#15803d',
  }),
  grid2: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
    gap: '20px',
  },
  sectionHeader: {
    margin: '0 0 14px',
    fontSize: '16px',
    fontWeight: '700',
    color: '#1e293b',
    borderBottom: '1px solid #f1f5f9',
    paddingBottom: '8px',
  },
  infoTable: { width: '100%', borderCollapse: 'collapse' },
  tableLabel: { padding: '8px 4px', fontSize: '13px', color: '#64748b', fontWeight: '500', width: '40%' },
  tableValue: { padding: '8px 4px', fontSize: '13px', color: '#0f172a' },
  code: {
    background: '#f1f5f9',
    padding: '2px 6px',
    borderRadius: '4px',
    fontSize: '12px',
    fontFamily: 'monospace',
  },
  checklistItem: (pass) => ({
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
    padding: '10px 12px',
    borderRadius: '8px',
    fontSize: '13px',
    background: pass ? '#f0fdf4' : '#fef2f2',
    color: pass ? '#166534' : '#991b1b',
    border: `1px solid ${pass ? '#bbf7d0' : '#fecaca'}`,
  }),
  violationBox: {
    marginTop: '16px',
    padding: '14px',
    background: '#fef2f2',
    borderRadius: '8px',
    border: '1px solid #f87171',
  },
  dataTable: { width: '100%', borderCollapse: 'collapse', fontSize: '13px' },
  tableHeaderRow: { background: '#f8fafc', borderBottom: '2px solid #e2e8f0' },
  th: { padding: '10px 12px', textAlign: 'left', fontWeight: '600', color: '#475569' },
  td: { padding: '10px 12px', borderBottom: '1px solid #f1f5f9' },
  trNormal: { background: '#fff' },
  trViolation: { background: '#fff1f2' },
  tagGreen: {
    background: '#dcfce7',
    color: '#15803d',
    padding: '2px 8px',
    borderRadius: '12px',
    fontSize: '11px',
    fontWeight: '600',
  },
  tagAmber: {
    background: '#fef3c7',
    color: '#92400e',
    padding: '2px 8px',
    borderRadius: '12px',
    fontSize: '11px',
    fontWeight: '600',
  },
  tagRed: {
    background: '#fee2e2',
    color: '#b91c1c',
    padding: '2px 8px',
    borderRadius: '12px',
    fontSize: '11px',
    fontWeight: '600',
  },
  hardwareSecurityBanner: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
    gap: '12px',
    padding: '16px',
    background: '#fff',
    borderRadius: '12px',
    border: '1px solid #e2e8f0',
    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
  },
  securityPill: {
    display: 'flex',
    alignItems: 'center',
    gap: '10px',
    padding: '8px 12px',
    background: '#f8fafc',
    borderRadius: '8px',
    border: '1px solid #f1f5f9',
  },
  batchTabRow: {
    display: 'flex',
    gap: '8px',
    flexWrap: 'wrap',
    padding: '12px 16px',
    background: '#fff',
    borderRadius: '10px',
    border: '1px solid #e2e8f0',
  },
  batchTabBtn: {
    padding: '8px 14px',
    background: '#f8fafc',
    border: '1px solid #cbd5e1',
    borderRadius: '6px',
    fontSize: '13px',
    fontWeight: '600',
    color: '#334155',
    cursor: 'pointer',
    transition: 'all 0.15s ease',
  },
  batchTabBtnActive: {
    background: '#0284c7',
    borderColor: '#0284c7',
    color: '#fff',
  },
};
