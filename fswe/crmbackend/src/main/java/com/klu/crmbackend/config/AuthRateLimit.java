package com.klu.crmbackend.config;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class AuthRateLimit extends OncePerRequestFilter {
    private final ConcurrentHashMap<String,long[]> attempts=new ConcurrentHashMap<>();
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException {
        if("POST".equals(req.getMethod()) && java.util.Set.of("/api/auth/login","/api/auth/forgot-password","/api/auth/reset-password").contains(req.getRequestURI())) {
            long now=System.currentTimeMillis();String key=req.getRemoteAddr()+req.getRequestURI();
            attempts.entrySet().removeIf(e->now-e.getValue()[0]>900000);
            if(attempts.size()>50000 && !attempts.containsKey(key)) {res.setStatus(429);return;}
            long[] count=attempts.compute(key,(k,v)->v==null?new long[]{now,1}:new long[]{v[0],v[1]+1});
            if(count[1]>10) {res.setStatus(429);res.setHeader("Retry-After",String.valueOf(Math.max(1,(900000-(now-count[0]))/1000)));res.setContentType("application/json");res.getWriter().write("{\"message\":\"Too many attempts. Try again in 15 minutes.\"}");return;}
        } chain.doFilter(req,res);
    }
}
