import { useEffect, useState } from "react";

import {
  LayoutDashboard,
  Users,
  Building2,
  Target,
  MessageSquare,
  BriefcaseBusiness,
  CheckSquare,
  Bell,
  UserCog,
  LogOut,
  ChevronRight,
  Sun,
  Moon,
  Menu,
  CreditCard,
} from "lucide-react";

import {
  Link,
  Outlet,
  useLocation,
  useNavigate,
} from "react-router-dom";

import { getCurrentUser } from "../utils/auth";

function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const user = getCurrentUser();

  const [sidebarOpen, setSidebarOpen] = useState(
    () => window.matchMedia("(min-width: 901px)").matches
  );

  useEffect(() => {
    const media = window.matchMedia("(min-width: 901px)");
    const onChange = () => setSidebarOpen(media.matches);
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, []);

  const closeSidebarOnMobile = () => {
    if (window.matchMedia("(max-width: 900px)").matches) {
      setSidebarOpen(false);
    }
  };

  // Load saved theme
  const [darkMode, setDarkMode] = useState(() => {
    return localStorage.getItem("crmTheme") === "dark";
  });

  // Apply theme to document
  useEffect(() => {
    const root = document.documentElement;

    if (darkMode) {
      root.classList.add("dark-mode");
      root.classList.remove("light-mode");
    } else {
      root.classList.add("light-mode");
      root.classList.remove("dark-mode");
    }

    localStorage.setItem(
      "crmTheme",
      darkMode ? "dark" : "light"
    );
  }, [darkMode]);

  const toggleDarkMode = () => {
    setDarkMode((previous) => !previous);
  };

  const handleLogout = () => {
    localStorage.removeItem("crmUser");
    localStorage.removeItem("crmToken");

    navigate("/login");
  };

  const menuItems = [

    {
      path: "/",
      label: "Dashboard",
      icon: LayoutDashboard,
    },
    {
      path: "/customers",
      label: "Customers",
      icon: Users,
    },
    {
      path: "/leads",
      label: "Leads",
      icon: Target,
    },
    {
      path: "/interactions",
      label: "Interactions",
      icon: MessageSquare,
    },
    {
      path: "/deals",
      label: "Deals",
      icon: BriefcaseBusiness,
    },
    {
      path: "/tasks",
      label: "Tasks",
      icon: CheckSquare,
    },
    {
      path: "/notifications",
      label: "Notifications",
      icon: Bell,
    },

    {path:"/reports",label:"Reports",icon:LayoutDashboard},
    {path:"/payments",label:"Payments",icon:CreditCard},
    {path:"/pipeline",label:"Pipeline",icon:BriefcaseBusiness},
    {path:"/data",label:"Import / export",icon:Building2},
    {path:"/account",label:"Account",icon:UserCog},
  ];


if (user?.role === "ADMIN" || user?.role === "MANAGER") {
  menuItems.push({ path: "/users", label: "Users", icon: UserCog });
  if (user?.role === "ADMIN") {
    menuItems.push({path:"/activity",label:"System activity",icon:Bell});
    menuItems.push({ path: "/companies", label: "Companies", icon: Building2 });
  }
}

  return (
    <div
      className={`
        app-layout
        ${sidebarOpen ? "sidebar-open" : "sidebar-closed"}
      `}
    >

      {/* ================= SIDEBAR ================= */}

      <aside className="sidebar">

        {/* BRAND */}

        <div className="brand">

          <button
            type="button"
            className="brand-mark brand-toggle"
            onClick={() =>
              setSidebarOpen((previous) => !previous)
            }
            title={
              sidebarOpen
                ? "Close sidebar"
                : "Open sidebar"
            }
            aria-label={
              sidebarOpen
                ? "Close sidebar"
                : "Open sidebar"
            }
          >
            C
          </button>

          <div className="brand-text">

            <div className="brand-name">
              CRM
            </div>

            <div className="brand-subtitle">
              Customer Suite
            </div>

          </div>

        </div>


        {/* NAVIGATION */}

        <div className="nav-section">

          <div className="nav-label">
            Overview
          </div>

          <nav className="nav-menu">

            {menuItems.map((item) => {

              const Icon = item.icon;

              const active =
                location.pathname === item.path;

              return (
                <Link
                  key={item.path}
                  to={item.path}
                  className={`
                    nav-item
                    ${active ? "active" : ""}
                  `}
                  onClick={closeSidebarOnMobile}
                  title={
                    !sidebarOpen
                      ? item.label
                      : undefined
                  }
                >

                  <Icon
                    size={19}
                    strokeWidth={1.9}
                  />

                  <span className="nav-item-text">
                    {item.label}
                  </span>

                  {active && sidebarOpen && (
                    <ChevronRight
                      className="nav-arrow"
                      size={16}
                    />
                  )}

                </Link>
              );

            })}

          </nav>

        </div>


        {/* THEME TOGGLE */}

        <div className="theme-toggle-wrapper">

          <button
            type="button"
            className="theme-toggle"
            onClick={toggleDarkMode}
            title={
              darkMode
                ? "Switch to light mode"
                : "Switch to dark mode"
            }
          >

            {darkMode ? (
              <Sun size={19} />
            ) : (
              <Moon size={19} />
            )}

            <span className="theme-toggle-text">
              {darkMode
                ? "Light mode"
                : "Dark mode"}
            </span>

          </button>

        </div>


        {/* USER AREA */}

        <div className="sidebar-bottom">

          <div className="sidebar-user">

            <div className="avatar">

              {user?.name
                ?.charAt(0)
                .toUpperCase() || "U"}

            </div>

            <div className="user-info">

              <div className="user-name">
                {user?.name || "User"}
              </div>

              <div className="user-role">
                {user?.role || "USER"}
              </div>

            </div>

          </div>


          {/* LOGOUT */}

          <button
            type="button"
            className="logout-button"
            onClick={handleLogout}
            title={
              !sidebarOpen
                ? "Sign out"
                : undefined
            }
          >

            <LogOut size={18} />

            <span className="logout-text">
              Sign out
            </span>

          </button>

        </div>

      </aside>


      {/* ================= MAIN CONTENT ================= */}

      <main className="main-content">
        <div className="mobile-topbar">
          <button
            type="button"
            className="mobile-menu-btn"
            onClick={() => setSidebarOpen(true)}
            aria-label="Open menu"
          >
            <Menu size={18} />
          </button>
          <strong>CRM</strong>
        </div>
        <Outlet />
      </main>

    </div>
  );
}

export default Layout;
