/**
 * ==========================================================================
 * SMART HOME // CYBER COMMAND & CONTROL (C2) - JAVASCRIPT ENGINE
 * CTF Dashboard State Manager, Socket Packet Tracer, and Audio Engine
 * ==========================================================================
 */

// Global State
const C2_STATE = {
    audioEnabled: true,
    server: {
        status: 'DISCONNECTED',
        host: '127.0.0.1',
        port: 5000,
        uptimeSeconds: 0,
        totalCommands: 0,
        totalAcked: 0,
        totalErrors: 0
    },
    devices: {
        LIGHT: { connected: false, rawStatus: 'OFFLINE', isOn: false, lastCommand: 'NONE', lastResponse: 'NONE', lastUpdated: '--:--:--' },
        FAN: { connected: false, rawStatus: 'OFFLINE', isOn: false, speed: 0, lastCommand: 'NONE', lastResponse: 'NONE', lastUpdated: '--:--:--' },
        AC: { connected: false, rawStatus: 'OFFLINE', isOn: false, temp: 24, lastCommand: 'NONE', lastResponse: 'NONE', lastUpdated: '--:--:--' },
        SMARTLOCK: { connected: false, rawStatus: 'OFFLINE', isLocked: true, lastCommand: 'NONE', lastResponse: 'NONE', lastUpdated: '--:--:--' }
    },
    commandHistory: [],
    historyIdx: -1,
    currentRoute: 'dashboard'
};

// ==========================================================================
// 1. Synthesized Web Audio Engine (Zero External Audio Files)
// ==========================================================================
class CyberAudioEngine {
    constructor() {
        this.ctx = null;
    }

    init() {
        if (!this.ctx && (window.AudioContext || window.webkitAudioContext)) {
            const AudioCtx = window.AudioContext || window.webkitAudioContext;
            this.ctx = new AudioCtx();
        }
    }

    playTone(freq, type = 'sine', duration = 0.08, gainVal = 0.05) {
        if (!C2_STATE.audioEnabled) return;
        try {
            this.init();
            if (!this.ctx) return;
            const osc = this.ctx.createOscillator();
            const gain = this.ctx.createGain();
            osc.type = type;
            osc.frequency.setValueAtTime(freq, this.ctx.currentTime);
            gain.gain.setValueAtTime(gainVal, this.ctx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.0001, this.ctx.currentTime + duration);
            osc.connect(gain);
            gain.connect(this.ctx.destination);
            osc.start();
            osc.stop(this.ctx.currentTime + duration);
        } catch (e) {}
    }

    click() { this.playTone(850, 'triangle', 0.04, 0.03); }
    sendPacket() {
        this.playTone(900, 'sine', 0.05, 0.04);
        setTimeout(() => this.playTone(1350, 'sine', 0.08, 0.04), 50);
    }
    ackSuccess() {
        this.playTone(1100, 'triangle', 0.06, 0.05);
        setTimeout(() => this.playTone(1650, 'sine', 0.12, 0.05), 60);
    }
    alertError() {
        this.playTone(320, 'sawtooth', 0.15, 0.08);
        setTimeout(() => this.playTone(280, 'sawtooth', 0.2, 0.08), 120);
    }
}
const audio = new CyberAudioEngine();

// ==========================================================================
// 2. SPA Router (Supports Hash and HTML5 Path Navigation)
// ==========================================================================
function initRouter() {
    function handleRoute() {
        let path = window.location.hash.replace('#', '') || window.location.pathname;
        if (path === '/' || path === '') path = 'dashboard';
        if (path.startsWith('/')) path = path.substring(1);
        
        // Normalize routes
        if (path.includes('devices/light')) path = 'device-light';
        else if (path.includes('devices/fan')) path = 'device-fan';
        else if (path.includes('devices/ac')) path = 'device-ac';
        else if (path.includes('devices/smartlock')) path = 'device-lock';
        else if (path.includes('architecture')) path = 'architecture';
        else path = 'dashboard';

        navigateTo(path, false);
    }

    window.addEventListener('popstate', handleRoute);
    window.addEventListener('hashchange', handleRoute);
    handleRoute();
}

