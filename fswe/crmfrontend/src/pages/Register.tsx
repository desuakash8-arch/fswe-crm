import { Link } from "react-router-dom";

function Register() {
  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-header">
          <h1>CRM System</h1>
          <p>Company-managed registration</p>
        </div>
        <p className="login-error" role="status">
          Public registration is disabled so accounts are assigned to the correct company.
          Please contact your company Admin or Manager to create your Sales account.
        </p>
        <div className="auth-switch">
          Already have an account? <Link to="/login">Login</Link>
        </div>
      </div>
    </div>
  );
}

export default Register;
