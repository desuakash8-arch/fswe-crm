import PageSearch from "../components/PageSearch";
import { getApiError } from "../utils/apiError";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  UserCog,
  Trash2,
  X,
  Plus,
  Pencil,
} from "lucide-react";
import api from "../services/api";
import { getCurrentUser } from "../utils/auth";

interface User {
  userId: number;
  active: boolean;
  name: string;
  email: string;
  role: string;
  companyId: number | null;
  companyName: string | null;
  managerId: number | null;
  managerName: string | null;
}

interface Company {
  companyId: number;
  name: string;
  status?: string;
}

function Users() {
  const currentUser = getCurrentUser();
  const isManager = currentUser?.role === "MANAGER";
  const [users, setUsers] = useState<User[]>([]);
  const [companies, setCompanies] = useState<Company[]>([]);

  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("ALL");

  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("SALES");
  const [companyId, setCompanyId] = useState("");
  const [managerId, setManagerId] = useState("");

  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(false);

  const getAuthHeader = useCallback(() => {
    const token =
      localStorage.getItem("crmToken") ||
      localStorage.getItem("token");

    return token
      ? { Authorization: `Bearer ${token}` }
      : {};
  }, []);

  // LOAD USERS AND COMPANIES
  const fetchUsers = useCallback(async () => {
    try {
      const response = await api.get("/users", {
        headers: getAuthHeader(),
      });

      setUsers(response.data);
    } catch (caughtError: unknown) {
      const error = getApiError(caughtError);
      console.error("Failed to load users:", error);

      if (error?.response?.status === 403) {
        alert("You do not have permission to manage these users.");
      } else {
        alert(
          error?.response?.data?.message ||
            "Failed to load users."
        );
      }
    }
  }, [getAuthHeader]);


const fetchCompanies = useCallback(async () => {
  try {
    const response = await api.get<Company[]>("/companies", {
      headers: getAuthHeader(),
    });

    setCompanies(
      response.data.filter(
        (company) =>
          company.status?.toUpperCase() === "ACTIVE"
      )
    );
  } catch (caughtError: unknown) {
      const error = getApiError(caughtError);
    console.error("Failed to load companies:", error);
    alert(
      error?.response?.data?.message ||
        "Failed to load companies."
    );
  }
}, [getAuthHeader]);


  useEffect(() => {
    void Promise.resolve().then(() => Promise.all([fetchUsers(), fetchCompanies()]));
  }, [fetchUsers, fetchCompanies]);

  const managers = users.filter(
    (user) => user.role === "MANAGER"
  );

  const availableManagers = managers.filter(
    (manager) =>
      !companyId ||
      String(manager.companyId ?? "") === companyId
  );

  // RESET FORM
  const resetForm = () => {
    setName("");
    setEmail("");
    setPassword("");
    setRole("SALES");
    setCompanyId("");
    setManagerId("");
    setEditingUser(null);
    setShowForm(false);
  };

  // OPEN ADD USER
  const openAddUser = () => {
    resetForm();
    if (isManager) {
      setRole("SALES");
      setCompanyId(companies.length === 1 ? String(companies[0].companyId) : "");
      setManagerId(String(currentUser?.userId ?? ""));
    }
    setShowForm(true);
  };

  // OPEN EDIT USER
  const openEditUser = (user: User) => {
    setEditingUser(user);
    setName(user.name);
    setEmail(user.email);
    setPassword("");
    setRole(user.role);
    setCompanyId(
      user.companyId != null
        ? String(user.companyId)
        : ""
    );
    setManagerId(
      isManager ? String(currentUser?.userId ?? "") :
      user.managerId != null ? String(user.managerId) : ""
    );
    setShowForm(true);
  };

  // CREATE OR UPDATE USER
  const handleSubmit = async (
    event: FormEvent<HTMLFormElement>
  ) => {
    event.preventDefault();

    if (role !== "ADMIN" && !companyId) {
      alert("Please select a company.");
      return;
    }

    if (role === "SALES" && !managerId) {
      alert("Please assign a Manager to this Sales user.");
      return;
    }

    if (!editingUser && !password.trim()) {
      alert("Please enter a password.");
      return;
    }

    const payload = {
      name: name.trim(),
      email: email.trim(),
      password: password.trim() || null,
      role,
      companyId:
        role === "ADMIN" ? null : Number(companyId),
      managerId:
        role === "SALES" ? Number(isManager ? currentUser?.userId : managerId) : null,
    };

    setLoading(true);

    try {
      if (editingUser) {
        await api.put(
          `/users/${editingUser.userId}`,
          payload,
          { headers: getAuthHeader() }
        );

        alert("User updated successfully!");
      } else {
        await api.post(
          "/users",
          payload,
          { headers: getAuthHeader() }
        );

        alert("User created successfully!");
      }

      resetForm();
      await fetchUsers();
    } catch (caughtError: unknown) {
      const error = getApiError(caughtError);
      console.error("USER SAVE ERROR:", error);
      console.error("SERVER RESPONSE:", error?.response?.data);

      alert(
        error?.response?.data?.message ||
          `Failed to ${editingUser ? "update" : "create"} user.`
      );
    } finally {
      setLoading(false);
    }
  };

  // DELETE USER
  const toggleActive = async (user: User) => {
    if (!window.confirm(`${user.active ? "Deactivate" : "Activate"} ${user.name}?`)) return;
    try { await api.patch(`/users/${user.userId}/active`, {active: !user.active}); await fetchUsers(); }
    catch (e) { alert(getApiError(e)?.response?.data?.message || "Unable to change account status"); }
  };
  const handleDelete = async (userId: number) => {
    const confirmed = window.confirm(
      "Are you sure you want to delete this user?"
    );

    if (!confirmed) return;

    try {
      await api.delete(`/users/${userId}`, {
        headers: getAuthHeader(),
      });

      alert("User deleted successfully!");
      await fetchUsers();
    } catch (caughtError: unknown) {
      const error = getApiError(caughtError);
      console.error("DELETE USER ERROR:", error);

      alert(
        error?.response?.data?.message ||
          "Failed to delete user."
      );
    }
  };

  // ROLE FILTER
 
const filteredUsers = users.filter((user) => {
  if (isManager && user.role !== "SALES") {
    return false;
  }

  const matchesSearch = [user.name, user.email, user.role, user.companyName, user.managerName]
    .some(value => value?.toLowerCase().includes(search.trim().toLowerCase()));
  return matchesSearch && (roleFilter === "ALL" || user.role === roleFilter);
});

  const adminCount = users.filter(
    (user) => user.role === "ADMIN"
  ).length;

  const managerCount = users.filter(
    (user) => user.role === "MANAGER"
  ).length;

  const salesCount = users.filter(
    (user) => user.role === "SALES"
  ).length;

  const roleBadgeClass = (userRole: string) => {
    if (userRole === "ADMIN") {
      return "status-badge status-negotiation";
    }

    if (userRole === "MANAGER") {
      return "status-badge status-proposal";
    }

    return "status-badge status-lead";
  };

  return (
    <div className="crm-page users-page">
      {/* PAGE HEADER */}
      <div className="page-header-modern">
        <div>
          <span className="page-eyebrow">
            Administration
          </span>

          <div className="page-title-row">
            <div className="page-title-icon">
              <UserCog size={20} />
            </div>

            <div>
              <h1>Users</h1>
              <p>
                Manage CRM users, companies and team assignments.
              </p>
            </div>
          </div>
        </div>

<div className="header-actions">
<PageSearch label="Search users" value={search} onChange={setSearch}/>
{!isManager && (
  <button
    type="button"
    className="primary-btn"
    onClick={openAddUser}
  >
    <Plus size={17} />
    Add User
  </button>
)}
</div>

      </div>

      {/* ROLE FILTER CARDS */}
      
{/* ROLE FILTER CARDS */}
<div className="stats-row">
  {(isManager
    ? [
        {
          key: "ALL",
          label: "My Sales Team",
          count: users.filter((u) => u.role === "SALES").length,
          icon: "💼",
        },
      ]
    : [
        { key: "ALL", label: "All Users", count: users.length, icon: "👥" },
        { key: "ADMIN", label: "Admins", count: adminCount, icon: "👑" },
        { key: "MANAGER", label: "Managers", count: managerCount, icon: "🧑‍💼" },
        { key: "SALES", label: "Sales", count: salesCount, icon: "💼" },
      ]
  ).map((item) => (
    <button
      key={item.key}
      type="button"
      className={`mini-stat ${
        roleFilter === item.key ? "active" : ""
      }`}
      onClick={() => setRoleFilter(item.key)}
    >
      <span>{item.icon}</span>
      <div>
        <strong>{item.count}</strong>
        <small>{item.label}</small>
      </div>
    </button>
  ))}
</div>

      {/* ADD / EDIT USER MODAL */}
      {showForm && (
        <div
          className="modal-overlay"
          onClick={resetForm}
        >
          <div
            className="modal-card"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="modal-header">
              <div>
                <h2>
                  {editingUser ? "Edit User" : "Create User"}
                </h2>
                <p>
                  {editingUser
                    ? "Update account and team assignments."
                    : "Add a new member to your CRM."}
                </p>
              </div>

              <button
                type="button"
                className="modal-close"
                onClick={resetForm}
                title="Close"
                aria-label="Close"
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSubmit}>
              <div className="form-grid">
                {/* NAME */}
                <div className="form-group">
                  <label>Name</label>
                  <input
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Full name"
                    required
                  />
                </div>

                {/* EMAIL */}
                <div className="form-group">
                  <label>Email</label>
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="user@company.com"
                    required
                  />
                </div>

                {/* PASSWORD */}
                <div className="form-group">
                  <label>
                    Password{" "}
                    {editingUser && "(leave blank to keep current)"}
                  </label>
                  <input
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder={
                      editingUser
                        ? "Optional new password"
                        : "Enter password"
                    }
                    required={!editingUser}
                  />
                </div>

                {/* ROLE */}
                <div className="form-group">
                  <label>Role</label>
                  {isManager ? (
                    <input value="Sales" readOnly aria-readonly="true" />
                  ) : (
                    <select
                      value={role}
                      onChange={(e) => {
                        setRole(e.target.value);
                        setManagerId("");
                      }}
                      required
                    >
                      <option value="SALES">Sales</option>
                      <option value="MANAGER">Manager</option>
                      <option value="ADMIN">Admin</option>
                    </select>
                  )}
                </div>

                {/* COMPANY */}
                {role !== "ADMIN" && (
                  <div className="form-group">
                    <label>Company</label>
                    <select
                      value={companyId}
                      onChange={(e) => {
                        setCompanyId(e.target.value);
                        setManagerId("");
                      }}
                      required
                      disabled={isManager}
                    >
                      <option value="">
                        Select company
                      </option>

                      {companies.map((company) => (
                        <option
                          key={company.companyId}
                          value={company.companyId}
                        >
                          {company.name}
                        </option>
                      ))}
                    </select>
                  </div>
                )}

                {/* MANAGER */}
                {role === "SALES" && (
                  <div className="form-group">
                    <label>Assigned Manager</label>
                    {isManager ? (
                      <input value={currentUser?.name || "Current Manager"} readOnly aria-readonly="true" />
                    ) : (
                    <select
                      value={managerId}
                      onChange={(e) =>
                        setManagerId(e.target.value)
                      }
                      required
                    >
                      <option value="">
                        Select Manager
                      </option>

                      {availableManagers.map((manager) => (
                        <option
                          key={manager.userId}
                          value={manager.userId}
                        >
                          {manager.name} — {manager.email}
                        </option>
                      ))}
                    </select>
                    )}

                    {!isManager && companyId && availableManagers.length === 0 && (
                      <small>No Managers found for this company. Create or assign a Manager first.</small>
                    )}
                  </div>
                )}
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  className="secondary-button"
                  onClick={resetForm}
                  disabled={loading}
                >
                  Cancel
                </button>

                <button
                  type="submit"
                  className="primary-button"
                  disabled={loading}
                >
                  {loading ? (
                    "Saving..."
                  ) : editingUser ? (
                    <>
                      <Pencil size={16} />
                      Save Changes
                    </>
                  ) : (
                    <>
                      <Plus size={16} />
                      Create User
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* USERS TABLE */}
      <div className="modern-table-card data-table-container">
        <div className="table-header">
          <div>
            <h2>Team Members</h2>
            <span>{filteredUsers.length} users found</span>
          </div>
        </div>

        <div className="table-scroll">
          <table className="modern-table data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                <th>Company</th>
                <th>Manager</th>
                <th>Actions</th>
              </tr>
            </thead>

            <tbody>
              {filteredUsers.map((user) => (
                <tr key={user.userId}>
                  <td>{user.userId}</td>

                  <td>
                    <div className="person-cell">
                      <span className="avatar">
                        {user.name
                          .split(" ")
                          .map((part) => part[0])
                          .slice(0, 2)
                          .join("")
                          .toUpperCase()}
                      </span>
                      <strong>{user.name} {user.active ? "" : "(inactive)"}</strong>
                    </div>
                  </td>

                  <td>{user.email}</td>

                  <td>
                    <span className={roleBadgeClass(user.role)}>
                      {user.role}
                    </span>
                  </td>

                  <td>
                    {user.companyName || "—"}
                  </td>

                  <td>
                    {user.managerName || "—"}
                  </td>

                  <td>
                    <div className="action-buttons">
                      {user.userId !== currentUser?.userId && (!isManager || user.role === "SALES") && <button type="button" onClick={() => void toggleActive(user)}>{user.active ? "Deactivate" : "Activate"}</button>}
                      <button
                        type="button"
                        className="icon-btn"
                        title="Edit user"
                        aria-label={`Edit ${user.name}`}
                        onClick={() => openEditUser(user)}
                      >
                        <Pencil size={15} />
                      </button>

                      {!isManager && (
                        <button
                          type="button"
                          className="icon-btn danger"
                          title="Delete user"
                          aria-label={`Delete ${user.name}`}
                          onClick={() => handleDelete(user.userId)}
                        >
                          <Trash2 size={15} />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {filteredUsers.length === 0 && (
            <div className="empty-state">
              <span>👥</span>
              <h3>No users found</h3>
              <p>
                There are no users in this category.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default Users;
