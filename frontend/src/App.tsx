import { useEffect, useState } from 'react'
import {
  AlertCircle, ArrowDownUp, ArrowRight, Check, CheckCircle2, ChevronLeft, ChevronRight,
  CircleHelp, Clock3, FileUp, Filter, Link2, LoaderCircle, RefreshCw, Search, X,
} from 'lucide-react'

type Order = { orderId: string; customerId: string; amount: number; currency: string; createdAt: string }
type Payment = { paymentId: string; orderReference: string; manualOrderId?: string | null; amount: number; currency: string; paidAt: string }
type Discrepancy = {
  id: number; category: string; status: string; detail: string; resolutionReason?: string | null;
  createdAt: string; updatedAt: string; orderRecord?: Order | null; paymentRecord?: Payment | null
}
type Summary = {
  orderCount: number; paymentCount: number; matchedOrders: number; matchedPayments: number;
  hasReconciled: boolean;
  openDiscrepancies: Record<string, number>; orderTotalsByCurrency: Record<string, number>;
  paymentTotalsByCurrency: Record<string, number>; matchedOrderTotalsByCurrency: Record<string, number>;
  matchedPaymentTotalsByCurrency: Record<string, number>; unmatchedOrderTotalsByCurrency: Record<string, number>;
  unmatchedPaymentTotalsByCurrency: Record<string, number>; tolerance: number
}
type Job = { id: string; type: string; status: string; ordersImported: number; paymentsImported: number; invalidRows: number; errors: string[]; message: string }
type PageData = { content: Discrepancy[]; totalElements: number; totalPages: number; number: number }
type Audit = { id: number; action: string; entityType: string; entityId: string; beforeValue?: string; afterValue?: string; reason: string; createdAt: string }

const categories = [
  ['ALL', 'All exceptions'], ['MISSING_PAYMENT', 'Missing payment'], ['ORPHAN_PAYMENT', 'Orphan payment'],
  ['DUPLICATE_PAYMENT', 'Duplicate payment'], ['AMOUNT_MISMATCH', 'Amount mismatch'],
]
const categoryLabel = (value: string) => categories.find(([key]) => key === value)?.[1] ?? value.replaceAll('_', ' ').toLowerCase()
const money = (value?: number, currency = 'USD') => value == null ? '—' : new Intl.NumberFormat('en-US', { style: 'currency', currency, minimumFractionDigits: 2 }).format(value)
const moneyMap = (values?: Record<string, number>) => values && Object.keys(values).length
  ? Object.entries(values).sort(([left], [right]) => left.localeCompare(right)).map(([currency, value]) => money(value, currency)).join(' · ')
  : '—'
const dateTime = (value?: string) => value ? new Date(value).toLocaleString() : '—'