function navigateTo(routeId, updateHistory = true) {
    audio.click();
    C2_STATE.currentRoute = routeId;

    // Hide all view containers
    document.querySelectorAll('.view-pane').forEach(el => el.classList.remove('active'));

    // Deactivate all nav tabs
    document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));

    // Show selected view
    const targetView = document.getElementById('view-' + routeId);
    if (targetView) {
        targetView.classList.add('active');
    } else {
        document.getElementById('view-dashboard').classList.add('active');
    }

    // Activate corresponding nav item if exists
    const navItem = document.getElementById('nav-' + routeId);
    if (navItem) navItem.classList.add('active');

    if (updateHistory) {
        let hash = routeId === 'dashboard' ? '' : '#' + routeId.replace('device-', 'devices/');
        window.history.pushState(null, null, hash || '/');
    }

    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// ==========================================================================
// 3. Command Execution Engine & Terminal Controller
// ==========================================================================
async function submitCommand(rawCmd) {
    if (!rawCmd || !rawCmd.trim()) return;
    const cmd = rawCmd.trim();

    // Sound feedback
    audio.sendPacket();

    // Terminal Logging: Operator line
    appendTerminalLine(getTimestamp(), 'OPERATOR', '>', cmd, 'term-op');
    
    // Save to history
    C2_STATE.commandHistory.push(cmd);
    C2_STATE.historyIdx = C2_STATE.commandHistory.length;

    // Show Pending line in terminal
    const pendingId = 'pending-' + Date.now();
    appendTerminalPending(pendingId, 'C2-GATEWAY > DISPATCHING PACKET VIA TCP/5000...');

    // Trigger visual packet animation if on architecture view
    triggerPacketAnimation(cmd);

    try {
        const startTime = performance.now();
        const res = await fetch('/api/command', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ command: cmd })
        });

        const data = await res.json();
        const latency = Math.round(performance.now() - startTime);

        // Remove pending indicator
        removeTerminalPending(pendingId);

        if (data.success) {
            audio.ackSuccess();

            // Detailed trace rendering
            if (data.trace && Array.isArray(data.trace)) {
                data.trace.forEach(step => {
                    if (step.includes('>')) {
                        const parts = step.split('>');
                        appendTerminalLine(getTimestamp(), parts[0].trim(), '>', parts[1].trim(), 'term-ctrl');
                    } else {
                        appendTerminalLine(getTimestamp(), 'CONTROLLER', '>', step, 'term-ctrl');
                    }
                });
            }

            appendTerminalLine(getTimestamp(), 'SYSTEM', '>', `ACKNOWLEDGED [${data.response}] (${latency}ms)`, 'term-res');
        } else {
            audio.alertError();
            appendTerminalLine(getTimestamp(), 'CONTROLLER', 'ERROR', data.error || 'COMMAND REJECTED', 'term-err');
        }

        // Immediately update telemetry
        fetchTelemetry();

    } catch (err) {
        removeTerminalPending(pendingId);
        audio.alertError();
        appendTerminalLine(getTimestamp(), 'SYSTEM', 'CRITICAL', 'FAILED TO REACH CONTROLLER: ' + err.message, 'term-err');
    }
}

function appendTerminalLine(time, prefix, arrow, message, cssClass) {
    const termBody = document.getElementById('terminal-logs');
    if (!termBody) return;

    const line = document.createElement('div');
    line.className = `terminal-line ${cssClass}`;
    line.innerHTML = `<span class="term-time">[${time}]</span> <span class="term-prefix">${prefix}</span> ${arrow} <span>${escapeHtml(message)}</span>`;
    
    termBody.appendChild(line);
    termBody.scrollTop = termBody.scrollHeight;
}

function appendTerminalPending(id, text) {
    const termBody = document.getElementById('terminal-logs');
    if (!termBody) return;

    const line = document.createElement('div');
    line.id = id;
    line.className = 'terminal-line term-sys';
    line.innerHTML = `<span class="term-time">[${getTimestamp()}]</span> <span class="term-prefix">GATEWAY</span> > <span class="pulse-text">${text}</span>`;
    termBody.appendChild(line);
    termBody.scrollTop = termBody.scrollHeight;
}

function removeTerminalPending(id) {
    const el = document.getElementById(id);
    if (el) el.remove();
}

// ==========================================================================
// 4. Real-time Telemetry & State Synchronization
// ==========================================================================
async function fetchTelemetry() {
    try {
        const res = await fetch('/api/status');
        if (!res.ok) throw new Error('Status HTTP ' + res.status);
        const data = await res.json();

        // Update server stats
        if (data.server) {
            C2_STATE.server = data.server;
            updateServerUI(data.server);
        }

        // Update devices state
        if (data.devices) {
            C2_STATE.devices = data.devices;
            updateDevicesUI(data.devices);
            updateMissionObjectives(data.devices);
        }

        // Update event list
        if (data.events && Array.isArray(data.events)) {
            renderEventList(data.events);
        }

    } catch (e) {
        setServerOfflineUI();
    }
}

