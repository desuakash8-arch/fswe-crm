export interface LoggedInUser {
  userId: number;
  name: string;
  email: string;
  role: string;
}

export function getCurrentUser(): LoggedInUser | null {
  const user = localStorage.getItem("crmUser");

  if (!user) {
    return null;
  }

  try {
    const parsed: unknown = JSON.parse(user);
    if (!parsed || typeof parsed !== "object") return null;
    const value = parsed as Partial<LoggedInUser>;
    if (typeof value.userId !== "number" || typeof value.name !== "string" ||
        typeof value.email !== "string" || typeof value.role !== "string") return null;
    return { userId: value.userId, name: value.name, email: value.email,
      role: value.role.trim().toUpperCase() };
  } catch {
    return null;
  }
}

export function hasRole(role: string): boolean {
  const user = getCurrentUser();

  return user?.role === role;
}
