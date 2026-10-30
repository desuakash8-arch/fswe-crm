import {useEffect,useRef,useState} from "react";
import {Link,useParams} from "react-router-dom";
import {Activity,ArrowLeft,CalendarDays,Clock3,MessageSquare,RefreshCw} from "lucide-react";
import api from "../services/api";
import {messageOf} from "../utils/records";
import RecordPage from "../components/RecordPage";
import type {Row} from "../components/RecordPage";
import {HealthStatus} from "../components/DealHealthBadge";
import type {Health} from "../components/DealHealthBadge";

const dateTime=(value:string|null)=>value?new Date(value).toLocaleString(undefined,{dateStyle:"medium",timeStyle:"short"}):"Not recorded";
export default function DealHealth() {
  const {id}=useParams();
  const [deal,setDeal]=useState<Row|null>(null),[health,setHealth]=useState<Health|null>(null);
  const [error,setError]=useState(""),[notice,setNotice]=useState(""),[revision,setRevision]=useState(0);
  const [tab,setTab]=useState<"tasks"|"interactions">("interactions"),[date,setDate]=useState("");
  const [editDate,setEditDate]=useState(false),[busy,setBusy]=useState(false);
  const activity=useRef<HTMLDivElement>(null),dateInput=useRef<HTMLInputElement>(null);
  const refresh=()=>setRevision(v=>v+1);
  useEffect(()=>{
    const controller=new AbortController();
    const fetchData=()=>{void Promise.all([api.get<Row>(`/deals/${id}`,{signal:controller.signal}),api.get<Health>(`/deals/${id}/health`,{signal:controller.signal})])
      .then(([d,h])=>{setDeal(d.data);setHealth(h.data);setError("");})
      .catch(e=>{if(!controller.signal.aborted){setError(messageOf(e));setHealth(null);setDeal(null);}});};
    fetchData();const timer=window.setInterval(fetchData,60000);
    return()=>{controller.abort();window.clearInterval(timer);};
  },[id,revision]);
  function action(kind:string) {
    if(kind==="CLOSE_DATE") {
      setDate(deal?.expectedCloseDate??"");setEditDate(true);
      requestAnimationFrame(()=>dateInput.current?.focus());
    } else {
      setTab(kind==="TASKS"?"tasks":"interactions");
      requestAnimationFrame(()=>{activity.current?.scrollIntoView({behavior:"smooth",block:"start"});activity.current?.focus({preventScroll:true});});
    }
  }
  async function saveDate(event:React.FormEvent) {
    event.preventDefault();setBusy(true);setNotice("");
    try {await api.patch(`/deals/${id}/expected-close`,{expectedCloseDate:date||null});setEditDate(false);setNotice("Closing date updated.");refresh();}
    catch(e){setError(messageOf(e));}finally{setBusy(false);}
  }
  return <div className="page-container crm-workspace">
    <Link className="health-back" to="/deals"><ArrowLeft size={16}/>Back to deals</Link>
    <header className="page-header-modern"><div><span className="page-eyebrow">Sales Management</span>
      <div className="customer-title-row"><Activity className="customer-title-icon" size={28}/><div><h1>Deal health</h1><p>{deal?.title??"Review this opportunity's next steps."}</p></div></div>
    </div><div className="header-actions"><button className="secondary-btn" onClick={refresh}><RefreshCw size={15}/>Refresh</button><Link to={`/assistant?mode=DEAL_HEALTH&dealId=${id}`}>Ask AI about this deal</Link><Link to="/pipeline">Pipeline board</Link></div></header>
    {error&&<p className="crm-error" role="alert">{error}</p>}{notice&&<p className="crm-notice" role="status">{notice}</p>}
    {!health&&!error&&<p role="status">Loading deal health…</p>}
    {deal&&health&&<>
      <div className="health-overview crm-panel"><div><HealthStatus health={health}/><p>Stage: {deal.stage} · {Number(deal.amount??0).toLocaleString("en-IN",{style:"currency",currency:"INR"})} · {deal.assignedTo?.name??"Unassigned"}</p></div>
        <small>Checked {dateTime(health.evaluatedAt)}</small></div>
      {health.status!=="CLOSED"&&<div className="crm-cards health-facts">
        <article><MessageSquare size={20}/><h2>Last interaction</h2><p>{dateTime(health.lastInteraction)}</p></article>
        <article><Clock3 size={20}/><h2>Next action</h2><p>{health.nextAction?dateTime(health.nextAction):"Nothing scheduled"}</p></article>
        <article><CalendarDays size={20}/><h2>Expected close</h2><p>{deal.expectedCloseDate??"Not set"}</p><button onClick={()=>action("CLOSE_DATE")}>Review closing date</button></article>
      </div>}
      <div className="health-details-grid">
        <section className="crm-panel"><h2>Why this status?</h2><ul className="health-reasons">{health.reasons.map(reason=><li key={reason.code}><span className={`health-reason-dot reason-${reason.severity.toLowerCase()}`}/><span>{reason.message}</span></li>)}</ul>
          <p>Health uses only activities explicitly linked to this deal that you can access. Unlinked customer activity and future-dated interactions do not count as completed contact.</p>
        </section>
        <section className="crm-panel"><h2>Suggested actions</h2>{health.actions.length?health.actions.map(a=><div className="health-suggestion" key={a.kind}><h3>{a.label}</h3><p>{a.description}</p><button onClick={()=>action(a.kind)}>{a.kind==="TASKS"?"Open deal tasks":a.kind==="INTERACTIONS"?"Open deal interactions":"Review closing date"}</button></div>):<p>{health.status==="CLOSED"?"This deal is closed. No open-deal actions are suggested.":"Keep the scheduled follow-up and record the outcome after contacting the customer."}</p>}</section>
      </div>
      {editDate&&<form className="crm-panel health-date-editor" onSubmit={e=>void saveDate(e)}><h2>Review closing date</h2><label>Expected closing date<input ref={dateInput} type="date" value={date} onChange={e=>setDate(e.target.value)} required/></label><div className="crm-toolbar"><button className="primary-btn" disabled={busy}>{busy?"Saving…":"Save closing date"}</button><button type="button" disabled={busy} onClick={()=>setEditDate(false)}>Cancel</button></div></form>}
      <p className="health-rule-note">Rules: {health.inactiveDays}+ days without contact needs attention. An overdue task or follow-up needs attention; {health.overdueRiskDays}+ days overdue is at risk. A past closing date is at risk. Missing contact, a next action, or a closing date needs attention. These are workflow checks, not a prediction of winning. Deal stages change only when you update them.</p>
      <div ref={activity} tabIndex={-1} className="health-linked-activity"><nav className="crm-toolbar" aria-label="Deal activity"><button aria-pressed={tab==="interactions"} onClick={()=>setTab("interactions")}>Deal interactions</button><button aria-pressed={tab==="tasks"} onClick={()=>setTab("tasks")}>Deal tasks</button></nav>
        {deal.customer?.customerId?<RecordPage key={`${id}-${tab}`} kind={tab} customerId={deal.customer.customerId} dealId={Number(id)} onChanged={refresh}/>:<p>Assign a customer in the Deals editor before adding deal activity.</p>}
      </div>
    </>}
  </div>;
}