function updateServerUI(server) {
    // Top bar status
    const statusText = document.getElementById('header-server-status');
    const pulseDot = document.getElementById('header-pulse-dot');
    if (statusText && pulseDot) {
        statusText.innerText = `CONTROLLER: ${server.port} [ACTIVE]`;
        pulseDot.className = 'pulse-dot';
    }

    // Telemetry bar counters
    const uptimeEl = document.getElementById('metric-uptime');
    if (uptimeEl) uptimeEl.innerText = formatUptime(server.uptimeSeconds);

    const cmdsEl = document.getElementById('metric-cmds');
    if (cmdsEl) cmdsEl.innerText = server.totalCommands || 0;

    const acksEl = document.getElementById('metric-acks');
    if (acksEl) acksEl.innerText = server.totalAcked || 0;
}

function setServerOfflineUI() {
    const statusText = document.getElementById('header-server-status');
    const pulseDot = document.getElementById('header-pulse-dot');
    if (statusText && pulseDot) {
        statusText.innerText = `CONTROLLER [OFFLINE]`;
        pulseDot.className = 'pulse-dot offline';
    }
}

function updateDevicesUI(devices) {
    let onlineCount = 0;

    // --- 1. LIGHT-01 ---
    const light = devices.LIGHT || {};
    if (light.connected) onlineCount++;
    updateDeviceCard('light', light, {
        badgeText: light.connected ? 'ONLINE' : 'OFFLINE',
        stateText: light.isOn ? 'ENERGIZED' : 'DORMANT',
        graphicActive: light.isOn
    });
    // Detail View update
    syncDetailLight(light);

    // --- 2. FAN-01 ---
    const fan = devices.FAN || {};
    if (fan.connected) onlineCount++;
    updateDeviceCard('fan', fan, {
        badgeText: fan.connected ? 'ONLINE' : 'OFFLINE',
        stateText: fan.isOn ? `RUNNING (SPD ${fan.speed})` : 'HALTED',
        graphicActive: fan.isOn
    });
    syncDetailFan(fan);

    // --- 3. AC-01 ---
    const ac = devices.AC || {};
    if (ac.connected) onlineCount++;
    updateDeviceCard('ac', ac, {
        badgeText: ac.connected ? 'ONLINE' : 'OFFLINE',
        stateText: ac.isOn ? `ACTIVE (${ac.temp}°C)` : `OFF (${ac.temp}°C)`,
        graphicActive: ac.isOn
    });
    syncDetailAC(ac);

    // --- 4. LOCK-01 ---
    const lock = devices.SMARTLOCK || {};
    if (lock.connected) onlineCount++;
    updateDeviceCard('lock', lock, {
        badgeText: lock.connected ? 'ONLINE' : 'OFFLINE',
        stateText: lock.isLocked ? 'SECURE [LOCKED]' : 'BREACHED [UNLOCKED]',
        graphicActive: lock.isLocked
    });
    syncDetailLock(lock);

    // Online counter
    const onlineEl = document.getElementById('metric-online-devices');
    if (onlineEl) onlineEl.innerText = `${onlineCount} / 4`;
}

function updateDeviceCard(key, data, meta) {
    const badge = document.getElementById(`card-${key}-badge`);
    if (badge) {
        badge.innerText = meta.badgeText;
        badge.className = `device-badge ${data.connected ? 'online' : 'offline'}`;
    }

    const stateEl = document.getElementById(`card-${key}-state`);
    if (stateEl) stateEl.innerText = meta.stateText;

    const cmdEl = document.getElementById(`card-${key}-lastcmd`);
    if (cmdEl) cmdEl.innerText = data.lastCommand || 'NONE';

    const respEl = document.getElementById(`card-${key}-lastresp`);
    if (respEl) respEl.innerText = data.lastResponse || 'NONE';

    // Graphic updates
    if (key === 'light') {
        const icon = document.getElementById('graphic-bulb');
        if (icon) icon.className = `bulb-icon ${data.isOn ? 'active' : ''}`;
    } else if (key === 'fan') {
        const turbine = document.getElementById('graphic-fan');
        if (turbine) {
            turbine.className = `fan-turbine ${data.isOn && data.speed > 0 ? 'spinning-' + data.speed : ''}`;
        }
    } else if (key === 'ac') {
        const gauge = document.getElementById('graphic-ac');
        if (gauge) gauge.className = `ac-gauge ${data.isOn ? 'active' : ''}`;
    } else if (key === 'lock') {
        const shield = document.getElementById('graphic-lock');
        if (shield) shield.className = `lock-shield ${data.isLocked ? 'locked' : 'unlocked'}`;
    }
}

