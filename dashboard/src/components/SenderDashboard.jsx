import React, { useState, useEffect } from 'react';
import QRCodeDisplay from './QRCodeDisplay';

export default function SenderDashboard({ backendUrl, onInspectShipment }) {
  const [token, setToken] = useState(null);
  const [currentUser, setCurrentUser] = useState(null);

  // Auth form states
  const [authMode, setAuthMode] = useState('login'); // 'login' | 'signup'
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [authError, setAuthError] = useState(null);
  const [authLoading, setAuthLoading] = useState(false);

  // Shipment creation form states
  const [origin, setOrigin] = useState('');
  const [destination, setDestination] = useState('');
  const [receiverName, setReceiverName] = useState('');
  const [receiverContact, setReceiverContact] = useState('');
  const [vehicleId, setVehicleId] = useState('');
  const [cargoDescription, setCargoDescription] = useState('');
  const [createdShipment, setCreatedShipment] = useState(null);
  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState(null);

  // Shipments list states
  const [myShipments, setMyShipments] = useState([]);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState(null);

  // ── Auth Handlers ───────────────────────────────────────────────────────────
  async function handleAuthSubmit(e) {
    e.preventDefault();
    setAuthError(null);
    setAuthLoading(true);

    const endpoint = authMode === 'signup' ? `${backendUrl}/auth/signup` : `${backendUrl}/auth/login`;

    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      });

      const data = await res.json();
      if (!res.ok) {
        throw new Error(data.error || `HTTP ${res.status}`);
      }

      if (authMode === 'signup') {
        // Auto-login after signup
        const loginRes = await fetch(`${backendUrl}/auth/login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password }),
        });
        const loginData = await loginRes.json();
        if (!loginRes.ok) throw new Error(loginData.error || 'Failed to login after signup');
        setToken(loginData.token);
        setCurrentUser(loginData.user);
      } else {
        if (data.user?.role !== 'sender') {
          throw new Error('Access denied: Sender Portal requires a "sender" account role.');
        }
        setToken(data.token);
        setCurrentUser(data.user);
      }
    } catch (err) {
      setAuthError(err.message);
    } finally {
      setAuthLoading(false);
    }
  }

  function handleLogout() {
    setToken(null);
    setCurrentUser(null);
    setMyShipments([]);
    setCreatedShipment(null);
  }

  // ── Fetch Sender's Shipments ────────────────────────────────────────────────
  async function loadMyShipments() {
    if (!token) return;
    setListLoading(true);
    setListError(null);

    try {
      const res = await fetch(`${backendUrl}/shipments/mine`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
      setMyShipments(data.shipments || []);
    } catch (err) {
      setListError(err.message);
    } finally {
      setListLoading(false);
    }
  }

  useEffect(() => {
    if (token) {
      loadMyShipments();
    }
  }, [token]);

  // ── Create Shipment Handler ─────────────────────────────────────────────────
  async function handleCreateShipment(e) {
    e.preventDefault();
    setCreateError(null);
    setCreateLoading(true);

    try {
      const res = await fetch(`${backendUrl}/shipments`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          origin,
          destination,
          receiver_name: receiverName,
          receiver_contact: receiverContact,
          vehicle_id: vehicleId,
          cargo_description: cargoDescription,
        }),
      });

      const data = await res.json();
      if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);

      setCreatedShipment(data.shipment);
      // Reset form
      setOrigin('');
      setDestination('');
      setReceiverName('');
      setReceiverContact('');
      setVehicleId('');
      setCargoDescription('');

      // Reload list
      loadMyShipments();
    } catch (err) {
      setCreateError(err.message);
    } finally {
      setCreateLoading(false);
    }
  }

  // ── Update Shipment Status ──────────────────────────────────────────────────
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
      loadMyShipments();
    } catch (err) {
      alert(`Failed to update status: ${err.message}`);
    }
  }

  // ── Render: Not Logged In ───────────────────────────────────────────────────
  if (!token) {
    return (
      <div style={styles.authContainer}>
        <div style={styles.authCard}>
          <div style={{ textAlign: 'center', marginBottom: '24px' }}>
            <span style={{ fontSize: '40px' }}>📦</span>
            <h2 style={{ color: '#0f172a', margin: '8px 0 4px 0', fontSize: '24px' }}>Sender Portal</h2>
            <p style={{ color: '#64748b', fontSize: '14px', margin: 0 }}>
              {authMode === 'login' ? 'Sign in to manage and dispatch cold-chain shipments' : 'Register a new sender account'}
            </p>
          </div>

          <div style={styles.tabToggle}>
            <button
              onClick={() => { setAuthMode('login'); setAuthError(null); }}
              style={{
                ...styles.tabBtn,
                ...(authMode === 'login' ? styles.tabBtnActive : {}),
              }}
            >
              Sign In
            </button>
            <button
              onClick={() => { setAuthMode('signup'); setAuthError(null); }}
              style={{
                ...styles.tabBtn,
                ...(authMode === 'signup' ? styles.tabBtnActive : {}),
              }}
            >
              Create Account
            </button>
          </div>

          {authError && <div style={styles.errorBanner}>⚠️ {authError}</div>}

          <form onSubmit={handleAuthSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            <div>
              <label style={styles.label}>Sender Email Address</label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="sender@coldchain.com"
                style={styles.input}
              />
            </div>

            <div>
              <label style={styles.label}>Password (min 8 chars)</label>
              <input
                type="password"
                required
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                style={styles.input}
              />
            </div>

            <button type="submit" disabled={authLoading} style={styles.primaryBtn}>
              {authLoading ? 'Processing...' : authMode === 'login' ? '🔑 Sign In as Sender' : '📝 Register Sender Account'}
            </button>
          </form>

          {authMode === 'signup' && (
            <p style={{ fontSize: '12px', color: '#64748b', marginTop: '16px', textAlign: 'center' }}>
              🔒 Security Policy: All public signups are assigned the <strong style={{ color: '#0f172a' }}>sender</strong> role.
            </p>
          )}
        </div>
      </div>
    );
  }

  // ── Render: Authenticated Sender Dashboard ───────────────────────────────────
  return (
    <div style={styles.dashboardContainer}>
      {/* Top Profile Bar */}
      <div style={styles.profileBar}>
        <div>
          <h2 style={{ margin: 0, fontSize: '20px', color: '#0f172a' }}>📦 Sender Dashboard</h2>
          <span style={{ fontSize: '13px', color: '#64748b' }}>
            Logged in as: <strong>{currentUser?.email}</strong> <span style={styles.roleBadge}>sender</span>
          </span>
        </div>
        <button onClick={handleLogout} style={styles.secondaryBtn}>
          🚪 Log Out
        </button>
      </div>

      <div style={styles.gridContainer}>
        {/* Left Column: Create Shipment Form */}
        <div style={styles.card}>
          <h3 style={styles.cardTitle}>➕ Register New Cold-Chain Shipment</h3>
          <p style={{ fontSize: '13px', color: '#64748b', marginTop: '-8px', marginBottom: '16px' }}>
            Backend automatically assigns an immutable, unique Shipment ID and generates a crate QR code.
          </p>

          {createError && <div style={styles.errorBanner}>⚠️ {createError}</div>}

          <form onSubmit={handleCreateShipment} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div>
                <label style={styles.label}>Origin *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Hyderabad Pharma Hub"
                  value={origin}
                  onChange={(e) => setOrigin(e.target.value)}
                  style={styles.input}
                />
              </div>
              <div>
                <label style={styles.label}>Destination *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Mumbai Vaccine Center"
                  value={destination}
                  onChange={(e) => setDestination(e.target.value)}
                  style={styles.input}
                />
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div>
                <label style={styles.label}>Receiver Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Dr. Ramesh Patel"
                  value={receiverName}
                  onChange={(e) => setReceiverName(e.target.value)}
                  style={styles.input}
                />
              </div>
              <div>
                <label style={styles.label}>Receiver Contact</label>
                <input
                  type="text"
                  placeholder="e.g. +91 98765 43210"
                  value={receiverContact}
                  onChange={(e) => setReceiverContact(e.target.value)}
                  style={styles.input}
                />
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <div>
                <label style={styles.label}>Vehicle / Container ID</label>
                <input
                  type="text"
                  placeholder="e.g. REEFER-TRUCK-88A"
                  value={vehicleId}
                  onChange={(e) => setVehicleId(e.target.value)}
                  style={styles.input}
                />
              </div>
              <div>
                <label style={styles.label}>Cargo Description *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. 5,000 Vials mRNA Vaccines (2-8°C)"
                  value={cargoDescription}
                  onChange={(e) => setCargoDescription(e.target.value)}
                  style={styles.input}
                />
              </div>
            </div>

            <button type="submit" disabled={createLoading} style={styles.primaryBtn}>
              {createLoading ? 'Generating Shipment...' : '🚀 Create Shipment & Generate QR Tag'}
            </button>
          </form>

          {/* Success Banner with Generated QR Tag */}
          {createdShipment && (
            <div style={styles.successBox}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#166534', fontWeight: 'bold' }}>
                <span>✔</span> Shipment Created & Assigned!
              </div>
              <div style={{ marginTop: '8px', fontSize: '13px' }}>
                <div><strong>Assigned Shipment ID:</strong> <code style={styles.code}>{createdShipment.shipment_id}</code></div>
                <div><strong>Route:</strong> {createdShipment.origin} ➔ {createdShipment.destination}</div>
              </div>

              <div style={{ marginTop: '16px', display: 'flex', justifyContent: 'center' }}>
                <QRCodeDisplay
                  value={createdShipment.shipment_id}
                  label={`${createdShipment.origin} ➔ ${createdShipment.destination}`}
                />
              </div>
            </div>
          )}
        </div>

        {/* Right Column: My Shipments List */}
        <div style={styles.card}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
            <h3 style={{ ...styles.cardTitle, margin: 0 }}>📋 My Dispatched Shipments ({myShipments.length})</h3>
            <button onClick={loadMyShipments} style={{ ...styles.secondaryBtn, padding: '4px 10px', fontSize: '12px' }}>
              🔄 Refresh
            </button>
          </div>

          {listLoading && <p style={{ color: '#64748b', fontSize: '14px' }}>Loading shipments...</p>}
          {listError && <div style={styles.errorBanner}>⚠️ {listError}</div>}

          {!listLoading && myShipments.length === 0 && (
            <div style={{ textAlign: 'center', padding: '32px 16px', color: '#94a3b8' }}>
              <span style={{ fontSize: '32px' }}>📦</span>
              <p style={{ margin: '8px 0 0 0', fontSize: '14px' }}>No shipments created yet. Register your first shipment on the left.</p>
            </div>
          )}

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', maxHeight: '560px', overflowY: 'auto' }}>
            {myShipments.map((s) => (
              <div key={s.id} style={styles.shipmentItem}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                  <div>
                    <code style={styles.shipmentIdBadge}>{s.shipment_id}</code>
                    <div style={{ fontWeight: 600, color: '#0f172a', marginTop: '4px' }}>
                      {s.origin} ➔ {s.destination}
                    </div>
                    <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>
                      📦 {s.cargo_description} | 👤 {s.receiver_name}
                    </div>
                  </div>
                  <span style={getStatusBadgeStyle(s.status)}>
                    {s.status.toUpperCase()}
                  </span>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '12px', paddingTop: '8px', borderTop: '1px solid #f1f5f9' }}>
                  {/* Status update select */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span style={{ fontSize: '12px', color: '#64748b' }}>Status:</span>
                    <select
                      value={s.status}
                      onChange={(e) => handleStatusChange(s.shipment_id, e.target.value)}
                      style={styles.select}
                    >
                      <option value="created">Created</option>
                      <option value="in_transit">In Transit</option>
                      <option value="delivered">Delivered</option>
                    </select>
                  </div>

                  {/* Public Inspector Link */}
                  <button
                    onClick={() => onInspectShipment && onInspectShipment(s.shipment_id)}
                    style={{
                      padding: '4px 10px',
                      fontSize: '12px',
                      background: '#0ea5e9',
                      color: '#ffffff',
                      border: 'none',
                      borderRadius: '4px',
                      cursor: 'pointer',
                      fontWeight: 600,
                    }}
                  >
                    🔍 Inspect as Receiver
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function getStatusBadgeStyle(status) {
  switch (status) {
    case 'created':
      return { background: '#e0f2fe', color: '#0369a1', padding: '3px 8px', borderRadius: '12px', fontSize: '11px', fontWeight: 'bold' };
    case 'in_transit':
      return { background: '#fef3c7', color: '#b45309', padding: '3px 8px', borderRadius: '12px', fontSize: '11px', fontWeight: 'bold' };
    case 'delivered':
      return { background: '#dcfce7', color: '#15803d', padding: '3px 8px', borderRadius: '12px', fontSize: '11px', fontWeight: 'bold' };
    default:
      return { background: '#f1f5f9', color: '#475569', padding: '3px 8px', borderRadius: '12px', fontSize: '11px', fontWeight: 'bold' };
  }
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
  tabToggle: {
    display: 'flex',
    background: '#f1f5f9',
    borderRadius: '8px',
    padding: '4px',
    marginBottom: '20px',
  },
  tabBtn: {
    flex: 1,
    padding: '8px',
    border: 'none',
    background: 'transparent',
    borderRadius: '6px',
    fontSize: '13px',
    fontWeight: 600,
    color: '#64748b',
    cursor: 'pointer',
  },
  tabBtnActive: {
    background: '#ffffff',
    color: '#0f172a',
    boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
  },
  dashboardContainer: {
    maxWidth: '1200px',
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
    marginBottom: '24px',
    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
  },
  roleBadge: {
    background: '#e0f2fe',
    color: '#0284c7',
    padding: '2px 6px',
    borderRadius: '4px',
    fontSize: '11px',
    fontWeight: 'bold',
    marginLeft: '6px',
  },
  gridContainer: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: '24px',
  },
  card: {
    background: '#ffffff',
    borderRadius: '12px',
    padding: '24px',
    border: '1px solid #e2e8f0',
    boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
  },
  cardTitle: {
    margin: '0 0 12px 0',
    fontSize: '17px',
    color: '#0f172a',
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
    padding: '8px 12px',
    borderRadius: '6px',
    border: '1px solid #cbd5e1',
    fontSize: '13px',
    boxSizing: 'border-box',
    outline: 'none',
  },
  select: {
    padding: '4px 8px',
    borderRadius: '4px',
    border: '1px solid #cbd5e1',
    fontSize: '12px',
    background: '#fff',
    outline: 'none',
  },
  primaryBtn: {
    background: '#0284c7',
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
    padding: '8px 14px',
    borderRadius: '6px',
    fontWeight: 600,
    fontSize: '13px',
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
  successBox: {
    marginTop: '20px',
    background: '#f0fdf4',
    border: '1px solid #bbf7d0',
    borderRadius: '8px',
    padding: '16px',
  },
  code: {
    fontFamily: 'monospace',
    background: '#e2e8f0',
    padding: '2px 6px',
    borderRadius: '4px',
    color: '#0f172a',
  },
  shipmentItem: {
    padding: '14px',
    borderRadius: '8px',
    border: '1px solid #e2e8f0',
    background: '#f8fafc',
  },
  shipmentIdBadge: {
    fontFamily: 'monospace',
    fontWeight: 'bold',
    fontSize: '12px',
    color: '#0284c7',
  },
};
