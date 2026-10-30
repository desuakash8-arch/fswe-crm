import { Navigate, Outlet } from "react-router-dom";
import { getCurrentUser } from "../utils/auth";

export default function AdminRoute() {
  const user = getCurrentUser();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== "ADMIN") return <Navigate to="/" replace />;
  return <Outlet />;
}