export default function App() {
  const [summary, setSummary] = useState<Summary | null>(null)
  const [items, setItems] = useState<Discrepancy[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [category, setCategory] = useState('ALL')
  const [status, setStatus] = useState('OPEN')
  const [query, setQuery] = useState('')
  const [active, setActive] = useState<Discrepancy | null>(null)
  const [ordersFile, setOrdersFile] = useState<File | null>(null)
  const [paymentsFile, setPaymentsFile] = useState<File | null>(null)
  const [job, setJob] = useState<Job | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [dialog, setDialog] = useState<'resolve' | 'link' | null>(null)
  const [reason, setReason] = useState('')
  const [orderQuery, setOrderQuery] = useState('')
  const [orderOptions, setOrderOptions] = useState<Order[]>([])
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null)
  const [audits, setAudits] = useState<Audit[]>([])
  const [showAudit, setShowAudit] = useState(false)

  async function api<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(`/api${path}`, init)
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw new Error(body?.message ?? `Request failed (${response.status})`)
    }
    if (response.status === 204) return undefined as T
    return response.json() as Promise<T>
  }

  async function refreshSummary() {
    try { setSummary(await api<Summary>('/summary')) } catch (caught) { setError((caught as Error).message) }
  }

  async function refreshItems() {
    setBusy(true)
    try {
      const params = new URLSearchParams({ page: String(page), size: '20', status })
      if (category !== 'ALL') params.set('category', category)
      if (query.trim()) params.set('q', query.trim())
      const result = await api<PageData>(`/discrepancies?${params}`)
      setItems(result.content); setTotalPages(result.totalPages); setTotalElements(result.totalElements)
      if (active) setActive(result.content.find(item => item.id === active.id) ?? active)
    } catch (caught) { setError((caught as Error).message) } finally { setBusy(false) }
  }

  useEffect(() => { void refreshSummary() }, [])
  useEffect(() => { void refreshItems() }, [page, category, status, query])
  useEffect(() => {
    if (!job || !['QUEUED', 'RUNNING'].includes(job.status)) return
    const timer = window.setInterval(async () => {
      try {
        const current = await api<Job>(`/jobs/${job.id}`)
        setJob(current)
        if (!['QUEUED', 'RUNNING'].includes(current.status)) {
          if (current.status === 'COMPLETED') setNotice(current.message)
          else setError(current.message)
          void refreshSummary(); void refreshItems()
        }
      } catch (caught) { setError((caught as Error).message) }
    }, 900)
    return () => window.clearInterval(timer)
  }, [job])

  useEffect(() => {
    if (!orderQuery.trim()) { setOrderOptions([]); return }
    const timer = window.setTimeout(async () => {
      try { setOrderOptions(await api<Order[]>(`/orders?q=${encodeURIComponent(orderQuery.trim())}`)) }
      catch (caught) { setError((caught as Error).message) }
    }, 180)
    return () => window.clearTimeout(timer)
  }, [orderQuery])

  async function upload() {
    if (!ordersFile || !paymentsFile) { setError('Choose both the Orders and Payments CSV files.'); return }
    setError(''); setNotice(''); setBusy(true)
    const form = new FormData(); form.append('orders', ordersFile); form.append('payments', paymentsFile)
    try { setJob(await api<Job>('/imports', { method: 'POST', body: form })) }
    catch (caught) { setError((caught as Error).message) } finally { setBusy(false) }
  }

  async function reconcile() {
    setError(''); setNotice(''); setBusy(true)
    try { setJob(await api<Job>('/reconciliations', { method: 'POST' })) }
    catch (caught) { setError((caught as Error).message) } finally { setBusy(false) }
  }

  async function submitAction() {
    if (!active) return
    setError('')
    try {
      if (dialog === 'resolve') {
        await api(`/discrepancies/${active.id}/resolve`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason }) })
        setNotice('Exception marked resolved.')
      } else if (dialog === 'link' && active.paymentRecord && selectedOrder) {
        await api(`/payments/${encodeURIComponent(active.paymentRecord.paymentId)}/link`, {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ orderId: selectedOrder.orderId, reason }),
        })
        setNotice('Payment linked and reconciliation refreshed.')
      }
      setDialog(null); setReason(''); setSelectedOrder(null); setOrderQuery('')
      await Promise.all([refreshItems(), refreshSummary()])
      setActive(null)
      if (showAudit) setAudits(await api<Audit[]>('/audit'))
    } catch (caught) { setError((caught as Error).message) }
  }

  async function openAudit() {
    setShowAudit(value => !value)
    try { setAudits(await api<Audit[]>('/audit')) } catch (caught) { setError((caught as Error).message) }
  }

  const counts = summary?.openDiscrepancies ?? {}
  const totalOpen = Object.values(counts).reduce((sum, value) => sum + value, 0)
  const isWorking = busy || Boolean(job && ['QUEUED', 'RUNNING'].includes(job.status))

  return <div className="app-shell">
    <header className="topbar">
      <a className="brand" href="#top" aria-label="Ledger workbench home"><span className="brand-mark">L</span><span>ledger<span className="brand-slash"> / </span><b>reconcile</b></span></a>
      <div className="topbar-right"><span className="environment"><i /> Local workspace</span><button className="icon-button" title="Refresh summary" onClick={() => { void refreshSummary(); void refreshItems() }}><RefreshCw size={16} /></button></div>
    </header>

    <main id="top" className="content">
      <div className="page-heading">
        <div><div className="eyebrow">OPERATIONS / DATA QUALITY</div><h1>Reconciliation desk</h1><p className="subtitle">Compare order and settlement records. Work exceptions through to closure.</p></div>
        <button className="button button-dark" onClick={reconcile} disabled={isWorking}><ArrowDownUp size={16} /> Run reconciliation</button>
      </div>

      {(error || notice) && <div className={`alert ${error ? 'alert-error' : 'alert-success'}`} role="status">
        {error ? <AlertCircle size={17} /> : <CheckCircle2 size={17} />}<span>{error || notice}</span>
        <button aria-label="Dismiss" onClick={() => { setError(''); setNotice('') }}><X size={15} /></button>
      </div>}

      <section className="summary-grid" aria-label="Reconciliation summary">
        <SummaryTile label="Orders loaded" value={summary?.orderCount} detail={`Gross ${moneyMap(summary?.orderTotalsByCurrency)}`} tone="green" />
        <SummaryTile label="Payments loaded" value={summary?.paymentCount} detail={`Gross ${moneyMap(summary?.paymentTotalsByCurrency)}`} tone="blue" />
        <SummaryTile label="Matched orders" value={summary?.matchedOrders} detail={`Matched ${moneyMap(summary?.matchedOrderTotalsByCurrency)}`} tone="dark" />
        <SummaryTile label="Open exceptions" value={summary?.hasReconciled ? totalOpen : undefined} detail={summary?.hasReconciled ? `${moneyMap(summary.unmatchedOrderTotalsByCurrency)} order value at risk` : 'Run reconciliation to compare records'} tone="coral" />
      </section>

      <section className="import-panel">
        <div className="section-title"><div><div className="eyebrow">01 / SOURCE DATA</div><h2>Import datasets</h2></div><span className="quiet-note"><CircleHelp size={14} /> Valid rows are kept when others fail validation</span></div>
        <div className="upload-row">
          <FilePicker label="Orders CSV" hint="order_id · customer_id · amount · currency · created_at" file={ordersFile} onChange={setOrdersFile} />
          <FilePicker label="Payments CSV" hint="payment_id · order_reference · amount · currency · paid_at" file={paymentsFile} onChange={setPaymentsFile} />
          <button className="button button-green upload-button" disabled={isWorking} onClick={upload}><FileUp size={16} /> Import files</button>
        </div>
        {job && <div className={`job-state ${job.status === 'FAILED' ? 'job-failed' : ''}`}>
          {['QUEUED', 'RUNNING'].includes(job.status) ? <LoaderCircle className="spin" size={16} /> : job.status === 'COMPLETED' ? <CheckCircle2 size={16} /> : <AlertCircle size={16} />}
          <span><b>{job.type === 'IMPORT' ? 'Import' : 'Reconciliation'} {job.status.toLowerCase()}</b> {job.message}</span>
          {job.type === 'IMPORT' && job.status === 'COMPLETED' && <span className="job-count">{job.ordersImported.toLocaleString()} orders · {job.paymentsImported.toLocaleString()} payments · {job.invalidRows} invalid</span>}
          {job.errors.length > 0 && <details className="validation-details"><summary>View row errors ({job.invalidRows})</summary><ul>{job.errors.map((item, index) => <li key={index}>{item}</li>)}</ul></details>}
        </div>}
      </section>

      <section className="exceptions-section">
        <div className="section-title exceptions-heading"><div><div className="eyebrow">02 / INVESTIGATION</div><h2>Open exceptions <span className="count-pill">{totalOpen.toLocaleString()}</span></h2></div>
          <button className={`text-button ${showAudit ? 'selected' : ''}`} onClick={() => void openAudit()}><Clock3 size={15} /> {showAudit ? 'Hide audit history' : 'Audit history'}</button>
        </div>
        <div className="filters">
          <label className="search-field"><Search size={16} /><input value={query} onChange={event => { setQuery(event.target.value); setPage(0) }} placeholder="Search order, payment, reference" /></label>
          <label className="select-wrap"><Filter size={15} /><select value={category} onChange={event => { setCategory(event.target.value); setPage(0) }} aria-label="Filter by category">
            {categories.map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select></label>
          <label className="select-wrap"><select value={status} onChange={event => { setStatus(event.target.value); setPage(0) }} aria-label="Filter by status"><option value="OPEN">Open</option><option value="RESOLVED">Resolved</option></select></label>
        </div>
        <div className="category-strip">
          {categories.slice(1).map(([key, label]) => <button key={key} className={`category-chip ${category === key ? 'chip-active' : ''}`} onClick={() => { setCategory(category === key ? 'ALL' : key); setPage(0) }}><span className={`category-dot dot-${key.toLowerCase()}`} />{label}<b>{counts[key] ?? 0}</b></button>)}
        </div>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Exception</th><th>Order</th><th>Payment</th><th>Order amount</th><th>Payment amount</th><th>Updated</th><th><span className="sr-only">Open details</span></th></tr></thead>
            <tbody>
              {busy && !items.length ? <tr><td colSpan={7} className="table-message"><LoaderCircle className="spin" size={18} /> Loading records</td></tr> : items.length === 0 ? <tr><td colSpan={7} className="table-message"><CheckCircle2 size={19} /> Nothing to review in this view</td></tr> : items.map(item => <tr key={item.id} className={active?.id === item.id ? 'row-active' : ''} onClick={() => setActive(item)}>
                <td><span className={`status-tag tag-${item.category.toLowerCase()}`}>{categoryLabel(item.category)}</span><div className="row-subtext">{item.status === 'RESOLVED' ? 'Resolved' : 'Needs review'}</div></td>
                <td className="mono">{item.orderRecord?.orderId ?? '—'}<div className="row-subtext">{item.orderRecord?.customerId ?? ''}</div></td>
                <td className="mono">{item.paymentRecord?.paymentId ?? '—'}<div className="row-subtext">Ref: {item.paymentRecord?.manualOrderId ?? item.paymentRecord?.orderReference ?? '—'}</div></td>
                <td className="amount">{money(item.orderRecord?.amount, item.orderRecord?.currency)}</td>
                <td className="amount">{money(item.paymentRecord?.amount, item.paymentRecord?.currency)}</td>
                <td className="date-cell">{dateTime(item.updatedAt)}</td><td className="open-cell"><ArrowRight size={16} /></td>
              </tr>)}
            </tbody>
          </table>
        </div>
        <div className="table-footer"><span>{totalElements.toLocaleString()} exceptions</span><div className="pagination"><span>Page {totalPages ? page + 1 : 0} of {totalPages}</span><button className="icon-button" aria-label="Previous page" disabled={page <= 0} onClick={() => setPage(page - 1)}><ChevronLeft size={16} /></button><button className="icon-button" aria-label="Next page" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}><ChevronRight size={16} /></button></div></div>
      </section>

      {showAudit && <section className="audit-section"><div className="section-title"><div><div className="eyebrow">CHANGE LOG</div><h2>Manual activity</h2></div></div>
        {audits.length ? <div className="audit-list">{audits.map(event => <div className="audit-row" key={event.id}><span className="audit-icon"><Check size={15} /></span><div><b>{event.action === 'RESOLVE' ? 'Exception resolved' : 'Payment manually linked'}</b><p>{event.entityType} {event.entityId} · {event.reason}</p><small>{event.beforeValue ? `${event.beforeValue} → ` : ''}{event.afterValue}</small></div><time>{dateTime(event.createdAt)}</time></div>)}</div> : <p className="empty-audit">No manual changes recorded yet.</p>}
      </section>}
    </main>

    {active && <aside className="detail-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) setActive(null) }}>
      <section className="detail-drawer" aria-label="Exception details">
        <div className="drawer-header"><div><div className="eyebrow">EXCEPTION #{active.id}</div><h2>{categoryLabel(active.category)}</h2></div><button className="icon-button" aria-label="Close details" onClick={() => { setActive(null); setDialog(null) }}><X size={18} /></button></div>
        <span className={`status-tag tag-${active.category.toLowerCase()}`}>{active.status === 'RESOLVED' ? 'Resolved' : categoryLabel(active.category)}</span>
        <p className="detail-description">{active.detail}</p>
        <div className="detail-columns">
          <RecordCard title="ORDER RECORD" empty="No order attached" record={active.orderRecord} fields={active.orderRecord ? [['Order ID', active.orderRecord.orderId], ['Customer', active.orderRecord.customerId], ['Amount', money(active.orderRecord.amount, active.orderRecord.currency)], ['Currency', active.orderRecord.currency], ['Created', dateTime(active.orderRecord.createdAt)]] : []} />
          <RecordCard title="PAYMENT RECORD" empty="No payment attached" record={active.paymentRecord} fields={active.paymentRecord ? [['Payment ID', active.paymentRecord.paymentId], ['Reference', active.paymentRecord.manualOrderId ?? active.paymentRecord.orderReference], ['Amount', money(active.paymentRecord.amount, active.paymentRecord.currency)], ['Currency', active.paymentRecord.currency], ['Paid', dateTime(active.paymentRecord.paidAt)]] : []} />
        </div>
        {active.resolutionReason && <div className="resolution-note"><b>Resolution note</b><p>{active.resolutionReason}</p></div>}
        <div className="drawer-actions">
          {active.paymentRecord && active.status === 'OPEN' && <button className="button button-outline" onClick={() => { setDialog('link'); setReason(''); setOrderQuery(''); setSelectedOrder(null) }}><Link2 size={15} /> Link to order</button>}
          {active.status === 'OPEN' && <button className="button button-green" onClick={() => { setDialog('resolve'); setReason('') }}><Check size={16} /> Resolve exception</button>}
        </div>
        {dialog && <div className="dialog-card"><div className="dialog-title"><b>{dialog === 'resolve' ? 'Resolve exception' : 'Link payment to order'}</b><button className="icon-button" aria-label="Close dialog" onClick={() => setDialog(null)}><X size={15} /></button></div>
          {dialog === 'link' && <div className="form-field"><label htmlFor="order-search">Target order</label><input id="order-search" value={selectedOrder ? `${selectedOrder.orderId} · ${selectedOrder.customerId}` : orderQuery} onChange={event => { setSelectedOrder(null); setOrderQuery(event.target.value) }} placeholder="Search order ID or customer" />
            {!selectedOrder && orderOptions.length > 0 && <div className="order-options">{orderOptions.slice(0, 8).map(order => <button key={order.orderId} onClick={() => { setSelectedOrder(order); setOrderQuery('') }}><b>{order.orderId}</b><span>{order.customerId} · {money(order.amount, order.currency)}</span></button>)}</div>}
            {!selectedOrder && orderQuery && orderOptions.length === 0 && <small>No matching order found.</small>}
          </div>}
          <div className="form-field"><label htmlFor="action-reason">Reason <span>Required</span></label><textarea id="action-reason" value={reason} onChange={event => setReason(event.target.value)} maxLength={1000} placeholder={dialog === 'resolve' ? 'Explain why this exception is resolved' : 'Explain why this payment belongs to the selected order'} /></div>
          <div className="dialog-actions"><button className="button button-quiet" onClick={() => setDialog(null)}>Cancel</button><button className="button button-dark" disabled={!reason.trim() || (dialog === 'link' && !selectedOrder)} onClick={() => void submitAction()}>{dialog === 'resolve' ? 'Confirm resolution' : 'Confirm link'}</button></div>
        </div>}
      </section>
    </aside>}
    <footer className="footer"><span>Ledger Reconciliation Workbench</span><span>Amount tolerance <b>{money(summary?.tolerance)}</b></span></footer>
  </div>
}

