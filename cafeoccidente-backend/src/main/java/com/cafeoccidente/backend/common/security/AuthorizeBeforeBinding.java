package com.cafeoccidente.backend.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.util.SimpleMethodInvocation;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Hallazgo 3 (ZAP, 24-nov): {@code @PreAuthorize} se revisa al llamar el metodo, cuando Spring ya leyo y valido la
 * peticion, asi que quien no tenia permiso recibia un 400 con la lista de campos en vez de un 403. Este interceptor
 * revisa antes de leer los datos:
 * <ul>
 *   <li>la misma regla de {@code @PreAuthorize} del metodo (o de su controlador). Las reglas de {@link Roles} no usan
 *       los argumentos del metodo, por eso se pueden evaluar sin ellos;</li>
 *   <li>que un {@code {agencyId}} de la URL sea la agencia de la sesion, salvo para el Administrador (Registro de
 *       Control).</li>
 * </ul>
 * Los controladores siguen revisando lo mismo como segunda barrera.
 */
@Component
public class AuthorizeBeforeBinding implements WebMvcConfigurer, HandlerInterceptor {

    private final PreAuthorizeAuthorizationManager authorization = new PreAuthorizeAuthorizationManager();
    private final SecurityUtils securityUtils;
    private final CurrentAgencyProvider currentAgencyProvider;

    public AuthorizeBeforeBinding(SecurityUtils securityUtils, CurrentAgencyProvider currentAgencyProvider) {
        this.securityUtils = securityUtils;
        this.currentAgencyProvider = currentAgencyProvider;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        AuthorizationResult result = authorization.authorize(
                () -> SecurityContextHolder.getContext().getAuthentication(),
                new SimpleMethodInvocation(method.getBean(), method.getMethod()));
        if (result != null && !result.isGranted()) {
            throw new AccessDeniedException("Access Denied");
        }
        String agencyId = pathVariables(request).get("agencyId");
        if (agencyId != null && !securityUtils.isAdmin()
                && !agencyId.equals(String.valueOf(currentAgencyProvider.currentAgencyId()))) {
            throw new AccessDeniedException("Solo puede trabajar con su agencia");
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> pathVariables(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return variables instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }
}
