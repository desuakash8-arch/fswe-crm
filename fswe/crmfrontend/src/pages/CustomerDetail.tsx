import { useEffect,useState } from "react";
import { Link,useParams } from "react-router-dom";
import api from "../services/api";
import RecordPage from "../components/RecordPage";
import {messageOf} from "../utils/records";
import type {Row,Kind} from "../components/RecordPage";
export default function CustomerDetail(){const {id}=useParams();const [data,setData]=useState<{customer:Row;sourceLeads:Row[]}|null>(null),[tab,setTab]=useState<Kind>("interactions"),[error,setError]=useState("");
useEffect(()=>{const controller=new AbortController();void api.get(`/customers/${id}/detail`,{signal:controller.signal}).then(r=>setData(r.data)).catch(e=>{if(!controller.signal.aborted)setError(messageOf(e));});return()=>controller.abort();},[id]);
return <div className="page-container crm-workspace"><Link to="/customers">← Customers</Link>{error&&<p role="alert">{error}</p>}{data?<><h1>{data.customer.name}</h1><div className="crm-cards"><article><h2>Contact</h2><p>{data.customer.email||"No email"}</p><p>{data.customer.phone}</p><p>{data.customer.address}</p></article><article><h2>Relationship</h2><p>{data.customer.company}</p><p>{data.customer.status} · {data.customer.assignedTo?.name}</p>{data.sourceLeads.map(l=><p key={l.leadId}>Converted from lead #{l.leadId}: {l.name}<br/>{l.convertedAt}</p>)}</article></div><nav className="crm-toolbar" aria-label="Customer activity">{["interactions","deals","tasks"].map(k=><button key={k} aria-pressed={tab===k} onClick={()=>setTab(k as Kind)}>{k}</button>)}</nav><RecordPage key={tab} kind={tab} customerId={Number(id)}/></>:!error&&<p>Loading customer…</p>}</div>;
}
