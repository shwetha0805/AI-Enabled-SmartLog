// =====================================================
// SMARTLOG FRONTEND SCRIPT
// =====================================================


// =====================================================
// API CONFIGURATION
// =====================================================

const API_BASE = "http://100.85.174.53:8080/api";

const API_URL = `${API_BASE}/logs`;
const DASHBOARD_API = `${API_BASE}/dashboard`;
const HEALTH_API = `${API_BASE}/health`;


// =====================================================
// GLOBAL STATE
// =====================================================

let allLogs = [];
let currentView = "dashboard";

let activeAlerts = [];
let acknowledgedAlerts = [];


// =====================================================
// LOAD LOGS
// =====================================================

async function loadLogs() {

    try {

        const response = await fetch(API_URL);

        if (!response.ok) {
            throw new Error("HTTP Error: " + response.status);
        }

        allLogs = await response.json();

        await updateDashboard();
        await loadAlerts();

        // Only refresh the main dashboard table automatically
        if (currentView === "dashboard") {
            await loadRecentLogs();
            applyFilters();
        }

        // Do NOT rebuild Analytics or Threat Detection
        // automatically every 5 seconds.
        //
        // This prevents selected log details from disappearing.

        if (currentView === "system") {
            await showSystemStatus();
        }

    } catch (error) {

        console.error("Error loading logs:", error);

        const table =
            document.getElementById("logTable");

        if (table) {

            table.innerHTML = `
                <tr>
                    <td colspan="6"
                        style="text-align:center;
                        color:#f87171;
                        padding:30px;">
                        Unable to load logs
                    </td>
                </tr>
            `;
        }
    }
}

// =====================================================
// DASHBOARD STATISTICS
// =====================================================

async function updateDashboard() {

    try {

        const response =
            await fetch(`${DASHBOARD_API}/stats`);

        if (!response.ok) {
            throw new Error(
                "Dashboard API error: " + response.status
            );
        }

        const stats = await response.json();

        document.getElementById("totalLogs").textContent =
            stats.totalLogs ?? 0;

        document.getElementById("anomalies").textContent =
            stats.anomalies ?? 0;

        document.getElementById("highRisk").textContent =
            stats.highRisk ?? 0;

        document.getElementById("alerts").textContent =
            stats.critical ?? 0;

    } catch (error) {

        console.error(
            "Error loading dashboard statistics:",
            error
        );
    }
}


// =====================================================
// RECENT LOGS
// =====================================================

async function loadRecentLogs() {

    try {

        const response =
            await fetch(`${DASHBOARD_API}/recent`);

        if (!response.ok) {
            throw new Error(
                "Recent logs API error: " + response.status
            );
        }

        const recentLogs = await response.json();

        displayLogs(recentLogs);

    } catch (error) {

        console.error(
            "Error loading recent logs:",
            error
        );
    }
}


// =====================================================
// SECURITY ALERTS
// =====================================================

async function loadAlerts() {

    try {

        const response =
            await fetch(`${DASHBOARD_API}/alerts`);

        if (!response.ok) {
            throw new Error(
                "Alerts API error: " + response.status
            );
        }

        const alerts = await response.json();

        activeAlerts = alerts;

        console.log(
            "Security alerts:",
            activeAlerts
        );

        checkCriticalAlerts(activeAlerts);

    } catch (error) {

        console.error(
            "Error loading security alerts:",
            error
        );
    }
}


// =====================================================
// CHECK CRITICAL ALERTS
// =====================================================

function checkCriticalAlerts(alerts) {

    const criticalAlerts = alerts.filter(
        log =>
            String(log.severity || "")
                .toUpperCase() === "CRITICAL"
    );

    if (criticalAlerts.length === 0) {
        return;
    }

    const latestCritical = criticalAlerts[0];

    const alertId = latestCritical.id;

    if (acknowledgedAlerts.includes(alertId)) {
        return;
    }

    showCriticalAlert(latestCritical);
}


