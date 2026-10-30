import { Navigate, Outlet } from "react-router-dom";

import { getCurrentUser } from "../utils/auth";

function ProtectedRoute() {
  const user = getCurrentUser();

  if (!user || !localStorage.getItem("crmToken")) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}

export default ProtectedRoute;