import {useEffect,useState} from "react";
import api from "../services/api";
import type {Health} from "../components/DealHealthBadge";
export function useDealHealth(ids:number[],revision=0) {
  const key=ids.join(",");
  const [result,setResult]=useState<{key:string;data:Record<string,Health>}>({key:"",data:{}});
  useEffect(()=>{
    if(!key)return;
    const controller=new AbortController();
    const fetchHealth=()=>{void api.get<Record<string,Health>>("/deals/health",{params:{ids:key},signal:controller.signal})
      .then(r=>setResult({key,data:r.data})).catch(()=>{if(!controller.signal.aborted)setResult({key,data:{}});});};
    fetchHealth();const timer=window.setInterval(fetchHealth,60000);
    return()=>{controller.abort();window.clearInterval(timer);};
  },[key,revision]);
  return result.key===key?result.data:{};
}