// =====================================================
// CRITICAL ALERT POPUP
// =====================================================

function showCriticalAlert(log) {

    const existing =
        document.getElementById("criticalAlertPopup");

    if (existing) {
        existing.remove();
    }

    const popup =
        document.createElement("div");

    popup.id = "criticalAlertPopup";

    popup.innerHTML = `

        <div class="critical-alert-box">

            <div class="critical-alert-title">
                🚨 CRITICAL SECURITY ALERT
            </div>

            <div class="critical-alert-event">
                ${log.eventType || "Security Event"}
            </div>

            <div class="critical-alert-details">

                <p>
                    <strong>User:</strong>
                    ${log.username || "Unknown"}
                </p>

                <p>
                    <strong>Event:</strong>
                    ${log.eventType || "Unknown"}
                </p>

                <p>
                    <strong>Risk:</strong>
                    ${log.severity || "CRITICAL"}
                </p>

                <p>
                    <strong>Source:</strong>
                    ${log.sourceIp || "Unknown"}
                </p>

                <p>
                    <strong>Message:</strong>
                    ${log.message || "No message available"}
                </p>

            </div>

            <div class="critical-alert-actions">

                <button
                    onclick="viewAlertDetails(${log.id})">
                    VIEW DETAILS
                </button>

                <button
                    onclick="acknowledgeAlert(${log.id})">
                    ACKNOWLEDGE
                </button>

            </div>

        </div>
    `;

    document.body.appendChild(popup);
}


// =====================================================
// ACKNOWLEDGE ALERT
// =====================================================

function acknowledgeAlert(id) {

    if (!acknowledgedAlerts.includes(id)) {

        acknowledgedAlerts.push(id);

    }

    const popup =
        document.getElementById("criticalAlertPopup");

    if (popup) {
        popup.remove();
    }

    console.log(
        "Alert acknowledged:",
        id
    );
}


// =====================================================
// VIEW ALERT DETAILS
// =====================================================

function viewAlertDetails(id) {

    const log =
        allLogs.find(
            item => String(item.id) === String(id)
        );

    if (!log) {

        console.error(
            "Alert details not found for ID:",
            id
        );

        return;
    }

    const popup =
        document.getElementById(
            "criticalAlertPopup"
        );

    if (popup) {
        popup.remove();
    }

    // Open Threat Detection first.
    // This creates the detailsArea element.
    showThreats();

    // Now display the selected alert details.
    showLogDetails(log);
}


// =====================================================
// DASHBOARD FILTERS
// =====================================================

function applyFilters() {

    const searchInput =
        document.getElementById("searchInput");

    const severityFilter =
        document.getElementById("severityFilter");

    if (!searchInput || !severityFilter) {
        return;
    }

    const searchText =
        searchInput.value
            .toLowerCase()
            .trim();

    const selectedSeverity =
        severityFilter.value;

    const filteredLogs =
        allLogs.filter(log => {

            const severityMatch =
                selectedSeverity === "ALL" ||
                String(log.severity || "")
                    .toUpperCase() === selectedSeverity;

            const searchMatch =
                searchText === "" ||

                String(log.username || "")
                    .toLowerCase()
                    .includes(searchText) ||

                String(log.eventType || "")
                    .toLowerCase()
                    .includes(searchText) ||

                String(log.sourceIp || "")
                    .toLowerCase()
                    .includes(searchText) ||

                String(log.message || "")
                    .toLowerCase()
                    .includes(searchText) ||

                String(log.aiStatus || "")
                    .toLowerCase()
                    .includes(searchText) ||

                String(log.label || "")
                    .toLowerCase()
                    .includes(searchText);

            return severityMatch && searchMatch;
        });

    displayLogs(filteredLogs);
}


// =====================================================
// DISPLAY LOG TABLE
// =====================================================

