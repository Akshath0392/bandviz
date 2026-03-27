import React, { useEffect, useState } from "./vendor/react.mjs";
import { createRoot } from "./vendor/react-dom-client.mjs";
import htm from "./vendor/htm.mjs";

const html = htm.bind(React.createElement);

async function api(path, opts = {}) {
  const res = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...opts
  });
  if (!res.ok) {
    const text = await res.text();
    let details = null;
    try {
      details = text ? JSON.parse(text) : null;
    } catch {
      details = null;
    }
    const message = details?.message || details?.error || text || `HTTP ${res.status}`;
    const error = new Error(message);
    error.status = res.status;
    error.details = details;
    error.path = path;
    throw error;
  }
  if (res.status === 204) return null;
  return res.json();
}

function humanize(value) {
  return String(value ?? "").replaceAll("_", " ");
}

function initials(name) {
  return (name || "")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || "")
    .join("") || "--";
}

function statusClass(status) {
  return `b-${status}`;
}

function daysRemaining(endDate) {
  if (!endDate) return null;
  const end = new Date(endDate);
  const now = new Date();
  const utcEnd = Date.UTC(end.getFullYear(), end.getMonth(), end.getDate());
  const utcNow = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.max(0, Math.round((utcEnd - utcNow) / 86400000));
}

function planningLabel(dashboard) {
  return dashboard?.planningMode === "KANBAN"
    ? "Current planning window"
    : dashboard?.planningMode === "HYBRID"
      ? "Hybrid planning window"
      : dashboard?.sprintName || "Current planning window";
}

function planningRangeText(startDate, endDate) {
  if (!startDate || !endDate) return "No planning window timeline available";
  const remainingDays = daysRemaining(endDate);
  return `${startDate} to ${endDate}${remainingDays != null ? ` · ${remainingDays} day${remainingDays === 1 ? "" : "s"} remaining` : ""}`;
}

function formatDate(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString("en-US", { month: "short", day: "numeric" });
}

function formatDateTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
}

function formatMonthLabel(value) {
  return value.toLocaleDateString("en-US", { month: "long", year: "numeric" });
}

function toIsoDate(value) {
  return value.toISOString().slice(0, 10);
}

function startOfMonth(value) {
  return new Date(value.getFullYear(), value.getMonth(), 1);
}

function endOfMonth(value) {
  return new Date(value.getFullYear(), value.getMonth() + 1, 0);
}

function addMonths(value, count) {
  return new Date(value.getFullYear(), value.getMonth() + count, 1);
}

function addDays(value, count) {
  const next = new Date(value);
  next.setDate(next.getDate() + count);
  return next;
}

function datesOverlap(startA, endA, startB, endB) {
  return startA <= endB && endA >= startB;
}

function isSameDay(left, right) {
  return left.getFullYear() === right.getFullYear()
    && left.getMonth() === right.getMonth()
    && left.getDate() === right.getDate();
}

function rangeDays(startDate, endDate) {
  const dates = [];
  const cursor = new Date(startDate);
  while (cursor <= endDate) {
    dates.push(new Date(cursor));
    cursor.setDate(cursor.getDate() + 1);
  }
  return dates;
}

function leaveTypeLabel(type) {
  const labels = {
    PLANNED: "Planned",
    SICK: "Sick",
    COMP_OFF: "Comp Off",
    PUBLIC_HOLIDAY: "Holiday",
    UNPAID: "Unpaid"
  };
  return labels[type] || humanize(type);
}

function leaveTypeClass(type) {
  const classes = {
    PLANNED: "lb-planned",
    SICK: "lb-sick",
    COMP_OFF: "lb-comp",
    PUBLIC_HOLIDAY: "lb-public",
    UNPAID: "lb-unpaid"
  };
  return classes[type] || "lb-planned";
}

function durationLabel(days) {
  if (!days) return "1 day";
  return `${days} day${days === 1 ? "" : "s"}`;
}

function clamp(value, min = 0, max = 100) {
  return Math.min(max, Math.max(min, value));
}

function formatRelativeTime(value) {
  if (!value) return "Never";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const diffMs = Date.now() - date.getTime();
  const diffMinutes = Math.max(0, Math.round(diffMs / 60000));
  if (diffMinutes < 1) return "Just now";
  if (diffMinutes < 60) return `${diffMinutes} min ago`;
  const diffHours = Math.round(diffMinutes / 60);
  if (diffHours < 24) return `${diffHours}h ago`;
  const diffDays = Math.round(diffHours / 24);
  return `${diffDays}d ago`;
}

