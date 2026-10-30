import {Link} from "react-router-dom";
import {ShieldCheck,TriangleAlert,Clock3,CheckCircle2} from "lucide-react";

export interface Health {
  status:"ON_TRACK"|"NEEDS_ATTENTION"|"AT_RISK"|"CLOSED";
  reasons:{code:string;severity:string;message:string}[];
  actions:{kind:"TASKS"|"INTERACTIONS"|"CLOSE_DATE";label:string;description:string}[];
  lastInteraction:string|null;nextAction:string|null;overdueTasks:number;
  evaluatedAt:string;inactiveDays:number;overdueRiskDays:number;
}
const statusInfo={ON_TRACK:{label:"On track",Icon:ShieldCheck},NEEDS_ATTENTION:{label:"Needs attention",Icon:Clock3},AT_RISK:{label:"At risk",Icon:TriangleAlert},CLOSED:{label:"Closed",Icon:CheckCircle2}};
export function HealthStatus({health}:{health:Health}) {
  const {label,Icon}=statusInfo[health.status];
  return <span className={`deal-health-badge health-${health.status.toLowerCase()}`}><Icon size={14} aria-hidden="true"/>{label}</span>;
}
export default function DealHealthBadge({id,health}:{id:number;health?:Health}) {
  return <div className="deal-health-cell">
    {health?<HealthStatus health={health}/>:<span className="deal-health-muted">Health unavailable</span>}
    <Link to={`/deals/${id}/health`}>Why this status?</Link>
  </div>;
}