function displayLogs(logs) {

    const tableBody =
        document.getElementById("logTable");

    if (!tableBody) {
        return;
    }

    tableBody.innerHTML = "";

    if (!logs || logs.length === 0) {

        tableBody.innerHTML = `
            <tr>
                <td colspan="6"
                    style="
                        text-align:center;
                        color:#64748b;
                        padding:30px;
                    ">
                    No matching logs found
                </td>
            </tr>
        `;

        return;
    }

    logs.forEach(log => {

        const row =
            document.createElement("tr");

        const severity =
            String(
                log.severity || "LOW"
            ).toLowerCase();

        const severityClass =
            "severity-" + severity;

        const aiStatus =
            String(
                log.aiStatus || "PENDING"
            ).toUpperCase();

        const aiClass =
            aiStatus === "ANOMALY"
                ? "ai-anomaly"
                : "ai-normal";

        const risk =
            String(
                log.label || "LOW"
            ).toLowerCase();

        const riskClass =
            "risk-" + risk;

        row.innerHTML = `

            <td class="timestamp">
                ${log.timestamp || "-"}
            </td>

            <td>
                ${log.username || "-"}
            </td>

            <td class="event-name">
                ${log.eventType || "-"}
            </td>

            <td>
                <span class="badge ${severityClass}">
                    ${log.severity || "LOW"}
                </span>
            </td>

            <td>
                <span class="badge ${aiClass}">
                    ${aiStatus}
                </span>
            </td>

            <td>
                <span class="${riskClass}">
                    ${log.label || "LOW"}
                </span>
            </td>

        `;

        tableBody.appendChild(row);
    });
}


// =====================================================
// MANUAL REFRESH
// =====================================================

async function refreshDashboard() {

    const button =
        document.getElementById("refreshButton");

    if (!button) {
        return;
    }

    const originalText =
        button.innerHTML;

    button.innerHTML =
        "↻ Refreshing...";

    button.disabled = true;

    try {

        await loadLogs();

    } finally {

        button.innerHTML =
            originalText;

        button.disabled = false;
    }
}


// =====================================================
// SHOW DASHBOARD
// =====================================================

function showDashboard() {

    currentView = "dashboard";

    document.getElementById(
        "dashboardView"
    ).style.display = "block";

    document.getElementById(
        "analyticsView"
    ).style.display = "none";

    document.getElementById(
        "threatView"
    ).style.display = "none";

    document.getElementById(
        "systemView"
    ).style.display = "none";

    setActiveNav("navDashboard");

    applyFilters();
}


// =====================================================
// LOG ANALYTICS
// =====================================================

function showAnalytics() {

    currentView = "analytics";

    document.getElementById(
        "dashboardView"
    ).style.display = "none";

    document.getElementById(
        "analyticsView"
    ).style.display = "block";

    document.getElementById(
        "threatView"
    ).style.display = "none";

    document.getElementById(
        "systemView"
    ).style.display = "none";

    setActiveNav("navAnalytics");

    const loginLogs =
        allLogs.filter(log =>
            String(log.eventType || "")
                .toUpperCase()
                .includes("LOGIN")
        );

    const fileLogs =
        allLogs.filter(log =>
            String(log.eventType || "")
                .toUpperCase()
                .includes("FILE")
        );

    const suspiciousLogs =
        allLogs.filter(log =>
            String(log.eventType || "")
                .toUpperCase()
                .includes("SUSPICIOUS")
        );

    document.getElementById(
        "analyticsView"
    ).innerHTML = `

        <div class="view-card">

            <h3>Log Analytics</h3>

            <p>
                Analyse security events collected by SmartLog.
                Click a category to view the corresponding logs.
            </p>

            <div class="analysis-grid">

                <div class="analysis-box"
                     onclick="showLogDetails(
                         'Login Events',
                         'LOGIN'
                     )">

                    <h4>LOGIN EVENTS</h4>

                    <strong>
                        ${loginLogs.length}
                    </strong>

                </div>


                <div class="analysis-box"
                     onclick="showLogDetails(
                         'File Access Events',
                         'FILE'
                     )">

                    <h4>FILE ACCESS</h4>

                    <strong>
                        ${fileLogs.length}
                    </strong>

                </div>


                <div class="analysis-box"
                     onclick="showLogDetails(
                         'Suspicious Activity',
                         'SUSPICIOUS'
                     )">

                    <h4>SUSPICIOUS ACTIVITY</h4>

                    <strong>
                        ${suspiciousLogs.length}
                    </strong>

                </div>

            </div>

            <div id="detailsArea"></div>

        </div>
    `;
}


