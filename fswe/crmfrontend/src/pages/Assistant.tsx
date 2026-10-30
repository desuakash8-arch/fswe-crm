import {useEffect, useRef, useState} from "react";
import {Link, useSearchParams} from "react-router-dom";
import {Bot, CalendarCheck, Users, Activity, Mail, Send, Copy, Sparkles, RotateCcw, ExternalLink, LoaderCircle} from "lucide-react";
import api from "../services/api";
import {messageOf} from "../utils/records";
import {getCurrentUser} from "../utils/auth";
import type {Row, Page} from "../components/RecordPage";

type Mode = "DAILY_PRIORITIES" | "CUSTOMER_SUMMARY" | "DEAL_HEALTH" | "EMAIL_DRAFT";
interface Source {id:string; label:string; href:string}
interface Reply {
  mode:Mode; answer:string; points:{text:string; sourceIds:string[]}[];
  draft:{subject:string; body:string}|null; sources:Source[]; facts:string[]; scope:string; generatedAt:string;
}
interface Entry {id:number; question:string; reply:Reply}
const modes = [
  {id:"DAILY_PRIORITIES" as const, label:"Daily priorities", icon:CalendarCheck, description:"Know what needs your attention today.", prompt:"What should I focus on today? Prioritize my follow-ups and explain why."},
  {id:"CUSTOMER_SUMMARY" as const, label:"Customer summary", icon:Users, description:"Catch up before your next conversation.", prompt:"Summarize this customer's recent activity, open deals, and next steps."},
  {id:"DEAL_HEALTH" as const, label:"Deal health", icon:Activity, description:"Understand the status and your next move.", prompt:"Explain this deal's health status and suggest the next actions."},
  {id:"EMAIL_DRAFT" as const, label:"Email draft", icon:Mail, description:"Turn customer context into a useful follow-up.", prompt:"Draft a polite follow-up email based on the latest recorded conversation. Ask about the next step."},
];
const validMode = (value:string|null):Mode => modes.find(m=>m.id===value)?.id??"DAILY_PRIORITIES";
const recordId = (value:string|null) => value && /^[1-9]\d*$/.test(value) ? value : "";

function EmailDraft({draft}:{draft:{subject:string;body:string}}) {
  const [subject,setSubject]=useState(draft.subject),[body,setBody]=useState(draft.body),[notice,setNotice]=useState("");
  async function copy() {
    try { await navigator.clipboard.writeText(`Subject: ${subject}\n\n${body}`); setNotice("Draft copied. You can paste it into your email app."); }
    catch { setNotice("Copy is unavailable. Select and copy the subject and message below."); }
  }
  return <section className="assistant-draft" aria-label="Email draft editor">
    <div className="assistant-section-title"><h3>Your email draft</h3><span>Not sent</span></div>
    <label>Draft subject<input value={subject} maxLength={240} onChange={e=>{setSubject(e.target.value);setNotice("");}}/></label>
    <label>Draft message<textarea rows={9} value={body} maxLength={8000} onChange={e=>{setBody(e.target.value);setNotice("");}}/></label>
    <button type="button" onClick={()=>void copy()}><Copy size={16}/>Copy draft</button>
    <p>Review and edit the details before sending from your email app.</p>
    {notice&&<p role="status">{notice}</p>}
  </section>;
}

