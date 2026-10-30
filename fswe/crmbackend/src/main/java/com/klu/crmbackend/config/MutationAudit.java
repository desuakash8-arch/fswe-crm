package com.klu.crmbackend.config;
import com.klu.crmbackend.service.AuditService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import jakarta.servlet.http.*;
@Configuration(proxyBeanMethods=false)
public class MutationAudit implements WebMvcConfigurer {
    private final AuditService audit;public MutationAudit(AuditService audit){this.audit=audit;}
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public void afterCompletion(HttpServletRequest req,HttpServletResponse res,Object handler,Exception error) {
                if(java.util.Set.of("POST","PUT","PATCH","DELETE").contains(req.getMethod()) && res.getStatus()<300 && req.getRequestURI().startsWith("/api/")) {
                    // Never record request bodies, passwords, reset tokens or query strings.
                    audit.record("REQUEST",null,req.getMethod(),req.getRequestURI());
                }
            }
        });
    }
}
