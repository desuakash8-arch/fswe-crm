import DealHealth from "./pages/DealHealth";
import Payments from "./pages/Payments";
import CustomerPayment from "./pages/CustomerPayment";
import Activity from "./pages/Activity";
import Recovery from "./pages/Recovery";
import Account from "./pages/Account";
import DataTools from "./pages/DataTools";
import Pipeline from "./pages/Pipeline";
import Reports from "./pages/Reports";
import CustomerDetail from "./pages/CustomerDetail";
import "./crm.css";

import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import "./App.css";
import Layout from "./components/Layout";
import ProtectedRoute from "./components/ProtectedRoute";
import AdminRoute from "./components/AdminRoute";
import UserManagementRoute from "./components/UserManagementRoute";

import Login from "./pages/Login";
import Register from "./pages/Register";
import Users from "./pages/Users";

import Dashboard from "./pages/Dashboard";
import Customers from "./pages/Customers";
import Companies from "./pages/Companies";
import Leads from "./pages/Leads";
import Interactions from "./pages/Interactions";
import Deals from "./pages/Deals";
import Tasks from "./pages/Tasks";
import Notifications from "./pages/Notifications";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/pay" element={<CustomerPayment/>}/>
<Route path="/forgot-password" element={<Recovery/>}/><Route path="/reset-password" element={<Recovery reset/>}/>

        {/* ================= PUBLIC ================= */}

        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />


        {/* ================= PROTECTED ================= */}

        <Route element={<ProtectedRoute />}>

          {/* ================= LAYOUT ================= */}

          <Route element={<Layout />}>
            <Route path="/payments" element={<Payments/>}/>

            {/* Normal authenticated pages */}

            <Route
              path="/"
              element={<Dashboard />}
            />

            <Route path="/customers/:id" element={<CustomerDetail/>}/>
            <Route path="/reports" element={<Reports/>}/><Route path="/deals/:id/health" element={<DealHealth/>}/><Route path="/pipeline" element={<Pipeline/>}/><Route path="/data" element={<DataTools/>}/><Route path="/account" element={<Account/>}/>
            <Route
              path="/customers"
              element={<Customers />}
            />

            <Route
              path="/leads"
              element={<Leads />}
            />

            <Route
              path="/interactions"
              element={<Interactions />}
            />

            <Route
              path="/deals"
              element={<Deals />}
            />

            <Route
              path="/tasks"
              element={<Tasks />}
            />

            <Route
              path="/notifications"
              element={<Notifications />}
            />


            {/* User management: Admin and Manager */}
            <Route element={<UserManagementRoute />}>
              <Route path="/users" element={<Users />} />
            </Route>

            {/* Company administration: Admin only */}
            <Route element={<AdminRoute />}>
<Route path="/activity" element={<Activity/>}/>
              <Route path="/companies" element={<Companies />} />
            </Route>

          </Route>

        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