// =====================================================
// THREAT DETECTION
// =====================================================

function showThreats() {

    currentView = "threats";

    document.getElementById(
        "dashboardView"
    ).style.display = "none";

    document.getElementById(
        "analyticsView"
    ).style.display = "none";

    document.getElementById(
        "threatView"
    ).style.display = "block";

    document.getElementById(
        "systemView"
    ).style.display = "none";

    setActiveNav("navThreats");

    const anomalies =
        allLogs.filter(log =>
            String(log.aiStatus || "")
                .toUpperCase() === "ANOMALY"
        );

    const highRisk =
        allLogs.filter(log =>
            ["HIGH", "CRITICAL"].includes(
                String(log.severity || "")
                    .toUpperCase()
            )
        );

    const critical =
        allLogs.filter(log =>
            String(log.severity || "")
                .toUpperCase() === "CRITICAL"
        );

    document.getElementById(
        "threatView"
    ).innerHTML = `

        <div class="view-card">

            <h3>Threat Detection</h3>

            <p>
                Security events identified as anomalous
                or high risk.
                Click a category to view the corresponding logs.
            </p>

            <div class="analysis-grid">

                <div class="analysis-box"
                     onclick="showLogDetails(
                         'AI Anomalies',
                         'ANOMALY'
                     )">

                    <h4>AI ANOMALIES</h4>

                    <strong class="status-info">
                        ${anomalies.length}
                    </strong>

                </div>


                <div class="analysis-box"
                     onclick="showLogDetails(
                         'High Risk Events',
                         'HIGH_RISK'
                     )">

                    <h4>HIGH RISK EVENTS</h4>

                    <strong>
                        ${highRisk.length}
                    </strong>

                </div>


                <div class="analysis-box"
                     onclick="showLogDetails(
                         'Critical Events',
                         'CRITICAL'
                     )">

                    <h4>CRITICAL EVENTS</h4>

                    <strong style="color:#f87171">

                        ${critical.length}

                    </strong>

                </div>

            </div>

            <div id="detailsArea"></div>

        </div>
    `;
}


// =====================================================
// SYSTEM STATUS
// =====================================================

async function showSystemStatus() {

    currentView = "system";

    document.getElementById(
        "dashboardView"
    ).style.display = "none";

    document.getElementById(
        "analyticsView"
    ).style.display = "none";

    document.getElementById(
        "threatView"
    ).style.display = "none";

    const systemView =
        document.getElementById("systemView");

    systemView.style.display = "block";

    setActiveNav("navSystem");

    try {

        const response =
            await fetch(HEALTH_API);

        if (!response.ok) {

            throw new Error(
                "Health API error: " +
                response.status
            );
        }

        const health =
            await response.json();

        systemView.innerHTML = `

            <div class="section-header">

                <h2>System Status</h2>

                <p>
                    Live SmartLog service health
                </p>

            </div>


            <div class="health-grid">

                ${createHealthCard(
                    "Spring Boot",
                    health.springBoot
                )}

                ${createHealthCard(
                    "MySQL",
                    health.mysql
                )}

                ${createHealthCard(
                    "AI Service",
                    health.aiService
                )}

                ${createHealthCard(
                    "Elasticsearch",
                    health.elasticsearch
                )}

                ${createHealthCard(
                    "Logstash",
                    health.logstash
                )}

            </div>
        `;

    } catch (error) {

        console.error(
            "Error loading system health:",
            error
        );

        systemView.innerHTML = `

            <div class="section-header">

                <h2>System Status</h2>

                <p style="color:#f87171;">

                    Unable to retrieve system health

                </p>

            </div>
        `;
    }
}


