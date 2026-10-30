import axios from "axios";

export function getApiError(error: unknown) {
  return axios.isAxiosError<{ message?: string }>(error) ? error : null;
}
