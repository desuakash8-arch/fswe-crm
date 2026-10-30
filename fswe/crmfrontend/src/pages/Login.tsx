import { getApiError } from "../utils/apiError";

import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import api from "../services/api";

function Login() {
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    setError("");

    const cleanEmail = email.trim();

    if (!cleanEmail || !password) {
      setError("Please enter email and password.");
      return;
    }

    setLoading(true);

    try {

      // Remove any old login information first
      localStorage.removeItem("crmToken");
      localStorage.removeItem("crmUser");

      const response = await api.post("/auth/login", {
        email: cleanEmail,
        password: password,
      });

      // =================================================
      // CHECK TOKEN
      // =================================================

      if (!response.data?.token) {
        console.error("No JWT token received:", response.data);

        setError(
          "Login failed: server did not return an authentication token."
        );

        return;
      }

      // =================================================
      // SAVE USER
      // =================================================

      localStorage.setItem(
        "crmUser",
        JSON.stringify(response.data)
      );

      // =================================================
      // SAVE JWT
      // =================================================

      localStorage.setItem(
        "crmToken",
        response.data.token
      );

      // =================================================
      // GO TO DASHBOARD
      // =================================================

      navigate("/", {
        replace: true,
      });

    } catch (caughtError: unknown) {
      const error = getApiError(caughtError);

      console.error("=================================");
      console.error("LOGIN FAILED");
      console.error("Status:", error?.response?.status);
      console.error("Response:", error?.response?.data);
      console.error("Error:", error);
      console.error("=================================");

      const status = error?.response?.status;

      if (status === 429) {setError("Too many attempts. Please try again in 15 minutes.");} else if (status === 401) {
        setError("Invalid email or password.");
      } else if (status === 403) {
        setError(
          "Your account does not have permission to login."
        );
      } else if (status === 404) {
        setError(
          "Login service was not found. Check the backend server."
        );
      } else if (status !== undefined && status >= 500) {
        setError(
          "Server error. Please check the Spring Boot backend."
        );
      } else if (!error?.response) {
        setError(
          "Cannot connect to the backend server."
        );
      } else {
        setError(
          error?.response?.data?.message ||
          "Login failed. Please try again."
        );
      }

    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">

      <div className="login-card">

        {/* =========================
            HEADER
        ========================= */}

        <div className="login-header">

          <h1>
            CRM System
          </h1>

          <p>
            Sign in to your account
          </p>

        </div>

        {/* =========================
            LOGIN FORM
        ========================= */}

        <form onSubmit={handleLogin}>

          {/* EMAIL */}

          <div className="form-group">

            <label htmlFor="login-email">
              Email
            </label>

            <input
              id="login-email"
              type="email"
              value={email}
              onChange={(event) =>
                setEmail(event.target.value)
              }
              placeholder="Enter your email"
              autoComplete="email"
              required
              disabled={loading}
            />

          </div>

          {/* PASSWORD */}

          <div className="form-group">

            <label htmlFor="login-password">
              Password
            </label>

            <input
              id="login-password"
              type="password"
              value={password}
              onChange={(event) =>
                setPassword(event.target.value)
              }
              placeholder="Enter your password"
              autoComplete="current-password"
              required
              disabled={loading}
            />

          </div>

          {/* ERROR */}

          {error && (
            <div className="login-error">
              {error}
            </div>
          )}

          {/* LOGIN BUTTON */}

          <button
            type="submit"
            className="login-button"
            disabled={loading}
          >
            {loading
              ? "Signing in..."
              : "Login"}
          </button>

        <Link to="/forgot-password">Forgot password?</Link>
</form>

        {/* =========================
            REGISTER
        ========================= */}

        <div className="auth-switch">

          Don't have an account?{" "}

          <Link to="/register">
            Register
          </Link>

        </div>

      </div>

    </div>
  );
}

export default Login;