function SummaryTile({ label, value, detail, tone }: { label: string; value?: number; detail: string; tone: string }) {
  return <div className={`summary-tile tone-${tone}`}><div className="tile-label">{label}</div><div className="tile-value">{value == null ? <span className="skeleton" /> : value.toLocaleString()}</div><div className="tile-detail">{detail}</div></div>
}

function FilePicker({ label, hint, file, onChange }: { label: string; hint: string; file: File | null; onChange: (file: File | null) => void }) {
  return <label className={`file-picker ${file ? 'file-selected' : ''}`}><span className="file-title">{file ? <CheckCircle2 size={16} /> : <FileUp size={16} />}{label}</span><span className="file-name">{file?.name ?? 'Choose a CSV file'}</span><span className="file-hint">{file ? `${(file.size / 1024).toFixed(0)} KB` : hint}</span><input type="file" accept=".csv,text/csv" onChange={event => onChange(event.target.files?.[0] ?? null)} /></label>
}

function RecordCard({ title, empty, record, fields }: { title: string; empty: string; record?: Order | Payment | null; fields: string[][] }) {
  return <div className="record-card"><div className="record-heading">{title}</div>{record ? fields.map(([label, value]) => <div className="record-field" key={label}><span>{label}</span><b>{value}</b></div>) : <div className="record-empty">{empty}</div>}</div>
}