// Dedicated Page Sync Helpers
function syncDetailLight(data) {
    const st = document.getElementById('detail-light-status');
    if (st) st.innerText = data.connected ? (data.isOn ? 'ENERGIZED [ON]' : 'DORMANT [OFF]') : 'OFFLINE';
    const resp = document.getElementById('detail-light-resp');
    if (resp) resp.innerText = data.lastResponse;
}

function syncDetailFan(data) {
    const st = document.getElementById('detail-fan-status');
    if (st) st.innerText = data.connected ? (data.isOn ? `ACTIVE (SPEED ${data.speed})` : 'HALTED [OFF]') : 'OFFLINE';
    const resp = document.getElementById('detail-fan-resp');
    if (resp) resp.innerText = data.lastResponse;

    // Highlight active speed button
    document.querySelectorAll('.speed-btn').forEach(btn => {
        const spd = parseInt(btn.dataset.speed);
        if (spd === data.speed && data.isOn) {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });
}

function syncDetailAC(data) {
    const st = document.getElementById('detail-ac-status');
    if (st) st.innerText = data.connected ? (data.isOn ? `ACTIVE (${data.temp}°C)` : `STANDBY (${data.temp}°C)`) : 'OFFLINE';
    const tempDisp = document.getElementById('detail-ac-temp-val');
    if (tempDisp) tempDisp.innerText = `${data.temp}°C`;
    const resp = document.getElementById('detail-ac-resp');
    if (resp) resp.innerText = data.lastResponse;
    const slider = document.getElementById('detail-ac-slider');
    if (slider && !slider.matches(':active')) {
        slider.value = data.temp;
    }
}

function syncDetailLock(data) {
    const st = document.getElementById('detail-lock-status');
    if (st) st.innerText = data.connected ? (data.isLocked ? 'SECURE [LOCKED]' : 'UNRESTRICTED [UNLOCKED]') : 'OFFLINE';
    const resp = document.getElementById('detail-lock-resp');
    if (resp) resp.innerText = data.lastResponse;
}

// CTF Objectives Tracker
function updateMissionObjectives(devs) {
    setMissionComplete('obj-uplink', C2_STATE.server.status === 'ONLINE' || Object.values(devs).some(d => d.connected));
    setMissionComplete('obj-light', devs.LIGHT && devs.LIGHT.isOn);
    setMissionComplete('obj-fan', devs.FAN && devs.FAN.isOn && devs.FAN.speed >= 4);
    setMissionComplete('obj-ac', devs.AC && devs.AC.isOn && devs.AC.temp <= 21);
    setMissionComplete('obj-lock', devs.SMARTLOCK && devs.SMARTLOCK.isLocked);
}

function setMissionComplete(id, completed) {
    const el = document.getElementById(id);
    if (!el) return;
    if (completed) {
        el.classList.add('completed');
        el.querySelector('.mission-check').innerHTML = '&#10003;';
    } else {
        el.classList.remove('completed');
        el.querySelector('.mission-check').innerHTML = '&times;';
    }
}

// Event Stream Renderer
function renderEventList(events) {
    const listEl = document.getElementById('events-stream');
    if (!listEl) return;

    listEl.innerHTML = '';
    events.slice(0, 40).forEach(ev => {
        const item = document.createElement('div');
        let typeClass = '';
        if (ev.type.includes('ERR') || !ev.success) typeClass = 'err';
        else if (ev.type.includes('REG')) typeClass = 'reg';
        else if (ev.type.includes('DISCONNECT')) typeClass = 'warn';

        item.className = `event-entry ${typeClass}`;
        item.innerHTML = `
            <div class="event-meta">
                <span>[${ev.timestamp}] ${escapeHtml(ev.source)}</span>
                <span>${escapeHtml(ev.type)}</span>
            </div>
            <div class="event-body">${escapeHtml(ev.message)}</div>
        `;
        listEl.appendChild(item);
    });
}

// ==========================================================================
// 5. Interactive Architecture Packet Simulator
// ==========================================================================
function triggerPacketAnimation(cmd) {
    const packet = document.getElementById('arch-anim-packet');
    if (!packet) return;

    packet.style.animation = 'none';
    void packet.offsetWidth; // Trigger reflow
    packet.style.animation = 'packetForward 0.8s ease-in-out forwards';

    // Highlight node steps
    const nodes = [
        document.getElementById('arch-node-dashboard'),
        document.getElementById('arch-node-gateway'),
        document.getElementById('arch-node-server'),
        document.getElementById('arch-node-device')
    ];

    nodes.forEach((n, idx) => {
        if (!n) return;
        setTimeout(() => {
            n.classList.add('active-packet');
            setTimeout(() => n.classList.remove('active-packet'), 500);
        }, idx * 200);
    });

    // Return packet
    setTimeout(() => {
        packet.style.animation = 'packetReturn 0.8s ease-in-out forwards';
    }, 900);
}

// ==========================================================================
// 6. Utility Functions
// ==========================================================================
function getTimestamp() {
    const d = new Date();
    return d.toTimeString().split(' ')[0];
}

function formatUptime(seconds) {
    const h = Math.floor(seconds / 3600).toString().padStart(2, '0');
    const m = Math.floor((seconds % 3600) / 60).toString().padStart(2, '0');
    const s = Math.floor(seconds % 60).toString().padStart(2, '0');
    return `${h}:${m}:${s}`;
}

function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;')
              .replace(/</g, '&lt;')
              .replace(/>/g, '&gt;')
              .replace(/"/g, '&quot;');
}

// ==========================================================================
// 7. Initial Setup & Event Listeners
// ==========================================================================
document.addEventListener('DOMContentLoaded', () => {
    initRouter();

    // Terminal Input
    const termInput = document.getElementById('terminal-input');
    const sendBtn = document.getElementById('btn-terminal-send');

    if (termInput) {
        termInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                submitCommand(termInput.value);
                termInput.value = '';
            } else if (e.key === 'ArrowUp') {
                if (C2_STATE.historyIdx > 0) {
                    C2_STATE.historyIdx--;
                    termInput.value = C2_STATE.commandHistory[C2_STATE.historyIdx] || '';
                }
            } else if (e.key === 'ArrowDown') {
                if (C2_STATE.historyIdx < C2_STATE.commandHistory.length - 1) {
                    C2_STATE.historyIdx++;
                    termInput.value = C2_STATE.commandHistory[C2_STATE.historyIdx] || '';
                } else {
                    C2_STATE.historyIdx = C2_STATE.commandHistory.length;
                    termInput.value = '';
                }
            }
        });
    }

    if (sendBtn && termInput) {
        sendBtn.addEventListener('click', () => {
            submitCommand(termInput.value);
            termInput.value = '';
        });
    }

    // Audio SFX Toggle Button
    const audioBtn = document.getElementById('btn-toggle-audio');
    if (audioBtn) {
        audioBtn.addEventListener('click', () => {
            C2_STATE.audioEnabled = !C2_STATE.audioEnabled;
            audioBtn.innerText = C2_STATE.audioEnabled ? '🔊 AUDIO ON' : '🔇 MUTED';
            if (C2_STATE.audioEnabled) audio.click();
        });
    }

    // Scanlines Toggle
    const crtBtn = document.getElementById('btn-toggle-crt');
    if (crtBtn) {
        crtBtn.addEventListener('click', () => {
            document.body.classList.toggle('no-scanlines');
            audio.click();
        });
    }

    // Clear Terminal Button
    const clearBtn = document.getElementById('btn-clear-term');
    if (clearBtn) {
        clearBtn.addEventListener('click', () => {
            const body = document.getElementById('terminal-logs');
            if (body) body.innerHTML = '';
            audio.click();
        });
    }

    // AC Slider Live Input
    const acSlider = document.getElementById('detail-ac-slider');
    const acSliderVal = document.getElementById('detail-ac-slider-val');
    if (acSlider && acSliderVal) {
        acSlider.addEventListener('input', () => {
            acSliderVal.innerText = `${acSlider.value}°C`;
        });
        acSlider.addEventListener('change', () => {
            submitCommand(`AC SET ${acSlider.value}`);
        });
    }

    // Polling Loop: Query every 2000ms
    fetchTelemetry();
    setInterval(fetchTelemetry, 2000);
});
