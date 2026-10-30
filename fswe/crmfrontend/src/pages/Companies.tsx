import PageSearch from "../components/PageSearch";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Building2,
  Plus,
  X,
  Pencil,
  Trash2,
  Mail,
  Phone,
  MapPin,
} from "lucide-react";
import api from "../services/api";

interface Company {
  companyId: number;
  name: string;
  email?: string;
  phone?: string;
  address?: string;
  status?: string;
}

const emptyForm = {
  name: "",
  email: "",
  phone: "",
  address: "",
  status: "ACTIVE",
};

function Companies() {
  const [companies, setCompanies] = useState<Company[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState(emptyForm);

  const loadCompanies = useCallback(async () => {
    try {
      const response = await api.get<Company[]>("/companies");
      setCompanies(response.data);
    } catch (error) {
      console.error("Failed to load companies:", error);
      alert("Unable to load companies. Check your login and API permissions.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(loadCompanies);
  }, [loadCompanies]);

  const filteredCompanies = useMemo(() => {
    const term = search.trim().toLowerCase();

    return companies.filter((company) =>
      [
        company.name,
        company.email,
        company.phone,
        company.address,
        company.status,
      ].some((value) => value?.toLowerCase().includes(term))
    );
  }, [companies, search]);

  const closeForm = () => {
    setShowForm(false);
    setEditingId(null);
    setForm(emptyForm);
  };

  const handleEdit = (company: Company) => {
    setEditingId(company.companyId);
    setForm({
      name: company.name || "",
      email: company.email || "",
      phone: company.phone || "",
      address: company.address || "",
      status: company.status || "ACTIVE",
    });
    setShowForm(true);
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const payload = {
      name: form.name.trim(),
      email: form.email.trim(),
      phone: form.phone.trim(),
      address: form.address.trim(),
      status: form.status,
    };

    try {
      if (editingId !== null) {
        await api.put(`/companies/${editingId}`, payload);
      } else {
        await api.post("/companies", payload);
      }

      closeForm();
      await loadCompanies();
    } catch (error) {
      console.error("Failed to save company:", error);
      alert("Unable to save company. Check the backend endpoint and validation.");
    }
  };

  const handleDelete = async (company: Company) => {
    if (
      !window.confirm(
        `Delete "${company.name}"? Only proceed if no users or other records depend on it.`
      )
    ) {
      return;
    }

    try {
      await api.delete(`/companies/${company.companyId}`);
      await loadCompanies();
    } catch (error) {
      console.error("Failed to delete company:", error);
      alert(
        "Unable to delete this company. It may have related users or records."
      );
    }
  };

  return (
    <div className="page-container">
      <div className="page-header-modern">
        <div>
          <span className="page-eyebrow">Company Management</span>
          <div className="customer-title-row">
            <Building2 size={28} className="customer-title-icon" />
            <div>
              <h1>Companies</h1>
              <p>Manage registered companies and their contact details.</p>
            </div>
          </div>
        </div>

        <div className="header-actions">
          <PageSearch label="Search companies" value={search} onChange={setSearch}/>

          <button
            type="button"
            className="primary-btn"
            onClick={() => {
              if (showForm) {
                closeForm();
              } else {
                setEditingId(null);
                setForm(emptyForm);
                setShowForm(true);
              }
            }}
          >
            {showForm ? "Close" : <><Plus size={17} /> Add Company</>}
          </button>
        </div>
      </div>

      <div className="customer-summary">
        <div className="summary-card">
          <div className="summary-icon purple">
            <Building2 size={18} />
          </div>
          <div>
            <span>Total Companies</span>
            <strong>{companies.length}</strong>
          </div>
        </div>

        <div className="summary-card">
          <div className="summary-icon green">
            <Building2 size={18} />
          </div>
          <div>
            <span>Active</span>
            <strong>
              {companies.filter((company) => company.status?.toUpperCase() === "ACTIVE").length}
            </strong>
          </div>
        </div>

        <div className="summary-card">
          <div className="summary-icon orange">
            <Building2 size={18} />
          </div>
          <div>
            <span>Inactive</span>
            <strong>
              {companies.filter((company) => company.status?.toUpperCase() === "INACTIVE").length}
            </strong>
          </div>
        </div>
      </div>
{showForm && (
  <div
    className="company-modal-overlay"
    onMouseDown={(event) => {
      if (event.target === event.currentTarget) {
        closeForm();
      }
    }}
  >
    <div
      className="company-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="company-modal-title"
    >
          <div className="modal-header">
            <div>
              <h2 id="company-modal-title">
  {editingId !== null ? "Edit Company" : "Add Company"}
</h2>
              <p>Enter the company information below.</p>
            </div>
            <button type="button" className="modal-close" onClick={closeForm}>
              <X size={18} />
            </button>
          </div>

          <form onSubmit={handleSubmit}>
            <div className="form-grid">
              <div className="form-group">
                <label>Company Name</label>
                <input
                  name="name"
                  value={form.name}
                  onChange={(event) =>
                    setForm((previous) => ({ ...previous, name: event.target.value }))
                  }
                  placeholder="Company name"
                  required
                />
              </div>

              <div className="form-group">
                <label>Email</label>
                <input
                  type="email"
                  name="email"
                  value={form.email}
                  onChange={(event) =>
                    setForm((previous) => ({ ...previous, email: event.target.value }))
                  }
                  placeholder="contact@example.com"
                />
              </div>

              <div className="form-group">
                <label>Phone</label>
                <input
                  name="phone"
                  value={form.phone}
                  onChange={(event) =>
                    setForm((previous) => ({ ...previous, phone: event.target.value }))
                  }
                  placeholder="Contact number"
                />
              </div>

              <div className="form-group">
                <label>Status</label>
                <select
                  name="status"
                  value={form.status}
                  onChange={(event) =>
                    setForm((previous) => ({ ...previous, status: event.target.value }))
                  }
                >
                  <option value="ACTIVE">Active</option>
                  <option value="INACTIVE">Inactive</option>
                </select>
              </div>

              <div className="form-group full-width">
                <label>Address</label>
                <input
                  name="address"
                  value={form.address}
                  onChange={(event) =>
                    setForm((previous) => ({ ...previous, address: event.target.value }))
                  }
                  placeholder="Company address"
                />
              </div>
            </div>

            <div className="modal-actions">
              <button type="button" className="secondary-button" onClick={closeForm}>
                Cancel
              </button>
              <button type="submit" className="primary-button">
                {editingId !== null ? "Save Changes" : <><Plus size={16} /> Create Company</>}
              </button>
            </div>
          </form>
        </div>
  </div>
      )}

      <div className="data-card">
        <div className="data-toolbar">
          <div>
            <strong>Company List</strong>
            <span> {filteredCompanies.length} companies found</span>
          </div>
        </div>

        <div className="table-wrapper">
          <table className="modern-table">
            <thead>
              <tr>
                <th>Company</th>
                <th>Contact</th>
                <th>Address</th>
                <th>Status</th>
                <th className="action-column">Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={5} className="table-empty">Loading companies...</td></tr>
              ) : filteredCompanies.length === 0 ? (
                <tr>
                  <td colSpan={5} className="table-empty">
                    <Building2 size={30} />
                    <strong>No companies found</strong>
                    <span>Try a different search or add a company.</span>
                  </td>
                </tr>
              ) : (
                filteredCompanies.map((company) => (
                  <tr key={company.companyId}>
                    <td>
                      <div className="customer-cell">
                        <div className="customer-avatar">
                          {company.name.charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <strong>{company.name}</strong>
                          <span>ID #{company.companyId}</span>
                        </div>
                      </div>
                    </td>
                    <td>
                      <div className="contact-cell">
                        <span><Mail size={13} /> {company.email || "—"}</span>
                        <span><Phone size={13} /> {company.phone || "—"}</span>
                      </div>
                    </td>
                    <td>
                      <div className="company-cell">
                        <MapPin size={15} />
                        <span>{company.address || "—"}</span>
                      </div>
                    </td>
                    <td>
                      <span className={`status-badge status-${(company.status || "ACTIVE").toLowerCase()}`}>
                        <span className="status-dot" />
                        {company.status || "ACTIVE"}
                      </span>
                    </td>
                    <td>
                      <div className="action-buttons">
                        <button type="button" onClick={() => handleEdit(company)}>
                          <Pencil size={15} /> Edit
                        </button>
                        <button type="button" onClick={() => handleDelete(company)}>
                          <Trash2 size={15} /> Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="table-footer">
          Showing <strong>{filteredCompanies.length}</strong> of{" "}
          <strong>{companies.length}</strong> companies
        </div>
      </div>
    </div>
  );
}

export default Companies;
