import PageSearch from "../components/PageSearch";

import { useCallback, useEffect, useMemo, useState } from "react";
import api from "../services/api";
import { X, Plus, Bell, BellDot, CheckCheck, Pencil, Trash2 } from "lucide-react";

/* =========================================================
   TYPES
========================================================= */

interface User {
  userId: number;
  name: string;
}

interface AssignedUser {
  userId?: number;
  id?: number;
  name?: string;
}

interface Notification {
  notificationId: number;
  title: string;
  message?: string;
  description?: string;
  type: string;

  isRead?: boolean;
  read?: boolean;

  createdAt?: string;
  date?: string;

  assignedTo?: AssignedUser | null;
  assignedUser?: AssignedUser | null;
  user?: AssignedUser | null;

  assignedToId?: number | null;
  userId?: number | null;
}

/* =========================================================
   COMPONENT
========================================================= */

function Notifications() {
  const [search, setSearch] = useState("");
  const [notifications, setNotifications] =
    useState<Notification[]>([]);

  const [users, setUsers] =
    useState<User[]>([]);

  const [loading, setLoading] =
    useState(true);

  /* =======================================================
     MODAL
  ======================================================= */

  const [showForm, setShowForm] =
    useState(false);

  const [editingId, setEditingId] =
    useState<number | null>(null);

  /* =======================================================
     FILTER
  ======================================================= */

  const [readFilter, setReadFilter] =
    useState("ALL");

  /* =======================================================
     FORM
  ======================================================= */

  const [formData, setFormData] =
    useState({
      title: "",
      message: "",
      type: "GENERAL",
      assignedToId: "",
      isRead: false,
    });

  /* =======================================================
     FETCH DATA
  ======================================================= */



  const fetchData = useCallback(async () => {
    try {
      const [
        notificationsResponse,
        usersResponse,
      ] = await Promise.all([
        api.get<Notification[]>(
          "/notifications"
        ),

        api.get<User[]>(
          "/users/assignable"
        ),
      ]);

      console.log(
        "NOTIFICATIONS API:",
        notificationsResponse.data
      );

      console.log(
        "USERS API:",
        usersResponse.data
      );

      setNotifications(
        notificationsResponse.data
      );

      setUsers(
        usersResponse.data
      );
    } catch (error) {
      console.error(
        "Failed to load notification data:",
        error
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(fetchData);
  }, [fetchData]);

  /* =======================================================
     GET ASSIGNED USER ID
     
     Supports:
     assignedTo.userId
     assignedTo.id
     assignedUser.userId
     assignedUser.id
     user.userId
     user.id
     assignedToId
     userId
  ======================================================= */

  const getAssignedUserId = (
    notification: Notification
  ): number | null => {
    if (
      notification.assignedTo
    ) {
      if (
        notification.assignedTo.userId
      ) {
        return Number(
          notification.assignedTo.userId
        );
      }

      if (
        notification.assignedTo.id
      ) {
        return Number(
          notification.assignedTo.id
        );
      }
    }

    if (
      notification.assignedUser
    ) {
      if (
        notification.assignedUser.userId
      ) {
        return Number(
          notification.assignedUser.userId
        );
      }

      if (
        notification.assignedUser.id
      ) {
        return Number(
          notification.assignedUser.id
        );
      }
    }

    if (
      notification.user
    ) {
      if (
        notification.user.userId
      ) {
        return Number(
          notification.user.userId
        );
      }

      if (
        notification.user.id
      ) {
        return Number(
          notification.user.id
        );
      }
    }

    if (
      notification.assignedToId
    ) {
      return Number(
        notification.assignedToId
      );
    }

    if (
      notification.userId
    ) {
      return Number(
        notification.userId
      );
    }

    return null;
  };

  /* =======================================================
     GET ASSIGNED USER NAME
  ======================================================= */

  const getAssignedUserName = (
    notification: Notification
  ): string => {
    /* First try the name directly
       from the notification */

    if (
      notification.assignedTo?.name
    ) {
      return notification.assignedTo.name;
    }

    if (
      notification.assignedUser?.name
    ) {
      return notification.assignedUser.name;
    }

    if (
      notification.user?.name
    ) {
      return notification.user.name;
    }

    /* Otherwise find user using ID */

    const assignedUserId =
      getAssignedUserId(
        notification
      );

    if (
      assignedUserId !== null
    ) {
      const user =
        users.find(
          (item) =>
            Number(item.userId) ===
            Number(assignedUserId)
        );

      if (user) {
        return user.name;
      }
    }

    return "—";
  };

  /* =======================================================
     FORM CHANGE
  ======================================================= */

  const handleChange = (
    e: React.ChangeEvent<
      HTMLInputElement |
      HTMLSelectElement |
      HTMLTextAreaElement
    >
  ) => {
    const {
      name,
      value,
    } = e.target;

    setFormData({
      ...formData,
      [name]: value,
    });
  };

  /* =======================================================
     RESET FORM
  ======================================================= */

  const resetForm = () => {
    setFormData({
      title: "",
      message: "",
      type: "GENERAL",
      assignedToId: "",
      isRead: false,
    });

    setEditingId(null);
    setShowForm(false);
  };

  /* =======================================================
     ADD NOTIFICATION
  ======================================================= */

  const openAddNotification = () => {
    setEditingId(null);

    setFormData({
      title: "",
      message: "",
      type: "GENERAL",
      assignedToId: "",
      isRead: false,
    });

    setShowForm(true);
  };

  /* =======================================================
     SUBMIT
  ======================================================= */

  const handleSubmit = async (
    e: React.FormEvent<HTMLFormElement>
  ) => {
    e.preventDefault();

    if (!formData.assignedToId) {
      alert(
        "Please select a user."
      );
      return;
    }

    const data = {
      title:
        formData.title,

      message:
        formData.message,

      type:
        formData.type,

      user: {
        userId:
          Number(
            formData.assignedToId
          ),
      },

      isRead:
        formData.isRead,
    };

    console.log(
      "NOTIFICATION REQUEST:",
      data
    );

    try {
      if (
        editingId === null
      ) {
        await api.post(
          "/notifications",
          data
        );
      } else {
        await api.put(
          `/notifications/${editingId}`,
          data
        );
      }

      resetForm();

      await fetchData();
    } catch (error) {
      console.error(
        "Failed to save notification:",
        error
      );

      alert(
        "Failed to save notification"
      );
    }
  };

  /* =======================================================
     EDIT
  ======================================================= */

  const handleEdit = (
    notification: Notification
  ) => {
    const assignedUserId =
      getAssignedUserId(
        notification
      );

    setEditingId(
      notification.notificationId
    );

    setFormData({
      title:
        notification.title || "",

      message:
        notification.message ||
        notification.description ||
        "",

      type:
        notification.type ||
        "GENERAL",

      assignedToId:
        assignedUserId !== null
          ? String(
              assignedUserId
            )
          : "",

      isRead:
        notification.isRead ??
        notification.read ??
        false,
    });

    setShowForm(true);
  };

  /* =======================================================
     DELETE
  ======================================================= */

  const handleDelete = async (
    id: number
  ) => {
    if (
      !window.confirm(
        "Delete this notification?"
      )
    ) {
      return;
    }

    try {
      await api.delete(
        `/notifications/${id}`
      );

      setNotifications(
        (previous) =>
          previous.filter(
            (
              notification
            ) =>
              notification.notificationId !==
              id
          )
      );
    } catch (error) {
      console.error(error);

      alert(
        "Failed to delete notification"
      );
    }
  };

  /* =======================================================
     READ STATUS
  ======================================================= */

  const isNotificationRead = (
    notification: Notification
  ) => {
    return (
      notification.isRead ??
      notification.read ??
      false
    );
  };

  /* =======================================================
     TOGGLE READ
  ======================================================= */

  const toggleReadStatus = async (
    notification: Notification
  ) => {
    const currentStatus =
      isNotificationRead(
        notification
      );

    const newStatus =
      !currentStatus;

    const assignedUserId =
      getAssignedUserId(
        notification
      );

    try {
      await api.put(
        `/notifications/${notification.notificationId}`,
        {
          title:
            notification.title,

          message:
            notification.message ||
            notification.description ||
            "",

          type:
            notification.type,

          user:
            assignedUserId !== null
              ? {
                  userId:
                    assignedUserId,
                }
              : null,

          isRead:
            newStatus,
        }
      );

      setNotifications(
        (previous) =>
          previous.map(
            (
              item
            ) =>
              item.notificationId ===
              notification.notificationId
                ? {
                    ...item,
                    isRead:
                      newStatus,
                  }
                : item
          )
      );
    } catch (error) {
      console.error(
        error
      );

      alert(
        "Failed to update notification"
      );
    }
  };

  /* =======================================================
     FILTER
  ======================================================= */

  const filteredNotifications =
    useMemo(() => {
      return notifications.filter(
        (notification) => {
          const term = search.trim().toLowerCase();
          if (term && ![notification.title, notification.message, notification.description, notification.type].some(value => value?.toLowerCase().includes(term))) return false;
          const read =
            isNotificationRead(
              notification
            );

          if (
            readFilter ===
            "UNREAD"
          ) {
            return !read;
          }

          if (
            readFilter ===
            "READ"
          ) {
            return read;
          }

          return true;
        }
      );
    }, [
      notifications,
      readFilter,
      search,
    ]);

  /* =======================================================
     COUNTS
  ======================================================= */

  const unreadCount =
    notifications.filter(
      (notification) =>
        !isNotificationRead(
          notification
        )
    ).length;

  const readCount =
    notifications.filter(
      (notification) =>
        isNotificationRead(
          notification
        )
    ).length;

  /* =======================================================
     DATE
  ======================================================= */

  const formatDate = (
    date?: string
  ) => {
    if (!date) {
      return "—";
    }

    const parsedDate =
      new Date(date);

    if (
      Number.isNaN(
        parsedDate.getTime()
      )
    ) {
      return date;
    }

    return parsedDate.toLocaleString(
      "en-IN",
      {
        dateStyle:
          "medium",

        timeStyle:
          "short",
      }
    );
  };

  /* =======================================================
     LOADING
  ======================================================= */

  if (loading) {
    return (
      <div className="page-loading">

        <div className="loading-spinner"></div>

        Loading notifications...

      </div>
    );
  }

  /* =======================================================
     UI
  ======================================================= */

  return (
    <div className="crm-page">

      {/* =================================================
          HEADER
      ================================================= */}

      <div className="page-header-modern">

        <div>

          <span className="page-eyebrow">
            Communication
          </span>

          <div className="customer-title-row">
            <Bell size={28} className="customer-title-icon" aria-hidden="true"/>
            <div>
              <h1>Notifications</h1>
              <p>Manage notifications and keep track of important updates.</p>
            </div>
          </div>

        </div>

        <div className="header-actions">
          <PageSearch label="Search notifications" value={search} onChange={setSearch}/>
        <button
          type="button"
          className="primary-btn"
          onClick={
            openAddNotification
          }
        >
          <Plus size={17} />
          Add Notification
        </button>
        </div>

      </div>

      {/* =================================================
          FILTER CARDS
      ================================================= */}

      <div className="stats-row">

        {/* ALL */}

        <button
          type="button"
          className={`mini-stat ${
            readFilter === "ALL"
              ? "active"
              : ""
          }`}
          onClick={() =>
            setReadFilter(
              "ALL"
            )
          }
        >

          <span className="stat-icon purple">
            <Bell size={20} aria-hidden="true"/>
          </span>

          <div>

            <strong>
              {
                notifications.length
              }
            </strong>

            <small>
              All Notifications
            </small>

          </div>

        </button>

        {/* UNREAD */}

        <button
          type="button"
          className={`mini-stat ${
            readFilter === "UNREAD"
              ? "active"
              : ""
          }`}
          onClick={() =>
            setReadFilter(
              "UNREAD"
            )
          }
        >

          <span className="stat-icon blue">
            <BellDot size={20} aria-hidden="true"/>
          </span>

          <div>

            <strong>
              {unreadCount}
            </strong>

            <small>
              Unread
            </small>

          </div>

        </button>

        {/* READ */}

        <button
          type="button"
          className={`mini-stat ${
            readFilter === "READ"
              ? "active"
              : ""
          }`}
          onClick={() =>
            setReadFilter(
              "READ"
            )
          }
        >

          <span className="stat-icon green">
            <CheckCheck size={20} aria-hidden="true"/>
          </span>

          <div>

            <strong>
              {readCount}
            </strong>

            <small>
              Read
            </small>

          </div>

        </button>

      </div>

      {/* =================================================
          TABLE
      ================================================= */}

      <div className="modern-table-card">

        <div className="table-header">

          <div>

            <h2>
              Notification List
            </h2>

            <span>
              {
                filteredNotifications.length
              }{" "}
              notifications found
            </span>

          </div>

        </div>

        <div className="table-scroll">

          <table className="modern-table">

            <thead>

              <tr>

                <th>
                  Notification
                </th>

                <th>
                  Assigned To
                </th>

                <th>
                  Type
                </th>

                <th>
                  Date
                </th>

                <th>
                  Status
                </th>

                <th>
                  Actions
                </th>

              </tr>

            </thead>

            <tbody>

              {filteredNotifications.map(
                (
                  notification
                ) => (

                  <tr
                    key={
                      notification.notificationId
                    }
                  >

                    {/* NOTIFICATION */}

                    <td>

                      <div>

                        <strong>
                          {
                            notification.title || "CRM update"
                          }
                        </strong>

                        <small>
                          {
                            notification.message ||
                            notification.description ||
                            "No description"
                          }
                        </small>

                      </div>

                    </td>

                    {/* ASSIGNED TO */}

                    <td>

                      <strong>
                        {
                          getAssignedUserName(
                            notification
                          )
                        }
                      </strong>

                    </td>

                    {/* TYPE */}

                    <td>

                      <span
                        className={`status-badge status-${(
                          notification.type ||
                          "general"
                        ).toLowerCase()}`}
                      >
                        {
                          notification.type ||
                          "GENERAL"
                        }
                      </span>

                    </td>

                    {/* DATE */}

                    <td>

                      {formatDate(
                        notification.createdAt ||
                        notification.date
                      )}

                    </td>

                    {/* STATUS */}

                    <td>

                      <span
                        className={`status-badge ${
                          isNotificationRead(
                            notification
                          )
                            ? "status-completed"
                            : "status-pending"
                        }`}
                      >
                        {isNotificationRead(
                          notification
                        )
                          ? "READ"
                          : "UNREAD"}
                      </span>

                    </td>

                    {/* ACTIONS */}

                    <td>

                      <div className="action-buttons">

                        <button
                          type="button"
                          className="icon-btn"
                          onClick={() =>
                            handleEdit(
                              notification
                            )
                          }
                          title="Edit notification"
                        >
                          <Pencil size={15} aria-hidden="true"/>
                        </button>

                        <button
                          type="button"
                          className="icon-btn"
                          onClick={() =>
                            toggleReadStatus(
                              notification
                            )
                          }
                          title={
                            isNotificationRead(
                              notification
                            )
                              ? "Mark unread"
                              : "Mark read"
                          }
                        >
                          {isNotificationRead(
                            notification
                          )
                            ? <BellDot size={15} aria-hidden="true"/>
                            : <CheckCheck size={15} aria-hidden="true"/>}
                        </button>

                        <button
                          type="button"
                          className="icon-btn danger"
                          onClick={() =>
                            handleDelete(
                              notification.notificationId
                            )
                          }
                          title="Delete notification"
                        >
                          <Trash2 size={15} aria-hidden="true"/>
                        </button>

                      </div>

                    </td>

                  </tr>

                )
              )}

            </tbody>

          </table>

          {/* EMPTY */}

          {filteredNotifications.length ===
            0 && (

            <div className="empty-state">

              <span>
                <Bell size={30} aria-hidden="true"/>
              </span>

              <h3>
                No notifications found
              </h3>

              <p>
                Try changing the
                notification filter.
              </p>

            </div>

          )}

        </div>

      </div>

      {/* =================================================
          ADD / EDIT MODAL
          SAME DESIGN AS DEALS
      ================================================= */}

      {showForm && (

        <div
          className="modal-overlay"
          onClick={
            resetForm
          }
        >

          <div
            className="modal-card"
            onClick={(
              event
            ) =>
              event.stopPropagation()
            }
          >

            {/* MODAL HEADER */}

            <div className="modal-header">

              <div>

                <h2>

                  {editingId ===
                  null
                    ? "Add New Notification"
                    : "Edit Notification"}

                </h2>

                <p>

                  {editingId ===
                  null
                    ? "Create a new notification and assign it to a user."
                    : "Update the notification details."}

                </p>

              </div>

              {/* REAL X BUTTON */}

              <button
                type="button"
                className="modal-close"
                onClick={
                  resetForm
                }
                title="Close"
                aria-label="Close"
              >
                <X size={18} />
              </button>

            </div>

            {/* FORM */}

            <form
              onSubmit={
                handleSubmit
              }
            >

              <div className="form-grid">

                {/* TITLE */}

                <div className="form-group">

                  <label>
                    Title
                  </label>

                  <input
                    name="title"
                    value={
                      formData.title
                    }
                    onChange={
                      handleChange
                    }
                    placeholder="Follow-up reminder"
                    required
                  />

                </div>

                {/* TYPE */}

                <div className="form-group">

                  <label>
                    Type
                  </label>

                  <select
                    name="type"
                    value={
                      formData.type
                    }
                    onChange={
                      handleChange
                    }
                  >

                    <option value="GENERAL">
                      GENERAL
                    </option>

                    <option value="TASK">
                      TASK
                    </option>

                    <option value="DEAL">
                      DEAL
                    </option>

                    <option value="LEAD">
                      LEAD
                    </option>

                    <option value="SYSTEM">
                      SYSTEM
                    </option>

                    <option value="REMINDER">
                      REMINDER
                    </option>

                  </select>

                </div>

                {/* ASSIGNED TO */}

                <div className="form-group">

                  <label>
                    Assigned To
                  </label>

                  <select
                    name="assignedToId"
                    value={
                      formData.assignedToId
                    }
                    onChange={
                      handleChange
                    }
                    required
                  >

                    <option value="">
                      Select User
                    </option>

                    {users.map(
                      (
                        user
                      ) => (

                        <option
                          key={
                            user.userId
                          }
                          value={
                            user.userId
                          }
                        >
                          {
                            user.name
                          }
                        </option>

                      )
                    )}

                  </select>

                </div>

                {/* STATUS */}

                <div className="form-group">

                  <label>
                    Status
                  </label>

                  <select
                    name="isRead"
                    value={
                      formData.isRead
                        ? "true"
                        : "false"
                    }
                    onChange={(
                      e
                    ) =>
                      setFormData({
                        ...formData,
                        isRead:
                          e.target.value ===
                          "true",
                      })
                    }
                  >

                    <option value="false">
                      UNREAD
                    </option>

                    <option value="true">
                      READ
                    </option>

                  </select>

                </div>

                {/* MESSAGE */}

                <div className="form-group full-width">

                  <label>
                    Message / Description
                  </label>

                  <textarea
                    name="message"
                    value={
                      formData.message
                    }
                    onChange={
                      handleChange
                    }
                    rows={4}
                    placeholder="Notification details..."
                    required
                  />

                </div>

              </div>

              {/* MODAL ACTIONS */}

              <div className="modal-actions">

                <button
                  type="button"
                  className="secondary-button"
                  onClick={
                    resetForm
                  }
                >
                  Cancel
                </button>

                <button
                  type="submit"
                  className="primary-button"
                >

                  <Plus size={16} />

                  {editingId ===
                  null
                    ? "Create Notification"
                    : "Update Notification"}

                </button>

              </div>

            </form>

          </div>

        </div>

      )}

    </div>
  );
}

export default Notifications;
