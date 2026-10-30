
import axios from "axios";
import { API_BASE_URL } from "./apiBaseUrl";

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

// =====================================================
// ADD JWT TO PROTECTED REQUESTS
// =====================================================

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("crmToken");

    // Do NOT add an old JWT to login/register requests
    const isAuthRequest =
      config.url === "/auth/login" ||
      config.url === "/auth/register";

    if (token && !isAuthRequest) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// =====================================================
// RESPONSE INTERCEPTOR
// =====================================================

api.interceptors.response.use(
  (response) => {
    return response;
  },

  (error) => {
    const status = error.response?.status;
    const requestUrl = error.config?.url || "";

    // NEVER redirect while login/register itself is running
    const isAuthRequest =
      requestUrl.includes("/auth/login") ||
      requestUrl.includes("/auth/register");

    if (status === 401 && !isAuthRequest) {
      localStorage.removeItem("crmToken");
      localStorage.removeItem("crmUser");

      window.location.href = "/login";
    }

    return Promise.reject(error);
  }
);

export default api;