export default function Assistant() {
  const [params]=useSearchParams();
  const initialMode=validMode(params.get("mode"));
  const [mode,setMode]=useState<Mode>(initialMode);
  const [customerId,setCustomerId]=useState(recordId(params.get("customerId")));
  const [dealId,setDealId]=useState(recordId(params.get("dealId")));
  const [search,setSearch]=useState("");
  const [records,setRecords]=useState<Row[]>([]),[dealOptions,setDealOptions]=useState<Row[]>([]);
  const [recordTotal,setRecordTotal]=useState(0),[recordLoading,setRecordLoading]=useState(false);
  const [question,setQuestion]=useState(modes.find(m=>m.id===initialMode)!.prompt);
  const [config,setConfig]=useState<{configured:boolean;provider:string}|null>(null);
  const [configError,setConfigError]=useState(""),[selectionError,setSelectionError]=useState(""),[error,setError]=useState("");
  const [busy,setBusy]=useState(false),[entries,setEntries]=useState<Entry[]>([]),[configRevision,setConfigRevision]=useState(0);
  const request=useRef<AbortController|null>(null),result=useRef<HTMLDivElement>(null),nextId=useRef(0);
  const user=getCurrentUser();
  const selectedMode=modes.find(m=>m.id===mode)!;
  const needsCustomer=mode==="CUSTOMER_SUMMARY"||mode==="EMAIL_DRAFT";
  const needsDeal=mode==="DEAL_HEALTH";

  useEffect(()=>{const c=new AbortController();void api.get("/assistant/config",{signal:c.signal}).then(r=>{setConfig(r.data);setConfigError("");}).catch(e=>{if(!c.signal.aborted)setConfigError(messageOf(e));});return()=>c.abort();},[configRevision]);
  useEffect(()=>()=>request.current?.abort(),[]);
  useEffect(()=>{
    if(mode==="DAILY_PRIORITIES")return;
    const c=new AbortController();
    const timer=setTimeout(()=>{setRecordLoading(true);void api.get<Page<Row>>(`/records/${mode==="DEAL_HEALTH"?"deals":"customers"}`,{params:{search,size:25,sort:mode==="DEAL_HEALTH"?"title":"name"},signal:c.signal})
      .then(r=>{setRecords(r.data.content);setRecordTotal(r.data.totalElements);setSelectionError("");})
      .catch(e=>{if(!c.signal.aborted)setSelectionError(messageOf(e));})
      .finally(()=>{if(!c.signal.aborted)setRecordLoading(false);});},200);
    return()=>{clearTimeout(timer);c.abort();};
  },[mode,search]);
  useEffect(()=>{
    if(mode!=="EMAIL_DRAFT"||!customerId)return;
    const c=new AbortController();void api.get<Page<Row>>("/records/deals",{params:{customerId,size:100},signal:c.signal}).then(r=>setDealOptions(r.data.content)).catch(e=>{if(!c.signal.aborted)setSelectionError(messageOf(e));});return()=>c.abort();
  },[mode,customerId]);

  function choose(next:Mode) {
    if(busy)return;
    setMode(next);setSearch("");setRecords([]);setRecordTotal(0);setSelectionError("");setError("");
    setQuestion(modes.find(m=>m.id===next)!.prompt);
    // A deal selected independently of a customer must not carry into a customer draft.
    if(next!=="DEAL_HEALTH")setDealId("");
  }
  async function ask(event:React.FormEvent) {
    event.preventDefault();
    if(busy)return;
    if(needsCustomer&&!customerId){setError("Select a customer first.");return;}
    if(needsDeal&&!dealId){setError("Select a deal first.");return;}
    const c=new AbortController();request.current=c;setBusy(true);setError("");
    const asked=question.trim()||selectedMode.prompt;
    try {
      const r=await api.post<Reply>("/assistant/ask",{mode,question:asked,customerId:needsCustomer?Number(customerId):null,dealId:(needsDeal||mode==="EMAIL_DRAFT")&&dealId?Number(dealId):null},{signal:c.signal,timeout:45000});
      setEntries(previous=>[...previous.slice(-7),{id:++nextId.current,question:asked,reply:r.data}]);
      requestAnimationFrame(()=>{result.current?.scrollIntoView({behavior:"smooth",block:"start"});result.current?.focus({preventScroll:true});});
    } catch(e) {if(!c.signal.aborted)setError(messageOf(e));}
    finally {if(!c.signal.aborted)setBusy(false);}
  }

  return <div className="page-container crm-workspace assistant-page">
    <header className="page-header-modern"><div><span className="page-eyebrow">Your CRM workspace</span><div className="customer-title-row"><Bot size={28} className="customer-title-icon"/><div><h1>AI Assistant</h1><p>A clearer view of your day, your customers, and your next steps.</p></div></div></div>
      <div className="header-actions"><button disabled={busy||!entries.length} onClick={()=>{setEntries([]);setError("");}}><RotateCcw size={16}/>Clear conversation</button></div>
    </header>
    <div className="assistant-intro"><span className="assistant-provider"><Sparkles size={15}/>{config?.configured?"Powered by Groq":"CRM Assistant"}</span><p>Answers use records you can access. Suggestions and drafts are for you to review.</p></div>
    {configError&&<div className="crm-error" role="alert">{configError} <button onClick={()=>setConfigRevision(v=>v+1)}>Retry connection</button></div>}
    {config&&!config.configured&&<section className="crm-panel assistant-setup"><h2>AI setup is pending</h2><p>{user?.role==="ADMIN"?"Add GROQ_API_KEY to the backend environment, then deploy or restart the backend. Your key stays on the server.":"Ask your administrator to connect Groq to enable the assistant."}</p><button onClick={()=>setConfigRevision(v=>v+1)}>Check connection</button></section>}
    <div className="assistant-options" aria-label="Assistant topics">{modes.map(item=>{const Icon=item.icon;return <button type="button" key={item.id} className="assistant-option" aria-pressed={mode===item.id} disabled={busy} onClick={()=>choose(item.id)}><Icon size={22}/><span><strong>{item.label}</strong><small>{item.description}</small></span></button>;})}</div>
    <form className="crm-panel assistant-composer" onSubmit={e=>void ask(e)}>
      <div className="assistant-section-title"><h2>{selectedMode.label}</h2><span>Live CRM context</span></div>
      {mode!=="DAILY_PRIORITIES"&&<fieldset disabled={busy} className="assistant-records"><legend>Choose the context</legend>
        <label>Find {needsDeal?"a deal":"a customer"}<input type="search" value={search} onChange={e=>setSearch(e.target.value)} placeholder={needsDeal?"Search deal title":"Search customer name or email"}/></label>
        <label>{needsDeal?"Deal":"Customer"}<select aria-label={needsDeal?"Deal":"Customer"} required value={needsDeal?dealId:customerId} onChange={e=>{if(needsDeal)setDealId(e.target.value);else{setCustomerId(e.target.value);setDealId("");setDealOptions([]);}setError("");}}>
          <option value="">Select {needsDeal?"a deal":"a customer"}</option>
          {(needsDeal?dealId:customerId)&&!records.some(r=>String(needsDeal?r.dealId:r.customerId)===(needsDeal?dealId:customerId))&&<option value={needsDeal?dealId:customerId}>Selected {needsDeal?"deal":"customer"} #{needsDeal?dealId:customerId}</option>}
          {records.map(r=><option key={needsDeal?r.dealId:r.customerId} value={needsDeal?r.dealId:r.customerId}>{needsDeal?r.title:r.name}</option>)}
        </select></label>
        {mode==="EMAIL_DRAFT"&&customerId&&<label>Related deal (optional)<select aria-label="Related deal (optional)" value={dealId} onChange={e=>setDealId(e.target.value)}><option value="">General customer follow-up</option>{dealId&&!dealOptions.some(d=>String(d.dealId)===dealId)&&<option value={dealId}>Selected deal #{dealId}</option>}{dealOptions.map(d=><option key={d.dealId} value={d.dealId}>{d.title}</option>)}</select></label>}
        <p className="assistant-selector-note" role="status">{recordLoading?"Finding records…":recordTotal===0?"No matching records. Try another search or create a record first.":recordTotal>records.length?`Showing ${records.length} of ${recordTotal}. Refine your search to find another record.`:`${recordTotal} matching record(s)`}</p>
      </fieldset>}
      {selectionError&&<p className="crm-error" role="alert">{selectionError}</p>}
      <label htmlFor="assistant-question">What would you like help with?</label>
      <textarea id="assistant-question" rows={3} maxLength={1500} value={question} disabled={busy} onChange={e=>setQuestion(e.target.value)} placeholder={selectedMode.prompt}/>
      <div className="assistant-compose-footer"><span>{question.length}/1500 · Each question uses fresh records</span><button className="primary-btn" disabled={busy||!config?.configured} type="submit">{busy?<LoaderCircle size={17} className="assistant-spin"/>:<Send size={17}/>} {busy?"Preparing your answer…":mode==="EMAIL_DRAFT"?"Draft email":"Ask assistant"}</button></div>
      {error&&<p className="crm-error" role="alert">{error}</p>}
      {busy&&<p role="status">Reviewing the selected CRM records…</p>}
    </form>
    {!entries.length&&<div className="assistant-empty"><Bot size={32}/><h2>Start with what matters today</h2><p>Choose a topic above. Add a customer or deal when needed, then ask your question.</p><p>Conversations stay on this page and clear when you leave or sign out.</p></div>}
    <div className="assistant-conversation" aria-label="Assistant conversation">{entries.map((entry,index)=>{
      const r=entry.reply;return <article className="assistant-exchange" key={entry.id}>
        <div className="assistant-question-bubble"><strong>You</strong><p>{entry.question}</p></div>
        <div className="crm-panel assistant-answer" ref={index===entries.length-1?result:undefined} tabIndex={-1}>
          <div className="assistant-section-title"><h2><Bot size={19}/>CRM Assistant</h2><span>{modes.find(m=>m.id===r.mode)?.label}</span></div>
          <p className="assistant-answer-text">{r.answer}</p>
          {r.points.length>0&&<ul className="assistant-points">{r.points.map((point,i)=><li key={i}><p>{point.text}</p>{point.sourceIds.length>0&&<div className="assistant-source-links">{point.sourceIds.map(id=>{const source=r.sources.find(s=>s.id===id);return source?<Link key={id} to={source.href}><ExternalLink size={12}/>{source.label}</Link>:null;})}</div>}</li>)}</ul>}
          {r.draft&&<EmailDraft draft={r.draft}/>}
          <details className="assistant-evidence"><summary>CRM facts and source records</summary><ul>{r.facts.map((fact,i)=><li key={i}>{fact}</li>)}</ul><div className="assistant-source-links">{r.sources.map(source=><Link key={source.id} to={source.href}><ExternalLink size={12}/>{source.label}</Link>)}</div></details>
          <p className="assistant-answer-meta">{r.scope} · Checked {new Date(r.generatedAt).toLocaleString()}</p>
        </div>
      </article>;
    })}</div>
  </div>;
}
