package com.cafeoccidente.backend.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Hallazgo 3 (ZAP, 24-nov): {@code @PreAuthorize} se revisa al llamar el metodo, cuando Spring ya leyo y valido la
 * peticion, asi que quien no tenia permiso recibia un 400 con la lista de campos en vez de un 403. Este interceptor
 * evalua la misma regla del metodo (o de su controlador) antes de leer los datos. Las reglas de {@link Roles} no usan
 * los argumentos del metodo, por eso se pueden evaluar sin ellos. La anotacion sigue en los metodos como segunda
 * barrera.
 */
@Component
public class AuthorizeBeforeBinding implements WebMvcConfigurer, HandlerInterceptor {

    private final PreAuthorizeAuthorizationManager authorization = new PreAuthorizeAuthorizationManager();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            AuthorizationResult result = authorization.authorize(
                    () -> SecurityContextHolder.getContext().getAuthentication(),
                    new SimpleMethodInvocation(method.getBean(), method.getMethod()));
            if (result != null && !result.isGranted()) {
                throw new AccessDeniedException("Access Denied");
            }
        }
        return true;
    }
}