// =====================================================
// HEALTH CARD
// =====================================================

function createHealthCard(name, status) {

    const online =
        String(status || "")
            .toUpperCase() === "ONLINE";

    const statusClass =
        online
            ? "online"
            : "offline";

    const icon = "●";

    return `

        <div class="health-card">

            <div class="health-card-name">
                ${name}
            </div>

            <div class="health-status ${statusClass}">
                ${icon} ${status || "UNKNOWN"}
            </div>

        </div>
    `;
}


// =====================================================
// LOG DETAILS
// =====================================================

function showLogDetails(titleOrLog, type) {

    // Critical alert can pass the actual log object
    if (
    typeof titleOrLog === "object" &&
    titleOrLog !== null
) {

    const log = titleOrLog;

    const area =
        document.getElementById("detailsArea");

    if (!area) {
        console.error("detailsArea not found");
        return;
    }

    area.innerHTML = `

        <div class="content-card">

            <div class="content-header">

                <div class="content-title">

                    <h3>🚨 Security Alert Details</h3>

                    <p>Critical security event</p>

                </div>

                <button
                    class="refresh-btn"
                    onclick="closeDetails()">

                    ✕ Close

                </button>

            </div>


            <div style="
                padding: 25px;
                line-height: 1.8;
            ">

                <p>
                    <strong>Timestamp:</strong>
                    ${log.timestamp || "-"}
                </p>

                <p>
                    <strong>User:</strong>
                    ${log.username || "Unknown"}
                </p>

                <p>
                    <strong>Event:</strong>
                    ${log.eventType || "Unknown"}
                </p>

                <p>
                    <strong>Event Code:</strong>
                    ${log.eventCode || "Unknown"}
                </p>

                <p>
                    <strong>Severity:</strong>
                    ${log.severity || "Unknown"}
                </p>

                <p>
                    <strong>AI Status:</strong>
                    ${log.aiStatus || "Unknown"}
                </p>

                <p>
                    <strong>Risk:</strong>
                    ${log.label || "Unknown"}
                </p>

                <p>
                    <strong>Source IP:</strong>
                    ${log.sourceIp || "Unknown"}
                </p>

                <p>
                    <strong>Elasticsearch ID:</strong>
                    ${log.elasticsearchId || "Unknown"}
                </p>

                <p>
                    <strong>Message:</strong>
                    ${log.message || "No message available"}
                </p>

            </div>

        </div>

    `;

    return;
}

    const title =
        titleOrLog;

    const area =
        document.getElementById("detailsArea");

    if (!area) {

        console.error(
            "detailsArea not found"
        );

        return;
    }

    let logs = [];


    // LOGIN EVENTS

    if (type === "LOGIN") {

        logs =
            allLogs.filter(log =>
                String(log.eventType || "")
                    .toUpperCase()
                    .includes("LOGIN")
            );
    }


    // FILE ACCESS

    else if (type === "FILE") {

        logs =
            allLogs.filter(log =>
                String(log.eventType || "")
                    .toUpperCase()
                    .includes("FILE")
            );
    }


    // SUSPICIOUS ACTIVITY

    else if (type === "SUSPICIOUS") {

        logs =
            allLogs.filter(log =>
                String(log.eventType || "")
                    .toUpperCase()
                    .includes("SUSPICIOUS")
            );
    }


    // AI ANOMALIES

    else if (type === "ANOMALY") {

        logs =
            allLogs.filter(log =>
                String(log.aiStatus || "")
                    .toUpperCase() === "ANOMALY"
            );
    }


    // HIGH RISK

    else if (type === "HIGH_RISK") {

        logs =
            allLogs.filter(log =>
                ["HIGH", "CRITICAL"].includes(
                    String(log.severity || "")
                        .toUpperCase()
                )
            );
    }


    // CRITICAL

    else if (type === "CRITICAL") {

        logs =
            allLogs.filter(log =>
                String(log.severity || "")
                    .toUpperCase() === "CRITICAL"
            );
    }


    console.log(
        "Selected:",
        title
    );

    console.log(
        "Matching logs:",
        logs
    );


    if (logs.length === 0) {

        area.innerHTML = `

            <div class="content-card"
                 style="margin-top:25px;">

                <div style="
                    padding:30px;
                    text-align:center;
                    color:#64748b;
                ">

                    No logs found for ${title}.

                </div>

            </div>

        `;

        return;
    }


    let rows = "";


    logs.forEach(log => {

        const severity =
            String(
                log.severity || "LOW"
            ).toLowerCase();

        const aiStatus =
            String(
                log.aiStatus || "PENDING"
            ).toUpperCase();

        const aiClass =
            aiStatus === "ANOMALY"
                ? "ai-anomaly"
                : "ai-normal";

        const risk =
            String(
                log.label || "LOW"
            ).toLowerCase();


        rows += `

            <tr>

                <td class="timestamp">
                    ${log.timestamp || "-"}
                </td>

                <td>
                    ${log.username || "-"}
                </td>

                <td class="event-name">
                    ${log.eventType || "-"}
                </td>

                <td>

                    <span class="badge severity-${severity}">
                        ${log.severity || "LOW"}
                    </span>

                </td>

                <td>

                    <span class="badge ${aiClass}">
                        ${aiStatus}
                    </span>

                </td>

                <td>

                    <span class="risk-${risk}">
                        ${log.label || "LOW"}
                    </span>

                </td>

            </tr>

        `;
    });


    area.innerHTML = `

        <div class="content-card">

            <div class="content-header">

                <div class="content-title">

                    <h3>
                        ${title}
                    </h3>

                    <p>
                        ${logs.length} matching log(s)
                    </p>

                </div>


                <button
                    class="refresh-btn"
                    onclick="closeDetails()">

                    ✕ Close

                </button>

            </div>


            <div class="table-wrapper">

                <table>

                    <thead>

                        <tr>

                            <th>Timestamp</th>

                            <th>User</th>

                            <th>Event</th>

                            <th>Severity</th>

                            <th>AI Status</th>

                            <th>Risk</th>

                        </tr>

                    </thead>


                    <tbody>

                        ${rows}

                    </tbody>

                </table>

            </div>

        </div>

    `;
}