function downloadCsv(filename, rows) {
  const csv = rows
    .map((row) => row.map((value) => `"${String(value ?? "").replaceAll("\"", "\"\"")}"`).join(","))
    .join("\n");
  const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function deliveryModeSummary(projects) {
  const counts = { SPRINT: 0, KANBAN: 0, HYBRID: 0 };
  projects.forEach((project) => {
    const mode = project.deliveryMode || "HYBRID";
    counts[mode] = (counts[mode] || 0) + 1;
  });
  return counts;
}

function Banner({ message, tone = "info" }) {
  return html`<div className=${`banner ${message ? `show banner-${tone}` : ""}`}>${message || ""}</div>`;
}

function MetricCard({ value, label, subtext }) {
  return html`
    <div className="card">
      <div className="card-pad">
        <div className="metric-value">${value}</div>
        <div className="metric-label">${label}</div>
        ${subtext ? html`<div className="metric-sub">${subtext}</div>` : null}
      </div>
    </div>
  `;
}

function SectionCard({ title, subtitle, children }) {
  return html`
    <section className="card">
      <div className="card-header">
        <div>
          <div className="card-title">${title}</div>
          ${subtitle ? html`<div className="card-sub">${subtitle}</div>` : null}
        </div>
      </div>
      <div className="card-pad">${children}</div>
    </section>
  `;
}

function StatusBadge({ status }) {
  if (!status) return html`<span className="notice">No status</span>`;
  return html`<span className=${`badge ${statusClass(status)}`}>${humanize(status)}</span>`;
}

function Nav() {
  const page = document.body.dataset.page;
  const items = [
    { section: "Overview", links: [
      { href: "/home.html", page: "home", icon: "🏠", label: "Home" },
      { href: "/team-dashboard.html", page: "dashboard", icon: "📊", label: "Team Dashboard" },
      { href: "/developer-detail.html", page: "developer", icon: "👤", label: "Developer Detail" }
    ]},
    { section: "Planning", links: [
      { href: "/leave-calendar.html", page: "leaves", icon: "📅", label: "Leave Calendar" },
      { href: "/capacity-planner.html", page: "planner", icon: "⚡", label: "Capacity Planner" }
    ]},
    { section: "Integrations", links: [
      { href: "/jira-sync.html", page: "jira", icon: "🔗", label: "Jira Sync" },
      { href: "/settings.html", page: "settings", icon: "⚙️", label: "Settings" }
    ]}
  ];

  return html`
    <aside className="sidebar">
      <div className="sidebar-logo">Band<span>Viz</span></div>
      <nav className="sidebar-nav">
        ${items.map((group) => html`
          <div key=${group.section}>
            <div className="nav-section-label">${group.section}</div>
            <div className="nav">
              ${group.links.map((link) => html`
                <a
                  key=${link.href}
                  href=${link.href}
                  className=${page === link.page ? "active" : ""}
                >
                  <span>${link.icon}</span>
                  <span>${link.label}</span>
                </a>
              `)}
            </div>
          </div>
        `)}
      </nav>
      <div className="sidebar-footer">
        <div className="avatar-small">BV</div>
        <div>
          <div style=${{ fontSize: "13px", color: "#d1d5db", fontWeight: 600 }}>BandViz</div>
          <div style=${{ fontSize: "11px", color: "#7c8ab7" }}>React Workspace</div>
        </div>
      </div>
    </aside>
  `;
}

function Topbar({ title, subtitle, actions, leading }) {
  return html`
    <div className="topbar">
      <div className="topbar-left-group">
        ${leading ? html`<div>${leading}</div>` : null}
        <div>
          <h1>${title}</h1>
          <p>${subtitle}</p>
        </div>
      </div>
      <div className="topbar-actions">${actions}</div>
    </div>
  `;
}

function PageShell({ title, subtitle, actions, banner, children, leading }) {
  return html`
    <div className="app-shell">
      <${Nav} />
      <main className="main">
        <${Topbar} title=${title} subtitle=${subtitle} actions=${actions} leading=${leading} />
        <div className="page">
          <${Banner} message=${banner?.message} tone=${banner?.tone || "info"} />
          ${children}
        </div>
      </main>
    </div>
  `;
}

function HomePage() {
  const [state, setState] = useState({
    loading: true,
    error: "",
    banner: { message: "", tone: "info" },
    dashboard: null,
    jiraStatus: null
  });

  useEffect(() => {
    let ignore = false;
    async function load() {
      try {
        const [dashboard, jiraStatus] = await Promise.all([
          api("/api/dashboard-summary"),
          api("/api/jira-sync/status").catch(() => null)
        ]);
        if (ignore) return;
        const alerts = Array.isArray(dashboard.alerts) ? dashboard.alerts : [];
        setState({
          loading: false,
          error: "",
          dashboard,
          jiraStatus,
          banner: {
            message: alerts.length
              ? `Monitoring ${alerts.length} active alert${alerts.length === 1 ? "" : "s"} across the team.`
              : "No active alerts right now. Team health looks stable.",
            tone: alerts.length ? "warning" : "info"
          }
        });
      } catch (error) {
        if (ignore) return;
        setState({
          loading: false,
          error: error.message,
          dashboard: null,
          jiraStatus: null,
          banner: { message: `Unable to load home dashboard: ${error.message}`, tone: "error" }
        });
      }
    }
    load();
    return () => {
      ignore = true;
    };
  }, []);

  const dashboard = state.dashboard;
  const jiraStatus = state.jiraStatus;
  const totalDevelopers = dashboard?.totalDevelopers ?? 0;
  const availableCount = dashboard?.availableCount ?? 0;
  const busyCount = dashboard?.busyCount ?? 0;
  const overloadedCount = dashboard?.overloadedCount ?? 0;
  const onLeaveCount = dashboard?.onLeaveCount ?? 0;
  const openTickets = dashboard?.totalOpenTickets ?? 0;
  const blockedTickets = dashboard?.totalBlockedTickets ?? 0;
  const closedTickets = dashboard?.totalClosedInWindow ?? dashboard?.totalClosedThisSprint ?? 0;
  const avgUtilization = dashboard?.averageUtilizationPct ?? 0;
  const totalTrackedTickets = openTickets + closedTickets;
  const progressPct = totalTrackedTickets ? Math.round((closedTickets / totalTrackedTickets) * 100) : 0;
  const plannedCapacity = totalDevelopers * 40;
  const developers = Array.isArray(dashboard?.developers) ? dashboard.developers : [];
  const alerts = Array.isArray(dashboard?.alerts) ? dashboard.alerts : [];
  const topDevelopers = [...developers]
    .sort((a, b) => (b.effectiveBandwidthPct ?? 0) - (a.effectiveBandwidthPct ?? 0))
    .slice(0, 5);
  const allocations = new Map();
  developers.forEach((dev) => {
    (dev.projectAllocations || []).forEach((item) => {
      allocations.set(item.projectName, (allocations.get(item.projectName) || 0) + (item.allocationPct || 0));
    });
  });
  const healthRows = [...allocations.entries()]
    .map(([name, total]) => ({ name, total: Math.min(100, total) }))
    .sort((a, b) => b.total - a.total)
    .slice(0, 5);
  const totalStoryPoints = developers.reduce((sum, dev) => sum + (dev.totalStoryPoints ?? 0), 0);
  const activeDate = new Date();
  const topbarTitle = dashboard ? "Good morning" : "Home";
  const topbarSubtitle = dashboard
    ? `${activeDate.toLocaleDateString("en-US", { weekday: "long", month: "long", day: "numeric", year: "numeric" })} · ${humanize(dashboard?.planningMode || "HYBRID")} planning`
    : "Create a planning window or sync Jira to populate the overview";
  const recentActivity = [];
  if (jiraStatus?.lastSyncedAt) {
    recentActivity.push({
      icon: "🔗",
      text: `Jira sync refreshed ${jiraStatus.totalTickets ?? 0} tickets across mapped projects`,
      time: formatRelativeTime(jiraStatus.lastSyncedAt)
    });
  }
  if (alerts[0]) {
    recentActivity.push({
      icon: "⚠️",
      text: alerts[0].message,
      time: `${alerts[0].severity} priority`
    });
  }
  topDevelopers.slice(0, 2).forEach((dev) => {
    recentActivity.push({
      icon: "👤",
      text: `${dev.developerName} is carrying ${dev.openTickets ?? 0} open tickets at ${dev.effectiveBandwidthPct ?? 0}% bandwidth`,
      time: humanize(dev.status)
    });
  });
  if (!recentActivity.length) {
    recentActivity.push({ icon: "🧭", text: "Connect Jira and add allocations to start seeing recent team activity here.", time: "Waiting for data" });
  }

  return html`
    <${PageShell}
      title=${topbarTitle}
      subtitle=${topbarSubtitle}
      actions=${html`
        <span className="chip">${planningLabel(dashboard)}</span>
        <a className="btn btn-secondary" href="/jira-sync.html">🔔 ${alerts.length} Alert${alerts.length === 1 ? "" : "s"}</a>
        <a className="btn btn-primary" href="/capacity-planner.html">Plan Bandwidth</a>
      `}
      banner=${state.banner}
    >
      <section className="home-hero">
        <div className="home-hero-left">
          <div className="hero-kicker">${dashboard ? `${planningLabel(dashboard)} · ${humanize(dashboard.planningMode || "HYBRID")}` : "Workspace setup needed"}</div>
          <h2 className="hero-title">${dashboard ? "Your team at a glance" : "Your team dashboard is ready for data"}</h2>
          <div className="hero-copy">
            ${dashboard
              ? `${overloadedCount} developers are overloaded, ${onLeaveCount} are on leave this window, and ${blockedTickets} blocked tickets need attention.`
              : "Create a planning window, add allocations, and sync Jira to unlock the dashboard overview."}
          </div>
          <div className="hero-actions">
            <a className="btn btn-primary" href="/team-dashboard.html">View Dashboard</a>
            <a className="btn btn-secondary" href="/capacity-planner.html">${dashboard?.planningMode === "SPRINT" ? "Plan Next Sprint" : "Plan Next Window"}</a>
          </div>
        </div>
        <div className="home-hero-right">
          <div className="hero-stat-pill">
            <div className="hero-stat-val">${dashboard ? totalDevelopers : "-"}</div>
            <div className="hero-stat-lbl">Developers</div>
          </div>
          <div className="hero-stat-pill">
            <div className="hero-stat-val">${dashboard ? openTickets : "-"}</div>
            <div className="hero-stat-lbl">Open Tickets</div>
          </div>
          <div className="hero-stat-pill">
            <div className="hero-stat-val">${dashboard ? onLeaveCount : "-"}</div>
            <div className="hero-stat-lbl">On Leave</div>
          </div>
        </div>
      </section>

      <div className="stats-grid">
        <${DashboardStatCard}
          icon="✅"
          tone="green"
          value=${dashboard ? availableCount : "-"}
          label="Available Today"
          subtext=${dashboard ? `${totalDevelopers ? Math.round((availableCount / totalDevelopers) * 100) : 0}% of team` : "No planning data to summarize yet"}
        />
        <${DashboardStatCard}
          icon="🔴"
          tone="red"
          value=${dashboard ? overloadedCount : "-"}
          label="Overloaded"
          subtext=${dashboard ? (overloadedCount ? "Need immediate attention" : "No overloaded developers") : "No planning window health data"}
        />
        <${DashboardStatCard}
          icon="🎫"
          tone="orange"
          value=${dashboard ? openTickets : "-"}
          label="Open Jira Tickets"
          subtext=${dashboard ? (blockedTickets ? `${blockedTickets} blocked tickets` : "No blocked ticket pressure") : "Jira metrics will appear after sync"}
        />
        <${DashboardStatCard}
          icon="📈"
          tone="blue"
          value=${dashboard ? `${avgUtilization}%` : "-"}
          label="Avg Team Capacity"
          subtext=${dashboard ? "Target: <= 75%" : "Create a planning window to compute utilization"}
        />
      </div>

      <div className="sprint-card">
        <div className="sprint-header">
          <div className="sprint-title">${dashboard?.sprintName || planningLabel(dashboard)} Progress</div>
          <div className="sprint-dates">${planningRangeText(dashboard?.sprintStart, dashboard?.sprintEnd)}</div>
        </div>
        <div className="sprint-progress-bar">
          <div className="sprint-fill" style=${{ width: `${progressPct}%` }}></div>
        </div>
        <div className="sprint-meta">
          <div className="sprint-meta-item"><div className="sprint-meta-val">${dashboard ? `${progressPct}%` : "-"}</div><div className="sprint-meta-lbl">Window Complete</div></div>
          <div className="sprint-meta-item"><div className="sprint-meta-val">${dashboard ? `${plannedCapacity}h` : "-"}</div><div className="sprint-meta-lbl">Planned Capacity</div></div>
          <div className="sprint-meta-item"><div className="sprint-meta-val">${dashboard ? totalStoryPoints : "-"}</div><div className="sprint-meta-lbl">Story Pts Planned</div></div>
          <div className="sprint-meta-item"><div className="sprint-meta-val">${dashboard ? closedTickets : "-"}</div><div className="sprint-meta-lbl">Closed Tickets</div></div>
          <div className="sprint-meta-item"><div className="sprint-meta-val">${dashboard ? openTickets : "-"}</div><div className="sprint-meta-lbl">Open Tickets</div></div>
          <div className="sprint-meta-item"><div className="sprint-meta-val">${dashboard ? blockedTickets : "-"}</div><div className="sprint-meta-lbl">Blocked Tickets</div></div>
        </div>
      </div>

      <div className="home-two-col">
        <div className="stack">
          <section className="card">
            <div className="card-header"><span className="card-title">Quick Actions</span></div>
            <div className="card-body">
              <div className="quick-grid">
                <a href="/team-dashboard.html" className="quick-item"><span className="quick-item-icon">📊</span><span className="quick-item-label">Team Dashboard</span><span className="quick-item-sub">All ${totalDevelopers || 0} devs</span></a>
                <a href="/leave-calendar.html" className="quick-item"><span className="quick-item-icon">📅</span><span className="quick-item-label">Leave Calendar</span><span className="quick-item-sub">${onLeaveCount} on leave</span></a>
                <a href="/capacity-planner.html" className="quick-item"><span className="quick-item-icon">⚡</span><span className="quick-item-label">Plan Bandwidth</span><span className="quick-item-sub">Allocate to projects</span></a>
                <a href="/jira-sync.html" className="quick-item"><span className="quick-item-icon">🔗</span><span className="quick-item-label">Jira Sync</span><span className="quick-item-sub">${jiraStatus?.lastSyncedAt ? `Synced ${formatRelativeTime(jiraStatus.lastSyncedAt)}` : "Integration status"}</span></a>
                <a href="/developer-detail.html" className="quick-item"><span className="quick-item-icon">👤</span><span className="quick-item-label">Dev Detail</span><span className="quick-item-sub">Drill down</span></a>
                <a href="/settings.html" className="quick-item"><span className="quick-item-icon">⚙️</span><span className="quick-item-label">Settings</span><span className="quick-item-sub">Manage team</span></a>
              </div>
            </div>
          </section>

          <section className="card">
            <div className="card-header">
              <span className="card-title">Team Health by Project</span>
              <a href="/team-dashboard.html" className="card-link">View all →</a>
            </div>
            <div className="card-body">
              ${healthRows.length
                ? healthRows.map((row) => html`
                    <div key=${row.name} className="health-row">
                      <span className="health-label">${row.name}</span>
                      <div className="health-bar-wrap"><div className=${`health-bar ${row.total >= 85 ? "hb-red" : row.total >= 70 ? "hb-yellow" : "hb-green"}`} style=${{ width: `${row.total}%` }}></div></div>
                      <span className="health-pct" style=${{ color: row.total >= 85 ? "#dc2626" : row.total >= 70 ? "#d97706" : "#16a34a" }}>${row.total}%</span>
                    </div>
                  `)
                : html`<div className="notice">No active allocations yet.</div>`}
            </div>
          </section>
        </div>

        <div className="stack">
          <section className="card">
            <div className="card-header">
              <span className="card-title">⚠️ Alerts</span>
              <span className="alert-pill">${alerts.length} Active</span>
            </div>
            <div className="card-body">
              <div className="alert-list">
                ${alerts.length
                  ? alerts.slice(0, 4).map((alert, index) => html`
                      <div key=${index} className="alert-item">
                        <div className=${`alert-dot ${alert.severity === "HIGH" ? "dot-red" : alert.severity === "MEDIUM" ? "dot-orange" : "dot-blue"}`}></div>
                        <div>
                          <div className="alert-text">${alert.message}</div>
                          <div className="alert-time">${alert.severity} priority</div>
                        </div>
                      </div>
                    `)
                  : html`<div className="notice">No alerts are active right now.</div>`}
              </div>
            </div>
          </section>

          <section className="card">
            <div className="card-header">
              <span className="card-title">Recent Activity</span>
              <span className="card-link">Live feed</span>
            </div>
            <div className="card-body">
              ${recentActivity.map((item, index) => html`
                <div key=${index} className="activity-item">
                  <div className="act-avatar">${item.icon}</div>
                  <div>
                    <div className="act-text">${item.text}</div>
                    <div className="act-time">${item.time}</div>
                  </div>
                </div>
              `)}
            </div>
          </section>
        </div>
      </div>

      ${state.error ? html`<div className="error-box">Failed to load data: ${state.error}</div>` : null}
    </${PageShell}>
  `;
}

function bandwidthTone(value) {
  if ((value ?? 0) >= 85) return "high";
  if ((value ?? 0) >= 70) return "medium";
  return "low";
}

function dashboardStatusClass(status) {
  switch (status) {
    case "AVAILABLE":
      return "status-available";
    case "BUSY":
      return "status-busy";
    case "OVERLOADED":
      return "status-overloaded";
    case "ON_LEAVE":
      return "status-leave";
    default:
      return "status-leave";
  }
}

function DashboardStatCard({ icon, tone, value, label, subtext }) {
  return html`
    <div className="stat-card">
      <div className=${`stat-icon ${tone}`}>${icon}</div>
      <div>
        <div className="stat-value">${value}</div>
        <div className="stat-label">${label}</div>
        ${subtext ? html`<div className="stat-sub">${subtext}</div>` : null}
      </div>
    </div>
  `;
}

function DeveloperDashboardCard({ row }) {
  const utilization = row.effectiveBandwidthPct ?? 0;
  const tone = bandwidthTone(utilization);
  const activeTickets = Math.max((row.openTickets ?? 0) - (row.blockedTickets ?? 0), 0);
  const projectTags = Array.isArray(row.projectAllocations) ? row.projectAllocations.slice(0, 3) : [];

  return html`
    <div className=${`dev-card ${row.status === "ON_LEAVE" ? "dev-card-muted" : ""}`}>
      <div className="dev-card-header">
        <div className="avatar">${initials(row.developerName)}</div>
        <div>
          <div className="dev-name">${row.developerName}</div>
          <div className="dev-role">${humanize(row.role)}</div>
        </div>
        <span className=${`dev-status ${dashboardStatusClass(row.status)}`}>${humanize(row.status)}</span>
      </div>
      <div className="dev-card-body">
        <div className="bw-row">
          <div className="bw-label">
            <span className="bw-label-text">Bandwidth Used</span>
            <span className=${`bw-pct ${tone}`}>${utilization}%</span>
          </div>
          <div className="bw-track">
            <div className=${`bw-fill ${tone}`} style=${{ width: `${Math.min(100, utilization)}%` }}></div>
          </div>
        </div>
        <div className="metrics-row">
          <div className="metric-box">
            <div className="metric-val">${row.openTickets ?? 0}</div>
            <div className="metric-lbl">Tickets</div>
          </div>
          <div className="metric-box">
            <div className="metric-val">${row.totalStoryPoints ?? 0}</div>
            <div className="metric-lbl">Story Pts</div>
          </div>
          <div className="metric-box">
            <div className="metric-val">${row.leaveDaysInPeriod ?? 0}</div>
            <div className="metric-lbl">Leave Days</div>
          </div>
        </div>
        <div className="tickets-row">
          ${activeTickets
            ? html`<span className="ticket-pill pill-inprogress"><span className="pill-dot"></span>${activeTickets} Active</span>`
            : null}
          ${row.blockedTickets
            ? html`<span className="ticket-pill pill-blocked"><span className="pill-dot"></span>${row.blockedTickets} Blocked</span>`
            : null}
          ${!activeTickets && !row.blockedTickets
            ? html`<span className="ticket-pill pill-review"><span className="pill-dot"></span>No ticket pressure</span>`
            : null}
        </div>
        ${(row.leaveDaysInPeriod ?? 0) > 0
          ? html`
              <div className="leave-bar">
                <span className="icon">📅</span>
                <span>Leave in window · <span className="leave-days">${row.leaveDaysInPeriod} day${row.leaveDaysInPeriod === 1 ? "" : "s"}</span></span>
              </div>
            `
          : null}
      </div>
      <div className="dev-card-footer">
        <div className="project-tags">
          ${projectTags.length
            ? projectTags.map((item) => html`<span key=${`${row.developerId}-${item.projectName}`} className="project-tag">${item.projectName}</span>`)
            : html`<span className="project-tag project-tag-muted">Unassigned</span>`}
        </div>
        <a className="card-action" href="/developer-detail.html">View Details</a>
      </div>
    </div>
  `;
}

function DashboardPage() {
  const [state, setState] = useState({
    loading: true,
    error: "",
    banner: null,
    rows: [],
    dashboard: null,
    statusFilter: "ALL",
    projectFilter: "ALL"
  });

  useEffect(() => {
    let ignore = false;
    async function load() {
      try {
        const [rows, dashboard] = await Promise.all([api("/api/capacity"), api("/api/dashboard-summary")]);
        if (ignore) return;
        const overloaded = dashboard?.overloadedCount ?? rows.filter((item) => item.status === "OVERLOADED").length;
        const onLeave = dashboard?.onLeaveCount ?? rows.filter((item) => item.status === "ON_LEAVE").length;
        setState({
          loading: false,
          error: "",
          rows,
          dashboard,
          statusFilter: "ALL",
          projectFilter: "ALL",
          banner: {
            message: rows.length
              ? `${overloaded} developer${overloaded === 1 ? "" : "s"} overloaded and ${onLeave} on leave in the current planning window.`
              : "No capacity data is available yet. Add assignments to populate the dashboard.",
            tone: rows.length ? (overloaded || onLeave ? "warning" : "info") : "warning"
          }
        });
      } catch (error) {
        if (ignore) return;
        setState((current) => ({
          ...current,
          loading: false,
          error: error.message,
          rows: [],
          dashboard: null,
          banner: { message: `Some data could not be loaded: ${error.message}`, tone: "error" }
        }));
      }
    }
    load();
    return () => { ignore = true; };
  }, []);

  const dashboard = state.dashboard;
  const statusOptions = ["ALL", "AVAILABLE", "BUSY", "OVERLOADED", "ON_LEAVE"];
  const allProjects = [...new Set(state.rows.flatMap((row) => (row.projectAllocations || []).map((item) => item.projectName)).filter(Boolean))].sort();
  const filteredRows = state.rows.filter((row) => {
    const statusMatch = state.statusFilter === "ALL" || row.status === state.statusFilter;
    const projectMatch = state.projectFilter === "ALL" || (row.projectAllocations || []).some((item) => item.projectName === state.projectFilter);
    return statusMatch && projectMatch;
  });

  const totalDevelopers = dashboard?.totalDevelopers ?? state.rows.length;
  const available = dashboard?.availableCount ?? state.rows.filter((item) => item.status === "AVAILABLE").length;
  const openTickets = dashboard?.totalOpenTickets ?? state.rows.reduce((sum, item) => sum + (item.openTickets ?? 0), 0);
  const onLeave = dashboard?.onLeaveCount ?? state.rows.filter((item) => item.status === "ON_LEAVE").length;
  const overloaded = dashboard?.overloadedCount ?? state.rows.filter((item) => item.status === "OVERLOADED").length;
  const lastSyncText = dashboard ? `${filteredRows.length} of ${state.rows.length} shown · ${openTickets} total open tickets` : `${filteredRows.length} developers shown`;
  const badgeText = dashboard?.sprintName ? `${planningLabel(dashboard)} · ${planningRangeText(dashboard.sprintStart, dashboard.sprintEnd)}` : "Current planning window";

  return html`
    <${PageShell}
      title="Team Dashboard"
      subtitle=${dashboard ? `${humanize(dashboard.planningMode || "SPRINT")} delivery · ${totalDevelopers} developers` : "Loading team capacity..."}
      actions=${html`
        <span className="chip">${badgeText}</span>
        <a className="btn btn-secondary" href="/settings.html">Export</a>
        <a className="btn btn-primary" href="/developer-detail.html">Add Developer</a>
      `}
      banner=${state.banner}
    >
      ${state.rows.length
        ? html`
            <div className="alert-banner">
              <span className="alert-icon">⚠️</span>
              <span><strong>${overloaded} developer${overloaded === 1 ? "" : "s"}</strong> are overloaded and <strong>${onLeave} developer${onLeave === 1 ? "" : "s"}</strong> are on leave in the current planning window. Review allocations before assigning new work.</span>
            </div>
          `
        : null}

      <div className="filters">
        <span className="filter-label">View by:</span>
        ${statusOptions.map((status) => html`
          <button
            key=${status}
            className=${`filter-chip ${state.statusFilter === status ? "active" : ""}`}
            onClick=${() => setState((current) => ({ ...current, statusFilter: status }))}
          >
            ${status === "ALL" ? "All" : humanize(status)}
          </button>
        `)}
        <div className="filter-divider"></div>
        <span className="filter-label">Project:</span>
        <select
          className="filter-select"
          value=${state.projectFilter}
          onChange=${(event) => setState((current) => ({ ...current, projectFilter: event.target.value }))}
        >
          <option value="ALL">All Projects</option>
          ${allProjects.map((project) => html`<option key=${project} value=${project}>${project}</option>`)}
        </select>
        <select className="filter-select" disabled>
          <option>${dashboard?.usingFallbackWindow ? "Current Window" : "This Sprint"}</option>
        </select>
      </div>

      <div className="stats-grid">
        <${DashboardStatCard}
          icon="👥"
          tone="blue"
          value=${totalDevelopers || "-"}
          label="Total Developers"
          subtext=${dashboard ? `${dashboard.busyCount ?? 0} busy · ${available} available` : "Team size in this view"}
        />
        <${DashboardStatCard}
          icon="✅"
          tone="green"
          value=${available || "-"}
          label="Available This Window"
          subtext=${totalDevelopers ? `${Math.round((available / totalDevelopers) * 100)}% of team` : "No capacity data yet"}
        />
        <${DashboardStatCard}
          icon="🎫"
          tone="orange"
          value=${openTickets || "-"}
          label="Open Jira Tickets"
          subtext=${dashboard?.totalBlockedTickets ? `${dashboard.totalBlockedTickets} blocked need triage` : "No blocked ticket pressure"}
        />
        <${DashboardStatCard}
          icon="🏖️"
          tone="red"
          value=${onLeave || "-"}
          label="On Leave This Window"
          subtext=${totalDevelopers ? `${Math.round((onLeave / totalDevelopers) * 100)}% of team` : "No leave data yet"}
        />
      </div>

      <div className="section-header">
        <div>
          <div className="section-title">Developer Bandwidth</div>
          <div className="section-sub">${lastSyncText}</div>
        </div>
        <div className="view-toggle">
          <button className="toggle-btn active">⊞ Grid</button>
          <button className="toggle-btn">≡ List</button>
        </div>
      </div>

      <div className="dev-grid">
        ${filteredRows.length
          ? filteredRows.map((row) => html`<${DeveloperDashboardCard} key=${row.developerId} row=${row} />`)
          : html`<div className="notice">No developers match the selected filters.</div>`}
      </div>

      ${state.error ? html`<div className="error-box">Failed to load data: ${state.error}</div>` : null}
    </${PageShell}>
  `;
}

function DeveloperPage() {
  const [devs, setDevs] = useState([]);
  const [bandwidth, setBandwidth] = useState([]);
  const [selectedId, setSelectedId] = useState("");
  const [assignments, setAssignments] = useState([]);
  const [leaves, setLeaves] = useState([]);
  const [tickets, setTickets] = useState([]);
  const [banner, setBanner] = useState(null);
  const [error, setError] = useState("");
  const [ticketStatusFilter, setTicketStatusFilter] = useState("ALL");
  const [ticketProjectFilter, setTicketProjectFilter] = useState("ALL");
  const [ticketQuery, setTicketQuery] = useState("");

  useEffect(() => {
    let ignore = false;
    async function load() {
      try {
        const [devList, bwList] = await Promise.allSettled([api("/api/developers"), api("/api/capacity")]);
        if (ignore) return;
        const nextDevs = devList.status === "fulfilled" ? devList.value : [];
        const nextBw = bwList.status === "fulfilled" ? bwList.value : [];
        setDevs(nextDevs);
        setBandwidth(nextBw);
        setSelectedId(nextDevs[0]?.id ? String(nextDevs[0].id) : "");
        if (!nextDevs.length) {
          setBanner({ message: "No developers are configured yet. Add developers to use the detail view.", tone: "warning" });
        } else {
          setBanner({ message: "Developer detail is live. Select a teammate to inspect allocations and ticket load.", tone: "info" });
        }
      } catch (loadError) {
        if (ignore) return;
        setError(loadError.message);
        setBanner({ message: `Some data could not be loaded: ${loadError.message}`, tone: "error" });
      }
    }
    load();
    return () => { ignore = true; };
  }, []);

  useEffect(() => {
    let ignore = false;
    async function loadDeveloperContext() {
      if (!selectedId) {
        setAssignments([]);
        setLeaves([]);
        setTickets([]);
        return;
      }
      try {
        const [assignmentRows, leaveRows, ticketRows] = await Promise.all([
          api(`/api/project-allocations?developerId=${selectedId}`),
          api(`/api/leave-requests?developerId=${selectedId}`),
          api(`/api/jira-tickets?developerId=${selectedId}`)
        ]);
        if (!ignore) {
          setAssignments(assignmentRows);
          setLeaves(leaveRows);
          setTickets(ticketRows);
        }
      } catch {
        if (!ignore) {
          setAssignments([]);
          setLeaves([]);
          setTickets([]);
        }
      }
    }
    loadDeveloperContext();
    return () => { ignore = true; };
  }, [selectedId]);

  const dev = devs.find((item) => String(item.id) === String(selectedId));
  const row = bandwidth.find((item) => String(item.developerId) === String(selectedId));
  const statusText = row ? humanize(row.status) : "No planning status";
  const totalBookedHours = dev && row ? Math.round((dev.weeklyCapacityHours || 0) * ((row.totalAllocationPct || 0) / 100)) : 0;
  const availableHours = dev ? Math.max((dev.weeklyCapacityHours || 0) - totalBookedHours, 0) : 0;
  const recentTrend = Array.from({ length: 8 }, (_, index) => {
    const current = row?.effectiveBandwidthPct ?? 0;
    const offset = (index - 7) * 4;
    return Math.max(0, Math.min(100, Math.round(current + offset)));
  });
  const leaveHistory = [...leaves].sort((a, b) => String(b.startDate).localeCompare(String(a.startDate))).slice(0, 5);
  const ticketProjects = [...new Set(tickets.map((ticket) => ticket.projectName || ticket.projectKey).filter(Boolean))].sort();
  const ticketStatuses = [...new Set(tickets.map((ticket) => ticket.rawStatus || humanize(ticket.status)).filter(Boolean))];
  const filteredTickets = tickets.filter((ticket) => {
    const statusValue = ticket.rawStatus || humanize(ticket.status);
    const projectValue = ticket.projectName || ticket.projectKey;
    const statusMatch = ticketStatusFilter === "ALL" || statusValue === ticketStatusFilter;
    const projectMatch = ticketProjectFilter === "ALL" || projectValue === ticketProjectFilter;
    const text = `${ticket.ticketKey || ""} ${ticket.summary || ""}`.toLowerCase();
    const searchMatch = !ticketQuery || text.includes(ticketQuery.toLowerCase());
    return statusMatch && projectMatch && searchMatch;
  });
  const skillTags = [
    humanize(dev?.role),
    ...(assignments.map((assignment) => assignment.projectName).filter(Boolean).slice(0, 4))
  ].filter(Boolean);

  return html`
    <${PageShell}
      title=${dev ? dev.name : "Developer Detail"}
      subtitle=${dev ? `${humanize(dev.role)} · ${dev.email}` : "Choose a developer to view current capacity and allocations."}
      leading=${html`<a href="/team-dashboard.html" className="back-btn">← Back</a>`}
      actions=${html`
        <label style=${{ fontSize: "12px", color: "#6b7280" }}>
          Developer
          <select value=${selectedId} onChange=${(event) => setSelectedId(event.target.value)} style=${{ minWidth: "220px", marginTop: "6px" }}>
            ${devs.length
              ? devs.map((item) => html`<option key=${item.id} value=${item.id}>${item.name}</option>`)
              : html`<option value="">No developers available</option>`}
          </select>
        </label>
        <button className="btn btn-danger" type="button">Mark On Leave</button>
        <a className="btn btn-secondary" href="/team-dashboard.html">Export</a>
        <a className="btn btn-primary" href="/capacity-planner.html">Edit Developer</a>
      `}
      banner=${banner}
    >
      <section className="detail-profile-header">
        <div className="detail-profile-avatar">${initials(dev?.name)}</div>
        <div className="detail-profile-info">
          <div className="detail-profile-name">${dev?.name || "-"}</div>
          <div className="detail-profile-role">${dev ? `${humanize(dev.role)} · ${dev.email}` : "No developer selected"}</div>
          <div className="detail-profile-tags">
            ${skillTags.length
              ? skillTags.map((tag) => html`<span key=${tag} className="detail-profile-tag">${tag}</span>`)
              : html`<span className="detail-profile-tag">No project context yet</span>`}
          </div>
        </div>
        <div className="detail-profile-stats">
          <div className="detail-p-stat">
            <div className="detail-p-stat-val">${dev ? `${dev.weeklyCapacityHours}h` : "-"}</div>
            <div className="detail-p-stat-lbl">Weekly Capacity</div>
          </div>
          <div className="detail-p-stat">
            <div className="detail-p-stat-val">${dev ? `${totalBookedHours}h` : "-"}</div>
            <div className="detail-p-stat-lbl">Currently Booked</div>
          </div>
          <div className="detail-p-stat">
            <div className="detail-p-stat-val">${dev ? `${availableHours}h` : "-"}</div>
            <div className="detail-p-stat-lbl">Available</div>
          </div>
        </div>
        <span className=${`detail-status-badge ${dashboardStatusClass(row?.status)}`}>${statusText}</span>
      </section>

      <div className="detail-metrics-strip">
        <div className="detail-metric-card">
          <div className="detail-metric-card-val" style=${{ color: row?.status === "OVERLOADED" ? "#dc2626" : row?.status === "BUSY" ? "#d97706" : "#111827" }}>
            ${row ? `${row.effectiveBandwidthPct}%` : "-"}
          </div>
          <div className="detail-metric-card-lbl">Bandwidth Used</div>
          <div className="detail-metric-card-sub">${row ? `${row.totalAllocationPct ?? 0}% allocated across active work` : "No planning data yet"}</div>
        </div>
        <div className="detail-metric-card">
          <div className="detail-metric-card-val">${row ? row.openTickets : "-"}</div>
          <div className="detail-metric-card-lbl">Open Tickets</div>
          <div className="detail-metric-card-sub">${planningLabel({ planningMode: "SPRINT", sprintName: "Current Window" })}</div>
        </div>
        <div className="detail-metric-card">
          <div className="detail-metric-card-val">${row ? row.totalStoryPoints ?? 0 : "-"}</div>
          <div className="detail-metric-card-lbl">Story Points</div>
          <div className="detail-metric-card-sub">Assigned to current workload</div>
        </div>
        <div className="detail-metric-card">
          <div className="detail-metric-card-val">${row ? row.blockedTickets : "-"}</div>
          <div className="detail-metric-card-lbl">Blocked Tickets</div>
          <div className="detail-metric-card-sub">${row?.blockedTickets ? "Needs attention" : "No blockers right now"}</div>
        </div>
        <div className="detail-metric-card">
          <div className="detail-metric-card-val">${row ? row.leaveDaysInPeriod ?? 0 : "-"}</div>
          <div className="detail-metric-card-lbl">Leave Days</div>
          <div className="detail-metric-card-sub">In current planning window</div>
        </div>
      </div>

      <div className="detail-two-col">
        <div>
          <section className="card">
            <div className="card-header">
              <span className="card-title">Bandwidth Timeline (Last 8 Weeks)</span>
              <select className="filter-select" disabled>
                <option>Last 8 Weeks</option>
              </select>
            </div>
            <div className="card-body">
              <div className="detail-week-chart">
                ${recentTrend.map((value, index) => {
                  const tone = bandwidthTone(value);
                  return html`
                    <div key=${index} className="detail-week-col">
                      <div className="detail-week-pct">${value}%</div>
                      <div className="detail-week-bar-wrap">
                        <div className=${`detail-week-bar detail-wb-${tone}`} style=${{ height: `${Math.max(value, 4)}%` }}></div>
                      </div>
                      <div className="detail-week-label">W${index + 1}${index === recentTrend.length - 1 ? " ←" : ""}</div>
                    </div>
                  `;
                })}
              </div>
              <div className="detail-week-legend">
                <span><span className="detail-legend-box detail-wb-low"></span> ≤ 70% Healthy</span>
                <span><span className="detail-legend-box detail-wb-medium"></span> 70–85% Busy</span>
                <span><span className="detail-legend-box detail-wb-high"></span> >85% Overloaded</span>
              </div>
            </div>
          </section>

          <section className="card">
            <div className="card-header">
              <span className="card-title">Jira Tickets</span>
              <span className="detail-sp-count">${tickets.length} tickets · ${row?.totalStoryPoints ?? 0} story pts</span>
            </div>
            <div className="card-body" style=${{ paddingTop: 0 }}>
              <div className="detail-tabs">
                ${["ALL", ...ticketStatuses].map((status) => {
                  const count = status === "ALL"
                    ? tickets.length
                    : tickets.filter((ticket) => (ticket.rawStatus || humanize(ticket.status)) === status).length;
                  const label = status === "ALL" ? "All" : status;
                  return html`
                    <button
                      key=${status}
                      type="button"
                      className=${`detail-tab ${ticketStatusFilter === status ? "active" : ""}`}
                      onClick=${() => setTicketStatusFilter(status)}
                    >
                      ${label} (${count})
                    </button>
                  `;
                })}
              </div>
              <div className="detail-filter-row">
                <input
                  className="search-input"
                  placeholder="Search tickets..."
                  value=${ticketQuery}
                  onInput=${(event) => setTicketQuery(event.target.value)}
                />
                <select className="filter-select" value=${ticketProjectFilter} onChange=${(event) => setTicketProjectFilter(event.target.value)}>
                  <option value="ALL">All Projects</option>
                  ${ticketProjects.map((project) => html`<option key=${project} value=${project}>${project}</option>`)}
                </select>
              </div>
              <table className="detail-jira-table">
                <thead>
                  <tr>
                    <th>Key</th>
                    <th>Summary</th>
                    <th>Status</th>
                    <th>Priority</th>
                    <th>SP</th>
                    <th>Project</th>
                  </tr>
                </thead>
                <tbody>
                  ${filteredTickets.length
                    ? filteredTickets.map((ticket) => html`
                        <tr key=${ticket.id || ticket.ticketKey}>
                          <td>
                            ${ticket.ticketUrl
                              ? html`<a className="ticket-key" href=${ticket.ticketUrl} target="_blank" rel="noreferrer">${ticket.ticketKey}</a>`
                              : html`<span className="ticket-key">${ticket.ticketKey}</span>`}
                          </td>
                          <td className="ticket-summary">${ticket.summary || "-"}</td>
                          <td><span className=${`status-pill ${ticket.status === "IN_PROGRESS" ? "sp-progress" : ticket.status === "IN_REVIEW" ? "sp-review" : ticket.status === "BLOCKED" ? "sp-blocked" : ticket.status === "DONE" ? "sp-done" : "sp-todo"}`}>${ticket.rawStatus || humanize(ticket.status)}</span></td>
                          <td><span className=${`priority-dot ${ticket.priority === "HIGH" || ticket.priority === "HIGHEST" ? "pr-high" : ticket.priority === "LOW" || ticket.priority === "LOWEST" ? "pr-low" : "pr-medium"}`}></span>${humanize(ticket.priority)}</td>
                          <td><strong>${ticket.storyPoints ?? 0}</strong></td>
                          <td>${ticket.projectName || ticket.projectKey || "-"}</td>
                        </tr>
                      `)
                    : html`<tr><td colSpan="6" className="notice">No Jira tickets available for this developer.</td></tr>`}
                </tbody>
              </table>
            </div>
          </section>
        </div>

        <div className="detail-sidebar">
          <section className="card">
            <div className="card-header">
              <span className="card-title">Allocation by Project</span>
            </div>
            <div className="card-body">
              ${assignments.length
                ? assignments.map((assignment, index) => html`
                    <div key=${`${assignment.projectName}-${assignment.startDate}`} className="detail-alloc-row">
                      <span className="detail-alloc-project">${assignment.projectName}</span>
                      <div className="detail-alloc-bar-wrap">
                        <div className=${`detail-alloc-bar detail-ab-${(index % 3) + 1}`} style=${{ width: `${Math.min(100, assignment.allocationPct || 0)}%` }}></div>
                      </div>
                      <span className="detail-alloc-pct">${assignment.allocationPct || 0}%</span>
                    </div>
                  `)
                : html`<div className="notice">No allocations yet.</div>`}
              ${assignments.length
                ? html`<div className="detail-alloc-total">Total: ${row?.totalAllocationPct ?? 0}%${(row?.totalAllocationPct ?? 0) > 85 ? " · Above recommended" : ""}</div>`
                : null}
            </div>
          </section>

          <section className="card">
            <div className="card-header">
              <span className="card-title">Leave History</span>
              <a href="/leave-calendar.html" className="card-action">Calendar →</a>
            </div>
            <div className="card-body">
              ${leaveHistory.length
                ? leaveHistory.map((leave) => html`
                    <div key=${leave.id} className="detail-leave-item">
                      <div><span className="detail-leave-type">${humanize(leave.leaveType)}</span></div>
                      <div>
                        <div className="detail-leave-dates">${formatDate(leave.startDate)}${leave.endDate && leave.endDate !== leave.startDate ? ` – ${formatDate(leave.endDate)}` : ""}</div>
                        <div className="detail-leave-dur">${leave.durationDays ?? 0} day${leave.durationDays === 1 ? "" : "s"}</div>
                      </div>
                      <span className="detail-leave-status">${humanize(leave.status)}</span>
                    </div>
                  `)
                : html`<div className="notice">No leave history available.</div>`}
              <div style=${{ paddingTop: "12px", borderTop: "1px solid #f3f4f6", marginTop: "8px" }}>
                <a className="btn btn-secondary" href="/leave-calendar.html" style=${{ width: "100%", justifyContent: "center" }}>+ Apply Leave</a>
              </div>
            </div>
          </section>

          <section className="card">
            <div className="card-header"><span className="card-title">Contact & Info</span></div>
            <div className="card-body detail-info-list">
              <div className="detail-info-row"><span>Email</span><span>${dev?.email || "N/A"}</span></div>
              <div className="detail-info-row"><span>Role</span><span>${dev ? humanize(dev.role) : "-"}</span></div>
              <div className="detail-info-row"><span>Jira Username</span><span>${dev?.jiraUsername || "N/A"}</span></div>
              <div className="detail-info-row"><span>Last Ticket Sync</span><span>${tickets[0]?.lastSyncedAt ? formatDateTime(tickets[0].lastSyncedAt) : "No sync data"}</span></div>
              <div className="detail-info-row"><span>Leave Balance</span><span>${Math.max(0, 20 - leaveHistory.reduce((sum, leave) => sum + (leave.durationDays || 0), 0))} days remaining</span></div>
            </div>
          </section>
        </div>
      </div>

      ${error ? html`<div className="error-box">Failed to load data: ${error}</div>` : null}
    </${PageShell}>
  `;
}

function LeavesPage() {
  const [pending, setPending] = useState([]);
  const [leaves, setLeaves] = useState([]);
  const [devs, setDevs] = useState([]);
  const [currentMonth, setCurrentMonth] = useState(startOfMonth(new Date()));
  const [selectedDeveloperId, setSelectedDeveloperId] = useState("");
  const [viewMode, setViewMode] = useState("MONTH");
  const [showForm, setShowForm] = useState(false);
  const [banner, setBanner] = useState(null);
  const [error, setError] = useState("");

  async function load(monthValue = currentMonth) {
    const monthStart = startOfMonth(monthValue);
    const monthEnd = endOfMonth(monthValue);
    try {
      const [pendingRows, devRows, leaveRows] = await Promise.all([
        api("/api/leave-requests/pending-approvals"),
        api("/api/developers"),
        api(`/api/leave-requests?startDate=${toIsoDate(monthStart)}&endDate=${toIsoDate(monthEnd)}`)
      ]);
      setPending(pendingRows);
      setDevs(devRows);
      setLeaves(leaveRows);
      setBanner({
        message: pendingRows.length
          ? "Pending leave requests need review. Use the right rail to keep the calendar current."
          : "Leave calendar is up to date. Use Mark Leave to capture the next absence.",
        tone: pendingRows.length ? "warning" : "info"
      });
      setError("");
    } catch (loadError) {
      setError(loadError.message);
      setBanner({ message: `Some data could not be loaded: ${loadError.message}`, tone: "error" });
    }
  }

  useEffect(() => {
    load(currentMonth);
  }, [currentMonth]);

  async function updateLeave(id, action) {
    try {
      await api(`/api/leave-requests/${id}/${action}`, { method: "POST" });
      setBanner({ message: "Leave request updated successfully.", tone: "info" });
      await load(currentMonth);
    } catch (updateError) {
      setError(`Failed to update leave request: ${updateError.message}`);
      setBanner({ message: `Unable to update leave request: ${updateError.message}`, tone: "error" });
    }
  }

  async function submitLeave(event) {
    event.preventDefault();
    const formData = Object.fromEntries(new FormData(event.currentTarget).entries());
    try {
      await api("/api/leave-requests", {
        method: "POST",
        body: JSON.stringify({
          developerId: Number(formData.developerId),
          leaveType: formData.leaveType,
          startDate: formData.startDate,
          endDate: formData.endDate,
          notes: formData.notes
        })
      });
      event.currentTarget.reset();
      setShowForm(false);
      setBanner({ message: "Leave request submitted. The queue has been refreshed.", tone: "info" });
      await load(currentMonth);
    } catch (submitError) {
      setError(`Failed to submit leave request: ${submitError.message}`);
      setBanner({ message: `Unable to submit leave request: ${submitError.message}`, tone: "error" });
    }
  }

  const today = new Date();
  const monthStart = startOfMonth(currentMonth);
  const monthEnd = endOfMonth(currentMonth);
  const monthDays = rangeDays(monthStart, monthEnd);
  const todayIndexInMonth = monthDays.findIndex((day) => isSameDay(day, today));
  const anchorIndex = todayIndexInMonth >= 0 ? todayIndexInMonth : 0;
  const sprintStartIndex = Math.max(0, Math.min(anchorIndex, Math.max(0, monthDays.length - 14)));
  const weekStartIndex = Math.max(0, Math.min(anchorIndex, Math.max(0, monthDays.length - 7)));
  const windowDays = viewMode === "WEEK"
    ? monthDays.slice(weekStartIndex, weekStartIndex + 7)
    : viewMode === "SPRINT"
      ? monthDays.slice(sprintStartIndex, sprintStartIndex + 14)
      : monthDays;
  const visibleStart = windowDays[0] || monthStart;
  const visibleEnd = windowDays[windowDays.length - 1] || monthEnd;
  const visibleLeaves = leaves.filter((leave) => {
    const leaveStart = new Date(leave.startDate);
    const leaveEnd = new Date(leave.endDate);
    const developerMatch = !selectedDeveloperId || String(leave.developerId) === selectedDeveloperId;
    return developerMatch && datesOverlap(leaveStart, leaveEnd, visibleStart, visibleEnd);
  });
  const visibleDevelopers = devs.filter((dev) => !selectedDeveloperId || String(dev.id) === selectedDeveloperId);
  const leaveByDeveloper = new Map();
  visibleLeaves.forEach((leave) => {
    const list = leaveByDeveloper.get(leave.developerId) || [];
    list.push(leave);
    leaveByDeveloper.set(leave.developerId, list);
  });
  const onLeaveTodayIds = new Set(
    leaves
      .filter((leave) => leave.status !== "REJECTED")
      .filter((leave) => datesOverlap(new Date(leave.startDate), new Date(leave.endDate), today, today))
      .map((leave) => leave.developerId)
  );
  const leaveEventsThisMonth = leaves.filter((leave) => leave.status !== "REJECTED").length;
  const publicHolidayCount = leaves.filter((leave) => leave.leaveType === "PUBLIC_HOLIDAY" && leave.status !== "REJECTED").length;
  const onLeaveToday = onLeaveTodayIds.size;
  const availableToday = Math.max(0, devs.length - onLeaveToday);

  function exportMonthData() {
    const rows = [["Developer", "Type", "Status", "Start Date", "End Date", "Duration Days"]];
    visibleLeaves.forEach((leave) => {
      rows.push([
        leave.developerName,
        leaveTypeLabel(leave.leaveType),
        humanize(leave.status),
        leave.startDate,
        leave.endDate,
        String(leave.durationDays ?? "")
      ]);
    });
    const csv = rows.map((row) => row.map((value) => `"${String(value ?? "").replaceAll("\"", "\"\"")}"`).join(",")).join("\n");
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `bandviz-leaves-${toIsoDate(visibleStart)}-${toIsoDate(visibleEnd)}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  }

  return html`
    <${PageShell}
      title="Leave Calendar"
      subtitle=${`Team availability · ${formatMonthLabel(currentMonth)}`}
      actions=${html`
        <button className="btn btn-secondary" onClick=${exportMonthData}>⬇ Export</button>
        <button className="btn btn-primary" onClick=${() => setShowForm((current) => !current)}>
          ${showForm ? "Hide Form" : "+ Mark Leave"}
        </button>
      `}
      banner=${banner}
    >
      <div className="mini-stats">
        <div className="mini-stat">
          <span className="ms-icon">🏖️</span>
          <div><div className="ms-val">${onLeaveToday}</div><div className="ms-lbl">On Leave Today</div></div>
        </div>
        <div className="mini-stat">
          <span className="ms-icon">📋</span>
          <div><div className="ms-val">${pending.length}</div><div className="ms-lbl">Pending Approvals</div></div>
        </div>
        <div className="mini-stat">
          <span className="ms-icon">📅</span>
          <div><div className="ms-val">${leaveEventsThisMonth}</div><div className="ms-lbl">Leave Events This ${viewMode === "WEEK" ? "View" : "Month"}</div></div>
        </div>
        <div className="mini-stat">
          <span className="ms-icon">🏛️</span>
          <div><div className="ms-val">${publicHolidayCount}</div><div className="ms-lbl">Public Holidays</div></div>
        </div>
        <div className="mini-stat mini-stat-push">
          <span className="ms-icon">✅</span>
          <div><div className="ms-val">${availableToday}</div><div className="ms-lbl">Available Today</div></div>
        </div>
      </div>

      <div className="view-header">
        <div className="month-nav">
          <button className="month-btn" onClick=${() => setCurrentMonth((value) => addMonths(value, -1))}>←</button>
          <span className="month-title">${formatMonthLabel(currentMonth)}</span>
          <button className="month-btn" onClick=${() => setCurrentMonth((value) => addMonths(value, 1))}>→</button>
          <button className="btn btn-secondary leave-today-btn" onClick=${() => setCurrentMonth(startOfMonth(new Date()))}>Today</button>
        </div>
        <div className="view-controls">
          <select value=${selectedDeveloperId} onChange=${(event) => setSelectedDeveloperId(event.target.value)}>
            <option value="">All Developers</option>
            ${devs.map((dev) => html`<option key=${dev.id} value=${String(dev.id)}>${dev.name}</option>`)}
          </select>
          <div className="view-toggle">
            ${["MONTH", "SPRINT", "WEEK"].map((mode) => html`
              <button
                key=${mode}
                className=${`vt-btn ${viewMode === mode ? "active" : ""}`}
                onClick=${() => setViewMode(mode)}
              >
                ${mode === "SPRINT" ? "Sprint" : humanize(mode)}
              </button>
            `)}
          </div>
        </div>
      </div>

      <div className="cal-wrap">
        <div className="gantt-header" style=${{ gridTemplateColumns: `200px repeat(${windowDays.length}, minmax(32px, 1fr))` }}>
          <div className="gantt-name-col">Developer</div>
          ${windowDays.map((day) => {
            const weekend = day.getDay() === 0 || day.getDay() === 6;
            const todayClass = isSameDay(day, today) ? "today" : "";
            return html`
              <div key=${day.toISOString()} className=${`gantt-day-header ${weekend ? "weekend" : ""} ${todayClass}`.trim()}>
                <div className="gantt-day-num">${day.getDate()}</div>
                <div className="gantt-day-name">${isSameDay(day, today) ? "Today" : day.toLocaleDateString("en-US", { weekday: "short" })}</div>
              </div>
            `;
          })}
        </div>

        ${visibleDevelopers.length
          ? visibleDevelopers.map((dev) => {
              const devLeaves = leaveByDeveloper.get(dev.id) || [];
              return html`
                <div key=${dev.id} className="gantt-row" style=${{ gridTemplateColumns: `200px repeat(${windowDays.length}, minmax(32px, 1fr))` }}>
                  <div className="gantt-name">
                    <div className="gantt-avatar">${initials(dev.name)}</div>
                    <div>
                      <div className="gantt-dev-name">${dev.name}</div>
                      <div className="gantt-dev-role">${humanize(dev.role)}</div>
                    </div>
                  </div>
                  ${windowDays.map((day) => {
                    const weekend = day.getDay() === 0 || day.getDay() === 6;
                    const todayClass = isSameDay(day, today) ? "today" : "";
                    const leave = devLeaves.find((item) => {
                      const leaveStart = new Date(item.startDate);
                      const leaveEnd = new Date(item.endDate);
                      return datesOverlap(leaveStart, leaveEnd, day, day) && item.status !== "REJECTED";
                    });
                    return html`
                      <div key=${`${dev.id}-${day.toISOString()}`} className=${`gantt-cell ${weekend ? "weekend" : ""} ${todayClass}`.trim()}>
                        ${leave ? html`<div className=${`leave-block ${leaveTypeClass(leave.leaveType)}`}>${leaveTypeLabel(leave.leaveType)}</div>` : null}
                      </div>
                    `;
                  })}
                </div>
              `;
            })
          : html`<div className="calendar-empty">No developers available for this view.</div>`}

        <div className="legend">
          <div className="legend-item"><div className="legend-dot legend-planned"></div> Planned Leave</div>
          <div className="legend-item"><div className="legend-dot legend-sick"></div> Sick Leave</div>
          <div className="legend-item"><div className="legend-dot legend-comp"></div> Comp Off</div>
          <div className="legend-item"><div className="legend-dot legend-public"></div> Public Holiday</div>
          <div className="legend-item"><div className="legend-dot legend-today"></div> Today</div>
        </div>
      </div>

      <div className="grid split-wide">
        <section className="leave-requests">
          <div className="lr-header">
            <span className="lr-title">Pending Leave Requests</span>
            <span className="pending-badge">${pending.length} Pending</span>
          </div>
          ${pending.length
            ? pending.map((leave) => html`
                <div key=${leave.id} className="lr-row">
                  <div className="lr-avatar">${initials(leave.developerName)}</div>
                  <div className="lr-info">
                    <div className="lr-name">${leave.developerName}</div>
                    <div className="lr-dates">
                      ${leave.startDate === leave.endDate
                        ? `${formatDate(leave.startDate)} · ${durationLabel(leave.durationDays)}`
                        : `${formatDate(leave.startDate)} - ${formatDate(leave.endDate)} · ${durationLabel(leave.durationDays)}`}
                    </div>
                  </div>
                  <span className=${`lr-type ${leaveTypeClass(leave.leaveType)}`}>${leaveTypeLabel(leave.leaveType)}</span>
                  <div className="lr-actions">
                    <button className="btn-approve" onClick=${() => updateLeave(leave.id, "approve")}>✓ Approve</button>
                    <button className="btn-reject" onClick=${() => updateLeave(leave.id, "reject")}>✕ Reject</button>
                  </div>
                </div>
              `)
            : html`<div className="leave-empty-state">No pending approvals. The leave calendar is fully caught up.</div>`}
        </section>

        ${showForm ? html`
          <${SectionCard} title="Mark Leave" subtitle="Capture a new leave event without leaving the calendar">
            <form className="stack" onSubmit=${submitLeave}>
              <div>
                <label for="developerId">Developer</label>
                <select name="developerId" id="developerId" required>
                  ${devs.length
                    ? devs.map((dev) => html`<option key=${dev.id} value=${dev.id}>${dev.name}</option>`)
                    : html`<option value="">No developers available</option>`}
                </select>
              </div>
              <div>
                <label for="leaveType">Leave Type</label>
                <select name="leaveType" id="leaveType">
                  <option value="PLANNED">Planned</option>
                  <option value="SICK">Sick</option>
                  <option value="COMP_OFF">Comp Off</option>
                  <option value="UNPAID">Unpaid</option>
                  <option value="PUBLIC_HOLIDAY">Public Holiday</option>
                </select>
              </div>
              <div className="row-2">
                <div>
                  <label for="startDate">Start Date</label>
                  <input type="date" id="startDate" name="startDate" required />
                </div>
                <div>
                  <label for="endDate">End Date</label>
                  <input type="date" id="endDate" name="endDate" required />
                </div>
              </div>
              <div>
                <label for="notes">Notes</label>
                <textarea id="notes" name="notes"></textarea>
              </div>
              <button className="btn btn-primary" type="submit">Submit Leave Request</button>
            </form>
          </${SectionCard}>
        ` : html`
          <${SectionCard} title="Leave Coverage" subtitle="A quick reading for managers checking month-level availability">
            <div className="stack">
              <div className="leave-summary-row">
                <span>Visible developers</span>
                <strong>${visibleDevelopers.length}</strong>
              </div>
              <div className="leave-summary-row">
                <span>Leave events in view</span>
                <strong>${visibleLeaves.filter((leave) => leave.status !== "REJECTED").length}</strong>
              </div>
              <div className="leave-summary-row">
                <span>Pending approvals</span>
                <strong>${pending.length}</strong>
              </div>
              <div className="leave-summary-row">
                <span>Available today</span>
                <strong>${availableToday}</strong>
              </div>
            </div>
          </${SectionCard}>
        `}
      </div>

      ${error ? html`<div className="error-box">${error}</div>` : null}
    </${PageShell}>
  `;
}

function PlannerPage() {
  const [rows, setRows] = useState([]);
  const [projects, setProjects] = useState([]);
  const [dashboard, setDashboard] = useState(null);
  const [banner, setBanner] = useState(null);
  const [error, setError] = useState("");
  const [formDefaults, setFormDefaults] = useState({ developerId: "", projectId: "" });

  async function loadBase() {
    try {
      const [capacityRows, projectRows, dashboardRow] = await Promise.all([
        api("/api/capacity"),
        api("/api/projects"),
        api("/api/dashboard-summary").catch(() => null)
      ]);
      setRows(capacityRows);
      setProjects(projectRows);
      setDashboard(dashboardRow);
      setFormDefaults({
        developerId: String(capacityRows[0]?.developerId || ""),
        projectId: String(projectRows[0]?.id || "")
      });
      setBanner({
        message: capacityRows.length && projectRows.length
          ? "Adjust allocations with the matrix below, then use the save panel to create new assignments."
          : "Add developers and projects to unlock capacity planning.",
        tone: capacityRows.length && projectRows.length ? "info" : "warning"
      });
      setError("");
    } catch (loadError) {
      setError(loadError.message);
      setBanner({ message: `Some data could not be loaded: ${loadError.message}`, tone: "error" });
    }
  }

  useEffect(() => {
    loadBase();
  }, []);

  async function createAllocation(event) {
    event.preventDefault();
    const formData = Object.fromEntries(new FormData(event.currentTarget).entries());
    try {
      await api("/api/project-allocations", {
        method: "POST",
        body: JSON.stringify({
          developerId: Number(formData.developerId),
          projectId: Number(formData.projectId),
          allocationPct: Number(formData.allocationPct),
          startDate: formData.startDate,
          endDate: formData.endDate || null
        })
      });
      event.currentTarget.reset();
      setFormDefaults((current) => ({
        developerId: current.developerId || String(rows[0]?.developerId || ""),
        projectId: current.projectId || String(projects[0]?.id || "")
      }));
      setBanner({ message: "Allocation created successfully.", tone: "info" });
      await loadBase();
    } catch (createError) {
      setError(`Failed to create allocation: ${createError.message}`);
      setBanner({ message: `Unable to create allocation: ${createError.message}`, tone: "error" });
    }
  }

  const totalTeamCapacity = rows.reduce((sum, row) => sum + (row.weeklyCapacityHours ?? 0), 0);
  const allocatedHours = rows.reduce((sum, row) => sum + Math.round((row.weeklyCapacityHours ?? 0) * ((row.totalAllocationPct ?? 0) / 100)), 0);
  const availableHours = Math.max(0, totalTeamCapacity - allocatedHours);
  const overloadedCount = rows.filter((row) => row.status === "OVERLOADED").length;
  const onLeaveCount = rows.filter((row) => row.status === "ON_LEAVE").length;
  const averageUtilization = totalTeamCapacity ? Math.round((allocatedHours / totalTeamCapacity) * 100) : 0;
  const projectTotals = new Map();
  const projectDevCounts = new Map();
  rows.forEach((row) => {
    (row.projectAllocations || []).forEach((allocation) => {
      projectTotals.set(allocation.projectName, (projectTotals.get(allocation.projectName) || 0) + (allocation.allocationPct || 0));
      if ((allocation.allocationPct || 0) > 0) {
        projectDevCounts.set(allocation.projectName, (projectDevCounts.get(allocation.projectName) || 0) + 1);
      }
    });
  });
  const visibleProjects = [...projects]
    .sort((left, right) => (projectTotals.get(right.name) || 0) - (projectTotals.get(left.name) || 0))
    .slice(0, 5);
  const alerts = [];
  rows.filter((row) => row.status === "OVERLOADED").slice(0, 2).forEach((row) => {
    alerts.push(`${row.developerName} is at ${row.totalAllocationPct ?? 0}%. Rebalance project load.`);
  });
  visibleProjects.filter((project) => !(projectTotals.get(project.name) || 0)).slice(0, 1).forEach((project) => {
    alerts.push(`${project.name} currently has 0% allocation. No owner is assigned.`);
  });
  rows.filter((row) => (row.totalAllocationPct ?? 0) < 60 && row.status !== "ON_LEAVE").slice(0, 1).forEach((row) => {
    alerts.push(`${row.developerName} still has ${100 - (row.totalAllocationPct ?? 0)}% free capacity for spillover work.`);
  });
  const matrixRows = rows;
  const projectTotalRow = visibleProjects.map((project) => projectTotals.get(project.name) || 0);

  function exportPlannerCsv() {
    const headers = ["Developer", ...visibleProjects.map((project) => project.name), "Total", "Status"];
    const dataRows = matrixRows.map((row) => {
      const allocationByProject = new Map((row.projectAllocations || []).map((item) => [item.projectName, item.allocationPct || 0]));
      return [
        row.developerName,
        ...visibleProjects.map((project) => allocationByProject.get(project.name) || 0),
        row.totalAllocationPct ?? 0,
        humanize(row.status)
      ];
    });
    downloadCsv("bandviz-capacity-planner.csv", [headers, ...dataRows]);
  }

  return html`
    <${PageShell}
      title="Bandwidth Planner"
      subtitle=${`Allocate developers across projects · ${planningLabel(dashboard)}`}
      actions=${html`
        <span className="chip">${planningLabel(dashboard)}</span>
        <button className="btn btn-secondary" onClick=${loadBase}>↺ Reset</button>
        <button className="btn btn-secondary" onClick=${exportPlannerCsv}>⬇ Export</button>
        <a className="btn btn-primary" href="#planner-create">💾 Save Plan</a>
      `}
      banner=${banner}
    >
      <div className="stats-strip">
        <div className="stat-box"><div className="sb-val">${totalTeamCapacity}h</div><div className="sb-lbl">Total Team Capacity</div><div className="sb-sub c-blue">${rows.length} devs × weekly capacity</div></div>
        <div className="stat-box"><div className="sb-val">${allocatedHours}h</div><div className="sb-lbl">Allocated Hours</div><div className="sb-sub c-orange">${averageUtilization}% utilized</div></div>
        <div className="stat-box"><div className="sb-val">${availableHours}h</div><div className="sb-lbl">Available Hours</div><div className="sb-sub c-green">Buffer remaining</div></div>
        <div className="stat-box"><div className="sb-val">${overloadedCount}</div><div className="sb-lbl">Overloaded Devs</div><div className="sb-sub c-red">Needs rebalancing</div></div>
        <div className="stat-box"><div className="sb-val">${onLeaveCount}</div><div className="sb-lbl">On Leave</div><div className="sb-sub c-orange">Capacity reduced</div></div>
      </div>

      <div className="planner-layout">
        <div className="alloc-card">
          <div className="alloc-card-header">
            <span className="alloc-card-title">Developer × Project Allocation (%)</span>
            <div className="alloc-card-sub">Review the live matrix, then use the side panel to save new assignments.</div>
          </div>
          <div className="table-wrap">
            <table className="alloc-table">
              <thead>
                <tr>
                  <th style=${{ minWidth: "210px" }}>Developer</th>
                  ${visibleProjects.map((project, index) => html`
                    <th key=${project.id} className="project-col"><span className=${`project-head ph-${(index % 5) + 1}`}>${project.name}</span></th>
                  `)}
                  <th>Total</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                ${matrixRows.map((row) => {
                  const allocationByProject = new Map((row.projectAllocations || []).map((item) => [item.projectName, item.allocationPct || 0]));
                  return html`
                    <tr key=${row.developerId} className=${row.status === "ON_LEAVE" ? "alloc-row-muted" : ""}>
                      <td>
                        <div className="dev-cell">
                          <div className="dev-mini-avatar">${initials(row.developerName)}</div>
                          <div>
                            <div className="dev-cell-name">${row.developerName}</div>
                            <div className="dev-cell-role">${humanize(row.role)}</div>
                          </div>
                        </div>
                      </td>
                      ${visibleProjects.map((project) => html`
                        <td key=${`${row.developerId}-${project.id}`} style=${{ textAlign: "center" }}>
                          <input
                            className="alloc-input"
                            value=${allocationByProject.get(project.name) || 0}
                            readOnly
                            disabled=${row.status === "ON_LEAVE"}
                          />
                        </td>
                      `)}
                      <td>
                        ${row.status === "ON_LEAVE"
                          ? html`<div className="total-cell"><span className="muted-inline">On Leave</span></div>`
                          : html`
                              <div className="total-cell">
                                <div className="total-bar-wrap"><div className=${`total-bar ${(row.totalAllocationPct ?? 0) >= 85 ? "tb-red" : (row.totalAllocationPct ?? 0) >= 70 ? "tb-yellow" : "tb-green"}`} style=${{ width: `${clamp(row.totalAllocationPct ?? 0)}%` }}></div></div>
                                <span className=${`total-pct ${(row.totalAllocationPct ?? 0) >= 85 ? "c-red" : (row.totalAllocationPct ?? 0) >= 70 ? "c-orange" : "c-green"}`}>${row.totalAllocationPct ?? 0}%</span>
                              </div>
                            `}
                      </td>
                      <td><span className=${`status-chip ${row.status === "OVERLOADED" ? "sc-over" : row.status === "BUSY" ? "sc-busy" : row.status === "ON_LEAVE" ? "sc-leave" : "sc-ok"}`}>${humanize(row.status)}</span></td>
                    </tr>
                  `;
                })}
                <tr className="alloc-totals-row">
                  <td>Project Total Alloc.</td>
                  ${projectTotalRow.map((total, index) => html`<td key=${index} style=${{ textAlign: "center" }}>${total}%</td>`)}
                  <td colSpan="2"></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div className="right-panel">
          <div className="panel-card">
            <div className="panel-card-header">Team Capacity Summary</div>
            <div className="panel-card-body">
              <div className="capacity-visual">
                <div className="capacity-value">${averageUtilization}%</div>
                <div className="capacity-sub">Average Utilization</div>
                <div className="capacity-progress"><div className="capacity-progress-fill" style=${{ width: `${averageUtilization}%` }}></div></div>
                <div className="cap-row"><div className="cap-dot cd-green"></div><span className="cap-label">Available (&lt;70%)</span><span className="cap-val">${rows.filter((row) => row.status === "AVAILABLE").length} devs</span></div>
                <div className="cap-row"><div className="cap-dot cd-yellow"></div><span className="cap-label">Busy (70–85%)</span><span className="cap-val">${rows.filter((row) => row.status === "BUSY").length} devs</span></div>
                <div className="cap-row"><div className="cap-dot cd-red"></div><span className="cap-label">Overloaded (&gt;85%)</span><span className="cap-val">${overloadedCount} devs</span></div>
                <div className="cap-row"><div className="cap-dot cd-gray"></div><span className="cap-label">On Leave</span><span className="cap-val">${onLeaveCount} devs</span></div>
              </div>
            </div>
          </div>

          <div className="panel-card">
            <div className="panel-card-header">Project Load Breakdown</div>
            <div className="panel-card-body panel-stack">
              ${visibleProjects.map((project) => {
                const total = projectTotals.get(project.name) || 0;
                return html`
                  <div key=${project.id}>
                    <div className="project-breakdown-row"><span>${project.name}</span><span>${projectDevCounts.get(project.name) || 0} devs</span></div>
                    <div className="project-breakdown-bar"><div className="project-breakdown-fill" style=${{ width: `${clamp(total)}%` }}></div></div>
                  </div>
                `;
              })}
            </div>
          </div>

          <div className="panel-card">
            <div className="panel-card-header">⚠️ Planner Alerts</div>
            <div className="panel-card-body">
              ${alerts.length
                ? alerts.map((alert, index) => html`
                    <div key=${index} className="alert-item-s">
                      <div className=${`a-dot ${index < 2 ? "ad-red" : index === 2 ? "ad-orange" : "ad-blue"}`}></div>
                      <span>${alert}</span>
                    </div>
                  `)
                : html`<div className="notice">No planner alerts right now.</div>`}
            </div>
          </div>

          <div className="panel-card" id="planner-create">
            <div className="panel-card-header">Save New Allocation</div>
            <div className="panel-card-body">
              <form className="stack" onSubmit=${createAllocation}>
                <div>
                  <label for="developerId">Developer</label>
                  <select id="developerId" name="developerId" defaultValue=${formDefaults.developerId} required>
                    ${rows.length
                      ? rows.map((row) => html`<option key=${row.developerId} value=${row.developerId}>${row.developerName}</option>`)
                      : html`<option value="">No developers available</option>`}
                  </select>
                </div>
                <div>
                  <label for="projectId">Project</label>
                  <select id="projectId" name="projectId" defaultValue=${formDefaults.projectId} required>
                    ${projects.length
                      ? projects.map((project) => html`<option key=${project.id} value=${project.id}>${project.name} (${humanize(project.deliveryMode || "HYBRID")})</option>`)
                      : html`<option value="">No projects available</option>`}
                  </select>
                </div>
                <div className="row">
                  <div>
                    <label for="allocationPct">Allocation %</label>
                    <input id="allocationPct" type="number" name="allocationPct" min="0" max="100" required />
                  </div>
                  <div>
                    <label for="startDate">Start Date</label>
                    <input id="startDate" type="date" name="startDate" required />
                  </div>
                  <div>
                    <label for="endDate">End Date</label>
                    <input id="endDate" type="date" name="endDate" />
                  </div>
                </div>
                <button className="btn btn-primary" type="submit">Create Allocation</button>
              </form>
            </div>
          </div>
        </div>
      </div>

      ${error ? html`<div className="error-box">${error}</div>` : null}
    </${PageShell}>
  `;
}

function JiraPage() {
  const [status, setStatus] = useState(null);
  const [projectMappings, setProjectMappings] = useState([]);
  const [dashboard, setDashboard] = useState(null);
  const [capacityRows, setCapacityRows] = useState([]);
  const [banner, setBanner] = useState(null);
  const [error, setError] = useState("");
  const [running, setRunning] = useState(false);

  async function load() {
    try {
      const [nextStatus, mappings, dashboardRow, capacity] = await Promise.all([
        api("/api/jira-sync/status"),
        api("/api/jira-sync/projects").catch(() => []),
        api("/api/dashboard-summary").catch(() => null),
        api("/api/capacity").catch(() => [])
      ]);
      setStatus(nextStatus);
      setProjectMappings(mappings);
      setDashboard(dashboardRow);
      setCapacityRows(capacity);
      setBanner({
        message: nextStatus.syncEnabled
          ? "Jira integration is configured. You can trigger a sync manually at any time."
          : "Jira sync is currently disabled. Configure credentials and enable sync to import issues.",
        tone: nextStatus.syncEnabled ? "info" : "warning"
      });
      setError("");
    } catch (loadError) {
      setError(loadError.message);
      setBanner({ message: `Some data could not be loaded: ${loadError.message}`, tone: "error" });
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function runSync() {
    setRunning(true);
    try {
      await api("/api/jira-sync/runs", { method: "POST" });
      setBanner({ message: "Jira sync triggered successfully. Refreshing status...", tone: "info" });
      await load();
    } catch (runError) {
      setError(`Failed to run Jira sync: ${runError.message}`);
      setBanner({ message: `Unable to run Jira sync: ${runError.message}`, tone: "error" });
    } finally {
      setRunning(false);
    }
  }

  const blockedTickets = dashboard?.totalBlockedTickets ?? 0;
  const closedTickets = dashboard?.totalClosedInWindow ?? dashboard?.totalClosedThisSprint ?? 0;
  const devsWithTickets = capacityRows.filter((row) => (row.openTickets ?? 0) > 0).length;
  const topTicketLoad = [...capacityRows]
    .filter((row) => (row.openTickets ?? 0) > 0)
    .sort((left, right) => (right.openTickets ?? 0) - (left.openTickets ?? 0))
    .slice(0, 8);
  const syncLogs = [
    status?.lastSyncedAt ? `Full sync completed · ${status.totalTickets ?? 0} synced tickets are currently in BandViz` : null,
    projectMappings.some((project) => project.syncState === "STALE") ? `${projectMappings.filter((project) => project.syncState === "STALE").length} project mapping(s) are stale and should be refreshed` : null,
    status?.syncEnabled ? `Sync is enabled for ${projectMappings.length} mapped project${projectMappings.length === 1 ? "" : "s"}` : "Sync is currently disabled in server configuration",
    blockedTickets ? `${blockedTickets} blocked Jira ticket${blockedTickets === 1 ? "" : "s"} need attention in the planning window` : null
  ].filter(Boolean);

  return html`
    <${PageShell}
      title="Jira Sync"
      subtitle="Integration status, project mapping & ticket breakdown"
      actions=${html`
        <span className="muted-inline">Last synced: <strong>${formatRelativeTime(status?.lastSyncedAt)}</strong></span>
        <a className="btn btn-secondary" href="/settings.html">⚙ Configure</a>
        <button className="btn btn-primary" onClick=${runSync} disabled=${running}>${running ? "Running..." : "↻ Sync Now"}</button>
      `}
      banner=${banner}
    >
      <div className="conn-card">
        <div className="conn-logo">J</div>
        <div className="conn-info">
          <div className="conn-name">Jira Cloud${status?.baseUrl ? ` · ${status.baseUrl.replace(/^https?:\/\//, "")}` : ""}</div>
          <div className="conn-url">${status?.baseUrl || "Configured in server settings"}</div>
          <div className="conn-meta">
            <div className="conn-meta-item">Auth: <strong>API Token</strong></div>
            <div className="conn-meta-item">Projects: <strong>${projectMappings.length} mapped</strong></div>
            <div className="conn-meta-item">Schedule: <strong>Server interval</strong></div>
            <div className="conn-meta-item">Next action: <strong>${status?.syncEnabled ? "Ready to sync" : "Enable sync first"}</strong></div>
          </div>
        </div>
        <div className=${`conn-status ${status?.syncEnabled ? "cs-connected" : "cs-disconnected"}`}>
          <div className=${`status-dot ${status?.syncEnabled ? "sd-green" : "sd-red"}`}></div>
          ${status?.syncEnabled ? "Connected" : "Disabled"}
        </div>
      </div>

      <div className="stats-strip jira-stats-strip">
        <div className="stat-card"><div className="stat-icon blue">🎫</div><div><div className="stat-value">${status?.totalTickets ?? 0}</div><div className="stat-label">Open Tickets Synced</div></div></div>
        <div className="stat-card"><div className="stat-icon green">✅</div><div><div className="stat-value">${closedTickets}</div><div className="stat-label">Closed In Window</div></div></div>
        <div className="stat-card"><div className="stat-icon orange">⛔</div><div><div className="stat-value">${blockedTickets}</div><div className="stat-label">Blocked Tickets</div></div></div>
        <div className="stat-card"><div className="stat-icon red">👤</div><div><div className="stat-value">${devsWithTickets}</div><div className="stat-label">Devs with Tickets</div></div></div>
      </div>

      <div className="grid split-wide">
        <div className="stack">
          <section className="card">
            <div className="card-header">
              <span className="card-title">Project Mapping</span>
              <a className="btn btn-secondary" href="/settings.html">+ Add Project</a>
            </div>
            <div className="table-wrap">
              <table className="map-table">
                <thead>
                  <tr>
                    <th>BandViz Project</th>
                    <th>Jira Project Key</th>
                    <th>Open Tickets</th>
                    <th>Last Synced</th>
                    <th>Status</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  ${projectMappings.length
                    ? projectMappings.map((project) => html`
                        <tr key=${project.jiraProjectKey}>
                          <td><strong>${project.projectName}</strong></td>
                          <td><span className="jira-key">${project.jiraProjectKey}</span></td>
                          <td className="ticket-count">${project.openTickets}</td>
                          <td className="last-sync">${formatRelativeTime(project.lastSyncedAt)}</td>
                          <td><span className=${`sync-status ${project.syncState === "SYNCED" ? "ss-ok" : project.syncState === "STALE" ? "ss-warn" : "ss-idle"}`}>${project.syncState === "SYNCED" ? "✓ Synced" : project.syncState === "STALE" ? "⚠ Stale" : "… Pending"}</span></td>
                          <td><a href="/settings.html" className="card-action">Edit</a></td>
                        </tr>
                      `)
                    : html`<tr><td colSpan="6" className="notice">No Jira project mappings are configured yet.</td></tr>`}
                </tbody>
              </table>
            </div>
          </section>

          <section className="card card-last">
            <div className="card-header">
              <span className="card-title">Sync Log</span>
              <span className="card-link">Last events</span>
            </div>
            <div className="card-body">
              ${syncLogs.length
                ? syncLogs.map((log, index) => html`
                    <div key=${index} className="log-item">
                      <span className="log-icon">${index === 0 ? "✅" : index === 1 ? "⚠️" : "ℹ️"}</span>
                      <span className="log-text">${log}</span>
                      <span className="log-time">${index === 0 ? formatRelativeTime(status?.lastSyncedAt) : "Live"}</span>
                    </div>
                  `)
                : html`<div className="notice">No sync activity has been recorded yet.</div>`}
            </div>
          </section>
        </div>

        <div className="stack">
          <section className="card card-last">
            <div className="card-header">
              <span className="card-title">Tickets by Developer</span>
              <span className="card-link">Open tickets</span>
            </div>
            <div className="card-body">
              ${topTicketLoad.length
                ? topTicketLoad.map((row) => html`
                    <div key=${row.developerId} className="dtb-row">
                      <div className="dtb-avatar">${initials(row.developerName)}</div>
                      <span className="dtb-name">${row.developerName}</span>
                      <div className="dtb-bar-wrap"><div className="dtb-bar" style=${{ width: `${clamp((row.openTickets ?? 0) * 12.5)}%` }}></div></div>
                      <span className="dtb-count">${row.openTickets ?? 0}</span>
                    </div>
                  `)
                : html`<div className="notice">No developer ticket breakdown is available yet.</div>`}
            </div>
          </section>

          <section className="card card-last">
            <div className="card-header"><span className="card-title">Sync Configuration</span></div>
            <div className="card-body">
              <div className="config-row"><div><div className="config-label">Sync Frequency</div><div className="config-sub">Configured on the server scheduler</div></div><span className="config-value">15 min default</span></div>
              <div className="config-row"><div><div className="config-label">Tracked Projects</div><div className="config-sub">Projects currently mapped for Jira sync</div></div><span className="config-value">${projectMappings.length}</span></div>
              <div className="config-row"><div><div className="config-label">Project Keys Source</div><div className="config-sub">Explicit configuration or active mapped projects</div></div><span className="config-value">${status?.projectKeys?.length ? "Configured" : "From BandViz projects"}</span></div>
              <div className="config-row"><div><div className="config-label">Last Sync Status</div><div className="config-sub">Most recent backend sync result</div></div><span className="config-value config-value-wide">${status?.lastSyncStatus || "Never synced"}</span></div>
              <div style=${{ marginTop: "12px" }}>
                <a className="btn btn-primary" href="/settings.html" style=${{ width: "100%", justifyContent: "center" }}>Open Configuration</a>
              </div>
            </div>
          </section>
        </div>
      </div>

      ${error ? html`<div className="error-box">${error}</div>` : null}
    </${PageShell}>
  `;
}

function SettingsPage() {
  const [state, setState] = useState({ dashboard: null, devs: [], projects: [], banner: null, error: "" });

  useEffect(() => {
    let ignore = false;
    async function load() {
      const [dashboardRes, devsRes, projectsRes] = await Promise.allSettled([
        api("/api/dashboard-summary"),
        api("/api/developers"),
        api("/api/projects")
      ]);
      if (ignore) return;
      const dashboard = dashboardRes.status === "fulfilled" ? dashboardRes.value : null;
      const devs = devsRes.status === "fulfilled" ? devsRes.value : [];
      const projects = projectsRes.status === "fulfilled" ? projectsRes.value : [];
      const modeCounts = deliveryModeSummary(projects);
      const banner = dashboard
        ? { message: `Planning is live in ${humanize(dashboard.planningMode || "SPRINT")} mode. ${modeCounts.KANBAN} Kanban, ${modeCounts.SPRINT} sprint, and ${modeCounts.HYBRID} hybrid projects are configured.`, tone: "info" }
        : { message: "Planning metadata is not available yet. Project and developer settings still load normally.", tone: "warning" };
      setState({ dashboard, devs, projects, banner, error: "" });
    }
    load().catch((loadError) => {
      if (!ignore) {
        setState({ dashboard: null, devs: [], projects: [], banner: { message: `Some data could not be loaded: ${loadError.message}`, tone: "error" }, error: loadError.message });
      }
    });
    return () => { ignore = true; };
  }, []);

  const modeCounts = deliveryModeSummary(state.projects);
  const visibleDevelopers = state.devs.slice(0, 6);
  const leaveTypes = [
    { name: "Planned Leave", note: "Managed in leave calendar", tone: "ltb-paid", color: "#bfdbfe" },
    { name: "Sick Leave", note: "Short-notice absences", tone: "ltb-paid", color: "#fed7aa" },
    { name: "Comp-off", note: "Adjusted against extra work", tone: "ltb-paid", color: "#e9d5ff" },
    { name: "Unpaid Leave", note: "No limit configured", tone: "ltb-unpaid", color: "#fef9c3" },
    { name: "Public Holiday", note: "Configured from leave entries", tone: "ltb-paid", color: "#d1fae5" }
  ];

  return html`
    <${PageShell}
      title="Settings"
      subtitle="Manage team, projects, capacity defaults and notifications"
      actions=${html`
        <button className="btn btn-secondary" onClick=${() => window.location.reload()}>↺ Reset Defaults</button>
        <a className="btn btn-primary" href="/home.html">💾 Save All</a>
      `}
      banner=${state.banner}
    >
      <div className="settings-layout">
        <nav className="settings-nav">
          <a href="#developers" className="sn-item active"><span className="sn-icon">👥</span> Developers</a>
          <a href="#projects" className="sn-item"><span className="sn-icon">📁</span> Projects</a>
          <div className="sn-divider"></div>
          <a href="#capacity" className="sn-item"><span className="sn-icon">⚡</span> Capacity Rules</a>
          <a href="#leave" className="sn-item"><span className="sn-icon">🏖️</span> Leave Types</a>
          <a href="#holidays" className="sn-item"><span className="sn-icon">🏛️</span> Public Holidays</a>
          <div className="sn-divider"></div>
          <a href="#jira" className="sn-item"><span className="sn-icon">🔗</span> Jira Integration</a>
          <a href="#notify" className="sn-item"><span className="sn-icon">🔔</span> Notifications</a>
          <div className="sn-divider"></div>
          <a href="#roles" className="sn-item"><span className="sn-icon">🔐</span> Roles & Access</a>
        </nav>

        <div className="settings-main">
          <section className="section-card" id="developers">
            <div className="sc-header">
              <div>
                <div className="sc-title">Developers</div>
                <div className="sc-sub">${state.devs.length} members · Profiles available to planning screens</div>
              </div>
              <a className="btn btn-primary" href="/developer-detail.html" style=${{ fontSize: "12px", padding: "7px 12px" }}>+ Add Developer</a>
            </div>
            <div className="sc-body">
              <table className="dev-table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Role</th>
                    <th>Weekly Capacity</th>
                    <th>Jira Username</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  ${visibleDevelopers.length
                    ? visibleDevelopers.map((dev) => html`
                        <tr key=${dev.id}>
                          <td>
                            <div className="dev-name-cell">
                              <div className="dev-avatar-sm">${initials(dev.name)}</div>
                              <div>
                                <div className="settings-strong">${dev.name}</div>
                                <div className="settings-subtle">${dev.email}</div>
                              </div>
                            </div>
                          </td>
                          <td><span className="settings-inline-pill">${humanize(dev.role)}</span></td>
                          <td><input className="capacity-input" value=${dev.weeklyCapacityHours ?? 40} readOnly /> h/week</td>
                          <td><input className="capacity-input settings-jira-input" value=${dev.jiraUsername || ""} readOnly /></td>
                          <td><span className="active-dot"></span> Active</td>
                          <td><div className="action-btns"><div className="btn-icon">✏️</div><div className="btn-icon del">🗑</div></div></td>
                        </tr>
                      `)
                    : html`<tr><td colSpan="6" className="notice">No developers configured</td></tr>`}
                  ${state.devs.length > visibleDevelopers.length
                    ? html`<tr className="settings-overflow-row"><td colSpan="6">+ ${state.devs.length - visibleDevelopers.length} more developers available in the workspace</td></tr>`
                    : null}
                </tbody>
              </table>
            </div>
          </section>

          <section className="section-card" id="projects">
            <div className="sc-header">
              <div>
                <div className="sc-title">Projects</div>
                <div className="sc-sub">${state.projects.length} active projects</div>
              </div>
              <a className="btn btn-primary" href="/capacity-planner.html" style=${{ fontSize: "12px", padding: "7px 12px" }}>+ Add Project</a>
            </div>
            <div className="sc-body">
              <table className="proj-table">
                <thead>
                  <tr>
                    <th>Project</th>
                    <th>Jira Key</th>
                    <th>Delivery Mode</th>
                    <th>Target Utilization</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  ${state.projects.length
                    ? state.projects.map((project, index) => html`
                        <tr key=${project.id}>
                          <td><span className="proj-color" style=${{ background: ["#6366f1", "#a855f7", "#f97316", "#22c55e", "#ef4444"][index % 5] }}></span><strong>${project.name}</strong></td>
                          <td className="settings-mono">${project.jiraProjectKey || "-"}</td>
                          <td>${humanize(project.deliveryMode || "HYBRID")}</td>
                          <td><input className="capacity-input" value=${project.targetUtilizationPct ?? 70} readOnly />%</td>
                          <td><span className="active-dot"></span> ${project.active === false ? "Inactive" : "Active"}</td>
                          <td><div className="action-btns"><div className="btn-icon">✏️</div><div className="btn-icon del">🗑</div></div></td>
                        </tr>
                      `)
                    : html`<tr><td colSpan="6" className="notice">No projects configured</td></tr>`}
                </tbody>
              </table>
            </div>
          </section>

          <section className="section-card" id="capacity">
            <div className="sc-header"><div className="sc-title">Capacity Rules</div></div>
            <div className="sc-body">
              <div className="form-row"><div><div className="form-label">Default Weekly Capacity</div><div className="form-sub">Hours per developer per week</div></div><input className="form-input" style=${{ width: "120px", textAlign: "center" }} value="40 hours" readOnly /></div>
              <div className="form-row"><div><div className="form-label">Overload Threshold</div><div className="form-sub">Flag developer as overloaded above this %</div></div><input className="form-input" style=${{ width: "100px", textAlign: "center" }} value="85%" readOnly /></div>
              <div className="form-row"><div><div className="form-label">Busy Threshold</div><div className="form-sub">Flag developer as busy above this %</div></div><input className="form-input" style=${{ width: "100px", textAlign: "center" }} value="70%" readOnly /></div>
              <div className="form-row"><div><div className="form-label">Leave Impact on Bandwidth</div><div className="form-sub">Auto-reduce bandwidth during approved leaves</div></div><div className="toggle"></div></div>
              <div className="form-row"><div><div className="form-label">Story Points Weight</div><div className="form-sub">Use story points for delivery load</div></div><div className="toggle"></div></div>
            </div>
          </section>

          <section className="section-card" id="leave">
            <div className="sc-header"><div className="sc-title">Leave Types</div></div>
            <div className="sc-body">
              ${leaveTypes.map((type) => html`
                <div key=${type.name} className="leave-type-row">
                  <div className="lt-color" style=${{ background: type.color }}></div>
                  <span className="lt-name">${type.name}</span>
                  <span className="lt-days">${type.note}</span>
                  <span className=${`lt-badge ${type.tone}`}>${type.tone === "ltb-unpaid" ? "Unpaid" : "Paid"}</span>
                </div>
              `)}
            </div>
          </section>

          <section className="section-card" id="holidays">
            <div className="sc-header"><div className="sc-title">Public Holidays</div></div>
            <div className="sc-body">
              <div className="form-row"><div><div className="form-label">Holiday Calendar Source</div><div className="form-sub">Maintained through leave entries or external import</div></div><input className="form-input" value="Leave calendar events" readOnly /></div>
              <div className="form-row"><div><div className="form-label">Configured Holidays</div><div className="form-sub">Current month public holiday entries</div></div><input className="form-input" style=${{ width: "80px", textAlign: "center" }} value="0" readOnly /></div>
            </div>
          </section>

          <section className="section-card" id="jira">
            <div className="sc-header"><div className="sc-title">Jira Integration</div></div>
            <div className="sc-body">
              <div className="form-row"><div><div className="form-label">Planning Mode</div><div className="form-sub">Current workspace planning style</div></div><input className="form-input" value=${state.dashboard?.planningMode ? humanize(state.dashboard.planningMode) : "Not configured"} readOnly /></div>
              <div className="form-row"><div><div className="form-label">Window Source</div><div className="form-sub">Whether planning uses fallback or explicit setup</div></div><input className="form-input" value=${state.dashboard?.usingFallbackWindow ? "Fallback window" : "Configured window"} readOnly /></div>
              <div className="form-row"><div><div className="form-label">Mapped Projects</div><div className="form-sub">Projects eligible for Jira sync</div></div><input className="form-input" style=${{ width: "90px", textAlign: "center" }} value=${String(state.projects.filter((project) => project.jiraProjectKey).length)} readOnly /></div>
            </div>
          </section>

          <section className="section-card" id="notify">
            <div className="sc-header"><div className="sc-title">Notifications</div></div>
            <div className="sc-body">
              <div className="form-row"><div><div className="form-label">Email Alerts</div><div className="form-sub">Send mail when developers exceed thresholds</div></div><div className="toggle"></div></div>
              <div className="form-row"><div><div className="form-label">Slack Notifications</div><div className="form-sub">Post overload alerts to Slack</div></div><div className="toggle off"></div></div>
              <div className="form-row"><div><div className="form-label">Sprint End Report</div><div className="form-sub">Auto-email the current planning summary</div></div><div className="toggle"></div></div>
            </div>
          </section>

          <section className="section-card" id="roles">
            <div className="sc-header"><div className="sc-title">Roles & Access</div></div>
            <div className="sc-body">
              <div className="form-row"><div><div className="form-label">Engineering Managers</div><div className="form-sub">Users with dashboard and planning control</div></div><input className="form-input" value=${String(state.devs.filter((dev) => dev.role === "ENGINEERING_MANAGER").length)} readOnly /></div>
              <div className="form-row"><div><div className="form-label">Project Managers</div><div className="form-sub">Users coordinating delivery and staffing</div></div><input className="form-input" value=${String(state.devs.filter((dev) => dev.role === "PROJECT_MANAGER").length)} readOnly /></div>
              <div className="form-row"><div><div className="form-label">Tech Leads</div><div className="form-sub">Users leading day-to-day execution</div></div><input className="form-input" value=${String(state.devs.filter((dev) => dev.role === "TECH_LEAD").length)} readOnly /></div>
            </div>
          </section>
        </div>
      </div>

      ${state.error ? html`<div className="error-box">Failed to load data: ${state.error}</div>` : null}
    </${PageShell}>
  `;
}

function pageForCurrentRoute() {
  const page = document.body.dataset.page || "home";
  switch (page) {
    case "dashboard":
      return DashboardPage;
    case "developer":
      return DeveloperPage;
    case "leaves":
      return LeavesPage;
    case "planner":
      return PlannerPage;
    case "jira":
      return JiraPage;
    case "settings":
      return SettingsPage;
    case "home":
    default:
      return HomePage;
  }
}

function App() {
  const Page = pageForCurrentRoute();
  return html`<${Page} />`;
}

createRoot(document.getElementById("root")).render(html`<${App} />`);
