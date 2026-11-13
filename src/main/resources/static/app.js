const STATUS_LABEL = {
  OPEN: '待处理',
  ASSIGNED: '已派单',
  IN_PROGRESS: '处理中',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
};

const PRIORITY_LABEL = { LOW: '低', MEDIUM: '中', HIGH: '高', URGENT: '紧急' };

const NEXT_ACTION = {
  OPEN: { to: 'IN_PROGRESS', label: '开始处理' },
  ASSIGNED: { to: 'IN_PROGRESS', label: '开始处理' },
  IN_PROGRESS: { to: 'RESOLVED', label: '标记解决' },
  RESOLVED: { to: 'CLOSED', label: '关闭工单' },
};

const el = (id) => document.getElementById(id);
let assignees = [];

async function api(path, options = {}) {
  const res = await fetch('/api' + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  const text = await res.text();
  const body = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const message = body?.message || res.statusText;
    const detail = body?.details?.length ? '：' + body.details.join('；') : '';
    throw new Error(message + detail);
  }
  return body;
}

function toast(node, message, kind = '') {
  node.textContent = message;
  node.className = 'hint ' + kind;
  if (message) {
    setTimeout(() => {
      if (node.textContent === message) node.textContent = '';
    }, 4000);
  }
}

async function loadStats() {
  const stats = await api('/stats');
  el('stats').innerHTML = [
    ['总数', stats.total],
    ...stats.byStatus.map((item) => [STATUS_LABEL[item.key] || item.key, item.count]),
  ]
    .map(([label, value]) => `<div class="chip"><b>${value}</b><span>${label}</span></div>`)
    .join('');
}

async function loadAssignees() {
  assignees = await api('/assignees');
}

function renderTicket(ticket) {
  const node = el('ticket-tpl').content.firstElementChild.cloneNode(true);
  node.querySelector('.tid').textContent = ticket.id;
  node.querySelector('.title').textContent = ticket.title;
  node.querySelector('.desc').textContent = ticket.description || '';
  node.querySelector('.assignee').textContent = ticket.assigneeName
    ? '处理人：' + ticket.assigneeName
    : '未派单';
  node.querySelector('.time').textContent = '创建于 ' + new Date(ticket.createdAt).toLocaleString('zh-CN');

  const status = node.querySelector('.pill.status');
  status.textContent = STATUS_LABEL[ticket.status] || ticket.status;
  status.classList.add('s-' + ticket.status);

  const priority = node.querySelector('.pill.priority');
  priority.textContent = PRIORITY_LABEL[ticket.priority] || ticket.priority;
  priority.classList.add('p-' + ticket.priority);

  const select = node.querySelector('.assignee-select');
  select.innerHTML =
    '<option value="">选择处理人</option>' +
    assignees
      .filter((a) => a.active)
      .map((a) => `<option value="${a.id}">${a.name} · ${a.team}</option>`)
      .join('');

  const assignBtn = node.querySelector('.assign');
  assignBtn.disabled = !(ticket.status === 'OPEN' || ticket.status === 'ASSIGNED' || ticket.status === 'IN_PROGRESS');
  assignBtn.addEventListener('click', async () => {
    if (!select.value) return;
    try {
      await api(`/tickets/${ticket.id}/assign`, {
        method: 'POST',
        body: JSON.stringify({ assigneeId: Number(select.value) }),
      });
      await refresh();
      toast(el('list-summary'), '派单成功', 'ok');
    } catch (e) {
      toast(el('list-summary'), e.message, 'error');
    }
  });

  const advance = node.querySelector('.advance');
  const next = NEXT_ACTION[ticket.status];
  if (next) {
    advance.textContent = next.label;
    advance.dataset.to = next.to;
  } else {
    advance.remove();
  }
  advance.addEventListener('click', async () => {
    try {
      await api(`/tickets/${ticket.id}/status`, {
        method: 'POST',
        body: JSON.stringify({ status: advance.dataset.to }),
      });
      await refresh();
    } catch (e) {
      toast(el('list-summary'), e.message, 'error');
    }
  });

  return node;
}

async function loadTickets() {
  const params = new URLSearchParams({ size: '20' });
  const status = el('filter-status').value;
  const priority = el('filter-priority').value;
  if (status) params.set('status', status);
  if (priority) params.set('priority', priority);

  const page = await api('/tickets?' + params.toString());
  const box = el('tickets');
  box.innerHTML = '';
  if (!page.items.length) {
    box.innerHTML = '<div class="empty">还没有工单，先在左边提交一个。</div>';
  } else {
    page.items.forEach((t) => box.appendChild(renderTicket(t)));
  }
  el('list-summary').textContent = `共 ${page.total} 条，当前第 ${page.page} 页（单页上限 100）`;
}

async function refresh() {
  await Promise.all([loadStats(), loadTickets()]);
}

el('create-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const payload = {
    title: el('title').value,
    description: el('description').value,
    priority: el('priority').value,
  };
  try {
    await api('/tickets', { method: 'POST', body: JSON.stringify(payload) });
    el('create-form').reset();
    el('priority').value = 'MEDIUM';
    toast(el('create-msg'), '已创建', 'ok');
    await refresh();
  } catch (e) {
    toast(el('create-msg'), e.message, 'error');
  }
});

el('refresh').addEventListener('click', refresh);
el('filter-status').addEventListener('change', loadTickets);
el('filter-priority').addEventListener('change', loadTickets);

(async function boot() {
  await loadAssignees();
  await refresh();
})();