// =====================================================
// CLOSE DETAILS
// =====================================================

function closeDetails() {

    const area =
        document.getElementById(
            "detailsArea"
        );

    if (area) {
        area.innerHTML = "";
    }
}


// =====================================================
// NAVIGATION
// =====================================================

function setActiveNav(activeId) {

    document
        .querySelectorAll(".nav-item")
        .forEach(item => {

            item.classList.remove("active");

        });

    const active =
        document.getElementById(activeId);

    if (active) {
        active.classList.add("active");
    }
}


// =====================================================
// EVENT LISTENERS
// =====================================================

document
    .getElementById("navDashboard")
    .addEventListener(
        "click",
        showDashboard
    );

document
    .getElementById("navAnalytics")
    .addEventListener(
        "click",
        showAnalytics
    );

document
    .getElementById("navThreats")
    .addEventListener(
        "click",
        showThreats
    );

document
    .getElementById("navSystem")
    .addEventListener(
        "click",
        showSystemStatus
    );


// IMPORTANT:
// Refresh button now calls refreshDashboard()
// instead of directly calling loadLogs().

document
    .getElementById("refreshButton")
    .addEventListener(
        "click",
        refreshDashboard
    );


document
    .getElementById("severityFilter")
    .addEventListener(
        "change",
        applyFilters
    );


document
    .getElementById("searchInput")
    .addEventListener(
        "input",
        applyFilters
    );


// =====================================================
// START SMARTLOG
// =====================================================

loadLogs();


// =====================================================
// AUTO REFRESH EVERY 5 SECONDS
// =====================================================

setInterval(() => {

    loadLogs();

}, 5000);