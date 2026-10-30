import {getApiError} from "./apiError";
export const messageOf=(e:unknown)=>getApiError(e)?.response?.data?.message||"The request failed. Please try again.";
export const stages=["LEAD","PROPOSAL","NEGOTIATION","WON","LOST"];
