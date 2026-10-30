import {useEffect,useState} from "react";
import {CreditCard,ShieldCheck,RefreshCw} from "lucide-react";
import {API_BASE_URL} from "../services/apiBaseUrl";
interface Invoice {id:number;title:string;customerName:string;amountMinor:number;currency:string;status:string;paymentId:string|null;paidAt:string|null;configured:boolean}
interface Result {razorpay_payment_id:string;razorpay_order_id:string;razorpay_signature:string}
interface CheckoutInstance {open:()=>void;on:(event:string,callback:()=>void)=>void}
interface CheckoutOptions {key:string;order_id:string;amount:number;currency:string;name:string;description:string;prefill:{name:string};theme:{color:string};handler:(result:Result)=>void;modal:{ondismiss:()=>void}}
declare global {interface Window {Razorpay?:new(options:CheckoutOptions)=>CheckoutInstance}}
let scriptPromise:Promise<void>|null=null;
function loadCheckout(){if(window.Razorpay)return Promise.resolve();if(!scriptPromise)scriptPromise=new Promise<void>((resolve,reject)=>{const script=document.createElement("script");script.src="https://checkout.razorpay.com/v1/checkout.js";script.onload=()=>window.Razorpay?resolve():reject(new Error("Checkout did not load"));script.onerror=()=>{script.remove();scriptPromise=null;reject(new Error("Unable to load Razorpay. Check your connection and retry."));};document.head.appendChild(script);});return scriptPromise;}
async function request<T>(path:string,data:unknown):Promise<T>{const response=await fetch(`${API_BASE_URL}/public/payments/${path}`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(data),cache:"no-store",referrerPolicy:"no-referrer"});const result=await response.json();if(!response.ok)throw new Error(result.message||"Payment request failed. Please retry.");return result as T;}
export default function CustomerPayment(){
 const [token]=useState(()=>window.location.hash.slice(1)),[invoice,setInvoice]=useState<Invoice|null>(null),[error,setError]=useState(""),[notice,setNotice]=useState(""),[busy,setBusy]=useState(false);
 const money=(amount:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(amount/100);
 useEffect(()=>{let active=true;void request<Invoice>("details",{token}).then(i=>{if(active)setInvoice(i);}).catch(e=>{if(active)setError(e.message);});return()=>{active=false;};},[token]);
 async function check(){setBusy(true);setError("");try{setInvoice(await request<Invoice>("status",{token}));setNotice("Payment status checked.");}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 useEffect(()=>{if(invoice?.status!=="PROCESSING")return;let checks=0;const timer=setInterval(()=>{if(++checks>10){clearInterval(timer);return;}void request<Invoice>("status",{token}).then(setInvoice).catch(()=>{});},8000);return()=>clearInterval(timer);},[invoice?.status,token]);
 async function pay(){if(!invoice)return;setBusy(true);setError("");setNotice("");try{
  await loadCheckout();const order=await request<{keyId:string;orderId:string;amountMinor:number;currency:string}>("order",{token});let completed=false;
  const checkout=new window.Razorpay!({key:order.keyId,order_id:order.orderId,amount:order.amountMinor,currency:order.currency,name:"CRM Customer Payments",description:`Test invoice INV-${String(invoice.id).padStart(5,"0")}`,prefill:{name:invoice.customerName},theme:{color:"#635bff"},
   handler:result=>{completed=true;void request<Invoice>("verify",{token,...result}).then(i=>{setInvoice(i);setNotice(i.status==="PAID"?"Test payment verified. Your receipt is below.":"Payment is awaiting capture. Use Check payment status to confirm.");}).catch(e=>setError(`${e.message} Use Check payment status before trying another payment.`)).finally(()=>setBusy(false));},
   modal:{ondismiss:()=>{if(!completed){setBusy(false);setNotice("Checkout closed. Use Check payment status if you attempted a payment.");}}}});
  checkout.on("payment.failed",()=>{setNotice("The payment attempt failed. You can retry inside checkout or close it and check payment status.");});checkout.open();
 }catch(e){setError((e as Error).message);setBusy(false);}}
 const settled=invoice?.paidAt!=null;
 return <main className="payment-customer-page crm-workspace"><div className="payment-customer-shell"><header className="customer-title-row"><CreditCard size={30}/><div><span className="page-eyebrow">Customer payments</span><h1>Invoice checkout</h1></div></header><div className="crm-notice payment-test-banner"><strong>Razorpay test mode</strong><span>For demonstration only. No real money will be charged.</span></div>
  {error&&<p className="crm-error" role="alert">{error}</p>}{notice&&<p className="crm-notice" role="status">{notice}</p>}{!invoice&&!error&&<p>Loading invoice…</p>}
  {invoice&&<section className="crm-panel payment-checkout-card"><span className="page-eyebrow">INV-{String(invoice.id).padStart(5,"0")}</span><h2>{invoice.title}</h2><p>Customer: {invoice.customerName}</p><p className="payment-amount">{money(invoice.amountMinor)}</p><p><strong>Status: {invoice.status.replaceAll("_"," ")}</strong></p>
   {settled?<div className="payment-receipt"><ShieldCheck size={28}/><h2>{invoice.status==="PAID"?"Test payment receipt":"Test payment record"}</h2><p>Transaction: {invoice.paymentId}</p><p>Recorded: {invoice.paidAt&&new Date(invoice.paidAt).toLocaleString()}</p><p>This is a demonstration receipt. No real funds were transferred.</p><button onClick={()=>window.print()}>Print receipt</button></div>:<><p>Pay the full invoice amount through Razorpay checkout.</p>{!invoice.configured&&<p role="status">Checkout is not ready yet. Please ask your contact to configure Razorpay test mode.</p>}<button className="primary-btn payment-pay-button" disabled={busy||!invoice.configured||invoice.status==="PROCESSING"} onClick={()=>void pay()}><CreditCard size={18}/>{busy?"Please wait…":`Pay ${money(invoice.amountMinor)}`}</button>{invoice.status==="PROCESSING"&&<p>Payment is authorized and awaiting capture. Please do not pay again.</p>}</>}
   <div className="crm-toolbar"><button disabled={busy||!invoice.configured} onClick={()=>void check()}><RefreshCw size={15}/>Check payment status</button></div><p className="payment-footnote">Payment information is entered in Razorpay checkout. This CRM does not store card numbers or bank credentials.</p>
  </section>}
 </div></main>;
}
