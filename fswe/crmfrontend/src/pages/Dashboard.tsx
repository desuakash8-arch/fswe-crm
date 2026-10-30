import {LayoutDashboard, Users, Target, BriefcaseBusiness, CheckSquare, CalendarDays, Bell} from "lucide-react";
import {useEffect,useState} from "react";
import api from "../services/api";
import type {Row,Page} from "../components/RecordPage";
import {messageOf} from "../utils/records";
interface Report {openPipeline:number;wonValue:number;wonDeals:number;stages:{stage:string;count:number;amount:number}[]}
interface Notification {notificationId:number;message:string;isRead:boolean;user?:{name:string}}
function Dashboard(){
  const [report,setReport]=useState<Report|null>(null),[counts,setCounts]=useState({customers:0,leads:0,deals:0,tasks:0,interactions:0});
  const [interactions,setInteractions]=useState<Row[]>([]),[pendingTasks,setTasks]=useState<Row[]>([]),[notifications,setNotifications]=useState<Notification[]>([]),[error,setError]=useState("");
  useEffect(()=>{const controller=new AbortController();const options={signal:controller.signal};void Promise.all([
    api.get<Report>("/reports",options),api.get<Page<Row>>("/records/customers?size=1",options),api.get<Page<Row>>("/records/leads?size=1",options),api.get<Page<Row>>("/records/deals?size=1",options),api.get<Page<Row>>("/records/tasks?size=5&status=PENDING&sort=dueDate&desc=false",options),api.get<Page<Row>>("/records/interactions?size=5&sort=interactionDate&desc=true",options),api.get<Notification[]>("/notifications/my",options)
  ]).then(([r,c,l,d,t,i,n])=>{setReport(r.data);setCounts({customers:c.data.totalElements,leads:l.data.totalElements,deals:d.data.totalElements,tasks:t.data.totalElements,interactions:i.data.totalElements});setTasks(t.data.content);setInteractions(i.data.content);setNotifications(n.data);}).catch(e=>{if(!controller.signal.aborted)setError(messageOf(e));});return()=>controller.abort();},[]);
  const unreadNotifications=notifications.filter(n=>!n.isRead);
  const totalWonAmount=report?.wonValue??0,totalPipeline=report?.openPipeline??0;
  const formatCurrency=(n:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR",maximumFractionDigits:0}).format(n);
  const getStageAmount=(stage:string)=>report?.stages.find(s=>s.stage===stage)?.amount??0;
  return (
    <div className="crm-page">
      {error&&<p className="crm-error" role="alert">{error}</p>}

      <div className="page-header-modern">

        <div>

          <span className="page-eyebrow">
            Overview
          </span>

          <div className="customer-title-row">
            <LayoutDashboard size={28} className="customer-title-icon" aria-hidden="true"/>
            <div>
              <h1>Dashboard</h1>
              <p>Welcome back. Here's what's happening in your CRM.</p>
            </div>
          </div>

        </div>

        <div className="dashboard-date">
          <CalendarDays size={16} aria-hidden="true"/> CRM Overview
        </div>

      </div>

      <div className="stats-grid-modern">

        <div className="dashboard-stat-card">

          <div className="stat-icon purple">
            <Users size={20} aria-hidden="true"/>
          </div>

          <div>
            <span>Total Customers</span>
            <strong>
              {counts.customers}
            </strong>
          </div>

        </div>

        <div className="dashboard-stat-card">

          <div className="stat-icon blue">
            <Target size={20} aria-hidden="true"/>
          </div>

          <div>
            <span>Total Leads</span>
            <strong>
              {counts.leads}
            </strong>
          </div>

        </div>

        <div className="dashboard-stat-card">

          <div className="stat-icon green">
            <BriefcaseBusiness size={20} aria-hidden="true"/>
          </div>

          <div>
            <span>Total Deals</span>
            <strong>
              {counts.deals}
            </strong>
          </div>

        </div>

        <div className="dashboard-stat-card">

          <div className="stat-icon orange">
            <CheckSquare size={20} aria-hidden="true"/>
          </div>

          <div>
            <span>Pending Tasks</span>
            <strong>
              {counts.tasks}
            </strong>
          </div>

        </div>

      </div>

      <div className="dashboard-two-column">

        <div className="modern-panel">

          <div className="panel-header">

            <div>
              <h2>Sales Overview</h2>
              <span>Current deal pipeline</span>
            </div>

            <strong className="panel-value">
              {formatCurrency(
                totalPipeline
              )}
            </strong>

          </div>

          <div className="pipeline-modern">

            {[
              "LEAD",
              "PROPOSAL",
              "NEGOTIATION",
              "WON",
              "LOST",
            ].map(stage => {

              const count =
                report?.stages.find(s=>s.stage===stage)?.count ?? 0;

              const amount =
                getStageAmount(stage);

              return (
                <div
                  className="pipeline-modern-item"
                  key={stage}
                >

                  <div className="pipeline-label">

                    <span>
                      {stage}
                    </span>

                    <strong>
                      {count}
                    </strong>

                  </div>

                  <div className="pipeline-bar">

                    <div
                      style={{
                        width: `${
                          counts.deals
                            ? Math.max(
                                5,
                                (count /
                                  counts.deals) *
                                  100
                              )
                            : 5
                        }%`,
                      }}
                    />

                  </div>

                  <small>
                    {formatCurrency(
                      amount
                    )}
                  </small>

                </div>
              );
            })}

          </div>

        </div>

        <div className="modern-panel">

          <div className="panel-header">

            <div>
              <h2>Sales Results</h2>
              <span>Won opportunities this month</span>
            </div>

            <span className="success-badge">
              WON
            </span>

          </div>

          <div className="big-metric">

            <strong>
              {formatCurrency(
                totalWonAmount
              )}
            </strong>

            <span>
              Won deal value this month
            </span>

          </div>

          <div className="metric-row">

            <div>
              <strong>
                {report?.wonDeals ?? 0}
              </strong>
              <span>Won Deals</span>
            </div>

            <div>
              <strong>
                {counts.interactions}
              </strong>
              <span>Interactions</span>
            </div>

            <div>
              <strong>
                {unreadNotifications.length}
              </strong>
              <span>Unread</span>
            </div>

          </div>

        </div>

      </div>

      <div className="dashboard-two-column">

        <div className="modern-panel">

          <div className="panel-header">

            <div>
              <h2>Recent Interactions</h2>
              <span>
                Latest customer activity
              </span>
            </div>

          </div>

          <div className="activity-list">

            {interactions
              .slice(0, 5)
              .map(interaction => (

                <div
                  className="activity-item"
                  key={
                    interaction.interactionId
                  }
                >

                  <div className="activity-avatar">
                    {interaction.customer?.name
                      ?.charAt(0)
                      .toUpperCase() ||
                      "C"}
                  </div>

                  <div className="activity-content">

                    <strong>
                      {interaction.customer?.name ||
                        "Customer"}
                    </strong>

                    <span>
                      {interaction.type}
                    </span>

                    <small>
                      {interaction.notes ||
                        "No notes"}
                    </small>

                  </div>

                </div>

              ))}

            {counts.interactions === 0 && (
              <div className="empty-small">
                No interactions found.
              </div>
            )}

          </div>

        </div>

        <div className="modern-panel">

          <div className="panel-header">

            <div>
              <h2>Upcoming Tasks</h2>
              <span>
                Pending work
              </span>
            </div>

          </div>

          <div className="activity-list">

            {pendingTasks
              .slice(0, 5)
              .map(task => (

                <div
                  className="task-preview"
                  key={task.taskId}
                >

                  <div>

                    <strong>
                      {task.title}
                    </strong>

                    <small>
                      Due {task.dueDate}
                    </small>

                  </div>

                  <span
                    className={`priority-badge priority-${(task.priority??"MEDIUM").toLowerCase()}`}
                  >
                    {task.priority}
                  </span>

                </div>

              ))}

            {counts.tasks === 0 && (
              <div className="empty-small">
                No pending tasks.
              </div>
            )}

          </div>

        </div>

      </div>

      <div className="modern-panel">

        <div className="panel-header">

          <div>
            <h2>Notifications</h2>
            <span>
              Latest unread alerts
            </span>
          </div>

          <span className="notification-count">
            {unreadNotifications.length}
          </span>

        </div>

        <div className="dashboard-notifications">

          {unreadNotifications
            .slice(0, 5)
            .map(notification => (

              <div
                className="dashboard-notification"
                key={
                  notification.notificationId
                }
              >

                <div className="notification-icon">
                  <Bell size={18} aria-hidden="true"/>
                </div>

                <div>

                  <strong>
                    {notification.message}
                  </strong>

                  <small>
                    {notification.user?.name}
                  </small>

                </div>

              </div>

            ))}

          {unreadNotifications.length === 0 && (
            <div className="empty-small">
              No unread notifications.
            </div>
          )}

        </div>

      </div>

    </div>
  );
}

export default Dashboard;
