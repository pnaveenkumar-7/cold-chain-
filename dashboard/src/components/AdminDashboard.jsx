import React, { useState, useEffect } from 'react';

export default function AdminDashboard({ backendUrl, onInspectShipment }) {
  const [token, setToken] = useState(null);
  const [currentUser, setCurrentUser] = useState(null);

  // Login states
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [authError, setAuthError] = useState(null);
  const [authLoading, setAuthLoading] = useState(false);

  // Admin Data states
  const [stats, setStats] = useState(null);
  const [shipments, setShipments] = useState([]);
  const [loadingData, setLoadingData] = useState(false);
  const [dataError, setDataError] = useState(null);

  // ── Admin Login Handler ─────────────────────────────────────────────────────
  async function handleLogin(e) {
    e.preventDefault();
    setAuthError(null);
    setAuthLoading(true);

    try {
      const res = await fetch(`${backendUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });

      const data = await res.json();
      if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);

      if (data.user?.role !== 'admin') {
        throw new Error('Access denied: Admin Portal requires an account with the "admin" role.');
      }

      setToken(data.token);
      setCurrentUser(data.user);
    } catch (err) {
      setAuthError(err.message);
    } finally {
      setAuthLoading(false);
    }
  }

  function handleLogout() {
    setToken(null);
    setCurrentUser(null);
    setStats(null);
    setShipments([]);
  }

  // ── Load Admin Stats and Shipments ──────────────────────────────────────────
  async function loadAdminData() {
    if (!token) return;
    setLoadingData(true);
    setDataError(null);

    try {
      // 1. Fetch Stats
      const statsRes = await fetch(`${backendUrl}/admin/stats`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const statsData = await statsRes.json();
      if (!statsRes.ok) throw new Error(statsData.error || 'Failed to load system stats');
      setStats(statsData.systemStats);

      // 2. Fetch Master Shipments List
      const shipRes = await fetch(`${backendUrl}/admin/shipments`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const shipData = await shipRes.json();
      if (!shipRes.ok) throw new Error(shipData.error || 'Failed to load master shipments');
      setShipments(shipData.shipments || []);
    } catch (err) {
      setDataError(err.message);
    } finally {
      setLoadingData(false);
    }
  }

  useEffect(() => {
    if (token) {
      loadAdminData();
    }
  }, [token]);

  // ── Update Shipment Status (Metadata Only) ──────────────────────────────────
  async function handleStatusChange(shipmentId, newStatus) {
    try {
      const res = await fetch(`${backendUrl}/shipments/${shipmentId}/status`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ status: newStatus }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
      loadAdminData();
    } catch (err) {
      alert(`Failed to update status: ${err.message}`);
    }
  }

  // ── Render: Admin Login Form ────────────────────────────────────────────────
  if (!token) {
    return (
      <div style={styles.authContainer}>
        <div style={styles.authCard}>
          <div style={{ textAlign: 'center', marginBottom: '24px' }}>
            <span style={{ fontSize: '40px' }}>🛡️</span>
            <h2 style={{ color: '#0f172a', margin: '8px 0 4px 0', fontSize: '24px' }}>Admin Portal</h2>
            <p style={{ color: '#64748b', fontSize: '14px', margin: 0 }}>
              Restricted management portal. Requires verified administrative credentials.
            </p>
          </div>

          {authError && <div style={styles.errorBanner}>⚠️ {authError}</div>}

          <form onSubmit={handleLogin} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div>
              <label style={styles.label}>Admin Email Address</label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="admin@coldchain.com"
                style={styles.input}
              />
            </div>

            <div>
              <label style={styles.label}>Admin Password</label>
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                style={styles.input}
              />
            </div>

            <button type="submit" disabled={authLoading} style={styles.primaryBtn}>
              {authLoading ? 'Authenticating...' : '🛡️ Sign In to Admin Console'}
            </button>
          </form>

          <p style={{ fontSize: '12px', color: '#64748b', marginTop: '16px', textAlign: 'center' }}>
            ℹ️ Root admin accounts are provisioned out-of-band via <code style={styles.code}>scripts/seed-admin.js</code>.
          </p>
        </div>
      </div>
    );
  }

  // ── Render: Authenticated Admin Dashboard ───────────────────────────────────
  return (
    <div style={styles.dashboardContainer}>
      {/* Top Profile Bar */}
      <div style={styles.profileBar}>
        <div>
          <h2 style={{ margin: 0, fontSize: '20px', color: '#0f172a' }}>🛡️ Master Administration Console</h2>
          <span style={{ fontSize: '13px', color: '#64748b' }}>
            Authenticated Admin: <strong>{currentUser?.email}</strong> <span style={styles.adminBadge}>ROOT ADMIN</span>
          </span>
        </div>
        <div style={{ display: 'flex', gap: '10px' }}>
          <button onClick={loadAdminData} style={styles.secondaryBtn}>
            🔄 Refresh Data
          </button>
          <button onClick={handleLogout} style={styles.secondaryBtn}>
            🚪 Log Out
          </button>
        </div>
      </div>

      {/* Prominent Cryptographic Immutability Notice Banner */}
      <div style={styles.immutabilityBanner}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: '12px' }}>
          <span style={{ fontSize: '24px' }}>🔒</span>
          <div>
            <strong style={{ color: '#0369a1', fontSize: '14px' }}>Cryptographic Immutability Enforced by Protocol</strong>
            <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: '#0c4a6e', lineHeight: 1.5 }}>
              Sensor telemetry, hash chain records, Merkle roots, and on-chain blockchain commitments are <strong>strictly append-only and immutable</strong>.
              No user or administrative role has permissions to modify, overwrite, or delete sensor records.
              Administrators can manage shipment metadata and view comprehensive cryptographic audits.
            </p>
          </div>
        </div>
      </div>

      {dataError && <div style={styles.errorBanner}>⚠️ {dataError}</div>}

      {/* System Metrics Overview */}
      {stats && (
        <div style={styles.statsGrid}>
          <div style={styles.statCard}>
            <span style={styles.statLabel}>Total Users</span>
            <span style={styles.statValue}>{stats.totalUsers}</span>
            <span style={styles.statSub}>{stats.sendersCount} Senders / {stats.adminsCount} Admin</span>
          </div>
          <div style={styles.statCard}>
            <span style={styles.statLabel}>Total Shipments</span>
            <span style={styles.statValue}>{stats.totalShipments}</span>
            <span style={styles.statSub}>Across all registered senders</span>
          </div>
          <div style={styles.statCard}>
            <span style={styles.statLabel}>Committed On-Chain</span>
            <span style={{ ...styles.statValue, color: '#16a34a' }}>{stats.committedShipments}</span>
            <span style={styles.statSub}>Secured with Merkle Roots</span>
          </div>
          <div style={styles.statCard}>
            <span style={styles.statLabel}>KMS Key Rotations</span>
            <span style={{ ...styles.statValue, color: '#0284c7' }}>{stats.keyRotationEvents || 0}</span>
            <span style={styles.statSub}>Cryptographic handover events</span>
          </div>
        </div>
      )}

      {/* Master Shipments Table */}
      <div style={styles.tableCard}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '18px', color: '#0f172a' }}>🌐 Master Shipments Ledger ({shipments.length})</h3>
            <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: '#64748b' }}>
              Real-time telemetry and metadata status across all registered cold-chain accounts.
            </p>
          </div>
        </div>

        {loadingData && <p style={{ color: '#64748b', fontSize: '14px' }}>Loading shipments ledger...</p>}

        {!loadingData && shipments.length === 0 && (
          <p style={{ color: '#94a3b8', textAlign: 'center', padding: '32px' }}>No shipments registered in the system yet.</p>
        )}

        <div style={{ overflowX: 'auto' }}>
          <table style={styles.table}>
            <thead>
              <tr style={styles.thRow}>
                <th style={styles.th}>Shipment ID</th>
                <th style={styles.th}>Sender Email</th>
                <th style={styles.th}>Route & Cargo</th>
                <th style={styles.th}>Metadata Status</th>
                <th style={styles.th}>On-Chain Ingestion</th>
                <th style={styles.th}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {shipments.map((s) => (
                <tr key={s.id} style={styles.tr}>
                  <td style={styles.td}>
                    <code style={styles.code}>{s.shipment_id}</code>
                    <div style={{ fontSize: '11px', color: '#94a3b8', marginTop: '2px' }}>
                      {new Date(s.created_at).toLocaleDateString()}
                    </div>
                  </td>
                  <td style={styles.td}>
                    <span style={{ fontWeight: 500, color: '#334155' }}>{s.sender_email}</span>
                  </td>
                  <td style={styles.td}>
                    <div style={{ fontWeight: 600, color: '#0f172a' }}>{s.origin} ➔ {s.destination}</div>
                    <div style={{ fontSize: '12px', color: '#64748b' }}>{s.cargo_description}</div>
                  </td>
                  <td style={styles.td}>
                    <select
                      value={s.status}
                      onChange={(e) => handleStatusChange(s.shipment_id, e.target.value)}
                      style={styles.select}
                    >
                      <option value="created">Created</option>
                      <option value="in_transit">In Transit</option>
                      <option value="delivered">Delivered</option>
                    </select>
                  </td>
                  <td style={styles.td}>
                    {s.onChainCommitted ? (
                      <div>
                        <span style={s.tamperDetected ? styles.tamperedBadge : styles.committedBadge}>
                          {s.tamperDetected ? '⚠️ TAMPER / BREACH FLAGGED' : '✔ COMMITTED ON-CHAIN'}
                        </span>
                        <div style={{ fontSize: '11px', fontFamily: 'monospace', color: '#64748b', marginTop: '4px' }}>
                          Root: {s.merkleRoot ? `${s.merkleRoot.slice(0, 14)}...` : 'N/A'} ({s.batchCount || 1} batch(es), {s.recordCount} recs)
                        </div>
                        {s.device_silicon_id && (
                          <div style={{ fontSize: '11px', color: '#0284c7', marginTop: '2px' }}>
                            🔒 Device: <code>{s.device_silicon_id.slice(0, 14)}...</code>
                          </div>
                        )}
                        {s.device_substitution_detected && (
                          <div style={{ fontSize: '11px', color: '#dc2626', fontWeight: 'bold', marginTop: '2px' }}>
                            🚨 Device Substituted!
                          </div>
                        )}
                        {s.rollback_detected && (
                          <div style={{ fontSize: '11px', color: '#dc2626', fontWeight: 'bold', marginTop: '2px' }}>
                            🚨 Anti-Rollback Violation!
                          </div>
                        )}
                      </div>
                    ) : (
                      <span style={styles.pendingBadge}>⏳ AWAITING SENSOR TELEMETRY</span>
                    )}
                  </td>
                  <td style={styles.td}>
                    <button
                      onClick={() => onInspectShipment && onInspectShipment(s.shipment_id)}
                      style={styles.inspectBtn}
                    >
                      🔍 Inspect
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

const styles = {
  authContainer: {
    maxWidth: '440px',
    margin: '40px auto',
    padding: '0 16px',
  },
  authCard: {
    background: '#ffffff',
    borderRadius: '16px',
    padding: '32px',
    boxShadow: '0 10px 25px -5px rgba(0,0,0,0.08), 0 8px 10px -6px rgba(0,0,0,0.04)',
    border: '1px solid #e2e8f0',
  },
  dashboardContainer: {
    maxWidth: '1280px',
    margin: '0 auto',
    padding: '24px 16px',
  },
  profileBar: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    background: '#ffffff',
    padding: '16px 24px',
    borderRadius: '12px',
    border: '1px solid #e2e8f0',
    marginBottom: '20px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
  },
  adminBadge: {
    background: '#fef2f2',
    color: '#dc2626',
    padding: '2px 6px',
    borderRadius: '4px',
    fontSize: '11px',
    fontWeight: 'bold',
    marginLeft: '6px',
  },
  immutabilityBanner: {
    background: '#f0f9ff',
    border: '1px solid #bae6fd',
    borderRadius: '12px',
    padding: '16px 20px',
    marginBottom: '24px',
  },
  statsGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(4, 1fr)',
    gap: '16px',
    marginBottom: '24px',
  },
  statCard: {
    background: '#ffffff',
    borderRadius: '12px',
    padding: '20px',
    border: '1px solid #e2e8f0',
    display: 'flex',
    flexDirection: 'column',
  },
  statLabel: {
    fontSize: '12px',
    fontWeight: 600,
    color: '#64748b',
    textTransform: 'uppercase',
    letterSpacing: '0.5px',
  },
  statValue: {
    fontSize: '28px',
    fontWeight: 'bold',
    color: '#0f172a',
    margin: '6px 0',
  },
  statSub: {
    fontSize: '12px',
    color: '#94a3b8',
  },
  tableCard: {
    background: '#ffffff',
    borderRadius: '12px',
    padding: '24px',
    border: '1px solid #e2e8f0',
    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
  },
  table: {
    width: '100%',
    borderCollapse: 'collapse',
    textAlign: 'left',
  },
  thRow: {
    borderBottom: '2px solid #e2e8f0',
  },
  th: {
    padding: '12px 14px',
    fontSize: '12px',
    fontWeight: 600,
    color: '#475569',
    textTransform: 'uppercase',
    letterSpacing: '0.5px',
  },
  tr: {
    borderBottom: '1px solid #f1f5f9',
  },
  td: {
    padding: '14px',
    fontSize: '13px',
    verticalAlign: 'middle',
  },
  code: {
    fontFamily: 'monospace',
    background: '#f1f5f9',
    padding: '2px 6px',
    borderRadius: '4px',
    fontWeight: 'bold',
    color: '#0284c7',
  },
  select: {
    padding: '6px 10px',
    borderRadius: '6px',
    border: '1px solid #cbd5e1',
    fontSize: '12px',
    background: '#fff',
    outline: 'none',
  },
  committedBadge: {
    background: '#dcfce7',
    color: '#15803d',
    padding: '3px 8px',
    borderRadius: '12px',
    fontSize: '11px',
    fontWeight: 'bold',
  },
  tamperedBadge: {
    background: '#fee2e2',
    color: '#b91c1c',
    padding: '3px 8px',
    borderRadius: '12px',
    fontSize: '11px',
    fontWeight: 'bold',
  },
  pendingBadge: {
    background: '#fef3c7',
    color: '#b45309',
    padding: '3px 8px',
    borderRadius: '12px',
    fontSize: '11px',
    fontWeight: 'bold',
  },
  inspectBtn: {
    padding: '6px 12px',
    fontSize: '12px',
    background: '#0284c7',
    color: '#ffffff',
    border: 'none',
    borderRadius: '6px',
    cursor: 'pointer',
    fontWeight: 600,
  },
  label: {
    display: 'block',
    fontSize: '12px',
    fontWeight: 600,
    color: '#475569',
    marginBottom: '4px',
  },
  input: {
    width: '100%',
    padding: '10px 12px',
    borderRadius: '8px',
    border: '1px solid #cbd5e1',
    fontSize: '14px',
    boxSizing: 'border-box',
    outline: 'none',
  },
  primaryBtn: {
    background: '#0f172a',
    color: '#ffffff',
    border: 'none',
    padding: '10px 16px',
    borderRadius: '8px',
    fontWeight: 600,
    fontSize: '14px',
    cursor: 'pointer',
    marginTop: '6px',
  },
  secondaryBtn: {
    background: '#f1f5f9',
    color: '#334155',
    border: '1px solid #cbd5e1',
    padding: '6px 12px',
    borderRadius: '6px',
    fontWeight: 600,
    fontSize: '12px',
    cursor: 'pointer',
  },
  errorBanner: {
    background: '#fef2f2',
    color: '#b91c1c',
    padding: '10px 14px',
    borderRadius: '6px',
    fontSize: '13px',
    marginBottom: '14px',
    border: '1px solid #fecaca',
  },
};
