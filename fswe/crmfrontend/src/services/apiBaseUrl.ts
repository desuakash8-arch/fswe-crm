// Vercel supplies the public Render URL at build time; local Vite uses its proxy.
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "/api").trim().replace(/\/+$/, "");
