package com.cafeoccidente.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.cafeoccidente.backend.common.config.CorsConfig;
import com.cafeoccidente.backend.common.config.SecurityConfig;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.inventory.service.InventoryReportService;
import com.cafeoccidente.backend.inventory.service.RemissionService;
import com.cafeoccidente.backend.permissions.service.PermissionService;
import com.cafeoccidente.backend.purchases.annulment.service.PurchaseAnnulmentService;
import com.cafeoccidente.backend.purchases.query.service.PurchaseQueryService;
import com.cafeoccidente.backend.purchases.report.service.PurchaseReportService;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementQuotaService;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.future.service.FuturePurchaseService;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseService;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.service.AgencyService;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import com.cafeoccidente.backend.supplies.service.SuppliesEntryService;
import com.cafeoccidente.backend.supplies.service.SuppliesReportService;
import com.cafeoccidente.backend.tables.service.TableAccessService;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.service.AuthService;
import com.cafeoccidente.backend.users.service.UserService;
import com.cafeoccidente.backend.users.service.WorkingAgencyService;
import com.cafeoccidente.backend.vendors.service.VendorReportService;
import com.cafeoccidente.backend.vendors.service.VendorService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Matriz rol x endpoint (RF-01, RN-01.1 a RN-01.3, RN-05.3). Cada endpoint se llama con los 3 roles:
 * 2xx si el rol esta permitido, 403 si no. Los servicios son mocks y la validacion de los body esta
 * apagada: @Valid corre antes que @PreAuthorize y un 400 taparia el 403.
 */
@WebMvcTest
@Import({SecurityConfig.class, CorsConfig.class, EndpointAuthorizationTest.NoBodyValidation.class})
class EndpointAuthorizationTest {

    private static final String A = "ADMINISTRADOR";
    private static final String U = "USUARIO";
    private static final String O = "OPERACIONES";
    private static final List<String> ROLES = List.of(A, U, O);

    private static final Set<String> ADMIN = Set.of(A);
    private static final Set<String> ADMIN_USER = Set.of(A, U);
    private static final Set<String> ADMIN_OPS = Set.of(A, O);
    private static final Set<String> ALL = Set.of(A, U, O);

    /** {@code screen}: la pantalla concedible (RF-02) que tambien abre el endpoint, o null. */
    record Endpoint(HttpMethod method, String pattern, String url, Set<String> roles, String screen) {
        @Override
        public String toString() {
            return method + " " + pattern;
        }
    }

    private static final List<Endpoint> MATRIX = new ArrayList<>();

    /** Body por defecto "{}"; estos no lo aceptan (lista, o boolean primitivo que no admite null). */
    private static final Map<String, String> BODIES = Map.of(
            "/api/remissions/mark-exported", "[]",
            "/api/vendors", "{\"association\": false}");

    /** Las 5 pantallas que se pueden conceder (Screen, ck_permisos_pantalla). */
    private static final List<String> SCREENS =
            List.of("ANULAR_DOCUMENTO", "ACCESO_TABLAS", "ACTUALIZAR_ANUNCIOS", "REGISTRO_CONTROL", "HISTORIAL");

    private static void add(Set<String> roles, HttpMethod method, String pattern, String url) {
        MATRIX.add(new Endpoint(method, pattern, url, roles, null));
    }

    private static void add(Set<String> roles, HttpMethod method, String pattern) {
        add(roles, method, pattern, pattern);
    }

    private static void add(Set<String> roles, String screen, HttpMethod method, String pattern, String url) {
        MATRIX.add(new Endpoint(method, pattern, url, roles, screen));
    }

    static {
        HttpMethod get = HttpMethod.GET;
        HttpMethod post = HttpMethod.POST;
        HttpMethod put = HttpMethod.PUT;
        HttpMethod delete = HttpMethod.DELETE;

        // Administracion: RF-06 Registro de Control, RF-07 usuarios.
        add(ADMIN, get, "/api/control-records");
        add(ADMIN, "REGISTRO_CONTROL", get, "/api/control-records/{agencyId}", "/api/control-records/1");
        add(ADMIN, "REGISTRO_CONTROL", post, "/api/control-records/{agencyId}", "/api/control-records/1");
        add(ADMIN, "REGISTRO_CONTROL", put, "/api/control-records/{agencyId}", "/api/control-records/1");
        // Cafe verde en todas las agencias a la vez (area de compras, 2026-10-26): solo el Administrador.
        add(ADMIN, put, "/api/control-records/green-coffee");
        add(ADMIN, get, "/api/users");
        // Historial de cambios (area de compras, 2026-10-22): el Administrador o con el permiso.
        add(ADMIN, "HISTORIAL", get, "/api/history", "/api/history");
        // RF-02 Permisos temporales: solo el Administrador los concede y retira.
        add(ADMIN, get, "/api/permissions", "/api/permissions?userId=1");
        add(ADMIN, post, "/api/permissions");
        add(ADMIN, delete, "/api/permissions/{userId}/{screen}", "/api/permissions/1/HISTORIAL");
        // RF-03 Acceso a Tablas, solo lectura: el Administrador o con el permiso.
        add(ADMIN, "ACCESO_TABLAS", get, "/api/tables", "/api/tables");
        add(ADMIN, "ACCESO_TABLAS", get, "/api/tables/{code}", "/api/tables/CAJA");
        add(ADMIN, post, "/api/users");
        // Fase 3, C4: editar y reasignar usuarios, solo el Administrador.
        add(ADMIN, put, "/api/users/{id}", "/api/users/1");
        add(ADMIN, get, "/api/roles");

        // Actualizar Anuncios: RF-08 y RF-09 (Administrador y Operaciones).
        add(ADMIN_OPS, "ACTUALIZAR_ANUNCIOS", post, "/api/announcements", "/api/announcements");
        add(ADMIN_OPS, "ACTUALIZAR_ANUNCIOS", post, "/api/announcements/husk", "/api/announcements/husk");
        add(ADMIN_OPS, "ACTUALIZAR_ANUNCIOS", get, "/api/announcements/history", "/api/announcements/history");
        add(ADMIN_OPS, "ACTUALIZAR_ANUNCIOS", get, "/api/announcements/types", "/api/announcements/types?fundId=1");
        add(ADMIN_OPS, "ACTUALIZAR_ANUNCIOS", post, "/api/announcements/types", "/api/announcements/types");
        add(ALL, get, "/api/funds");
        add(ADMIN_USER, get, "/api/announcements/latest", "/api/announcements/latest?fundId=1&specialType=X");
        add(ADMIN, get, "/api/agencies");
        // HU-09 (RF-07): crear agencias, solo el Administrador.
        add(ADMIN, post, "/api/agencies");

        // Compras: RF-13 a RF-20 (seco, verde, pasilla).
        for (String module : List.of("dry-coffee", "green-coffee", "husk")) {
            String base = "/api/purchases/" + module;
            add(ADMIN_USER, post, base);
            add(ADMIN_USER, get, base + "/{id}", base + "/1");
            add(ADMIN_USER, post, base + "/preview");
            add(ADMIN_USER, get, base + "/next-invoice-number");
        }
        add(ADMIN_USER, get, "/api/purchases/dry-coffee/special-info", "/api/purchases/dry-coffee/special-info?fundId=1&specialType=X");
        add(ADMIN_USER, get, "/api/purchases/dry-coffee/quality-percentages");
        add(ADMIN_USER, get, "/api/purchases/green-coffee/announcement-info");
        add(ADMIN_USER, get, "/api/purchases/husk/announcement-info");
        // Generar Reporte Anuncio (Q14: Administrador y Usuario, prima sobre RF-24) y Compras por fecha (RF-25).
        add(ADMIN_USER, get, "/api/purchases/reports/by-date", "/api/purchases/reports/by-date?from=2026-01-01&to=2026-01-01");
        // Consultar Compras (RF-26): Administrador y Usuario.
        add(ADMIN_USER, get, "/api/purchases/query", "/api/purchases/query?invoiceNumber=1");
        add(ADMIN_USER, get, "/api/purchases/query/navigate", "/api/purchases/query/navigate?direction=FIRST");
        add(ADMIN_USER, get, "/api/purchases/query/{module}/{id}", "/api/purchases/query/DRY/1");
        // RN-23.1 Anular Documento: el Administrador o con el permiso (RF-02).
        add(ADMIN, "ANULAR_DOCUMENTO", get, "/api/purchases/annulment", "/api/purchases/annulment?invoiceNumber=1");
        add(ADMIN, "ANULAR_DOCUMENTO", get, "/api/purchases/annulment/navigate",
                "/api/purchases/annulment/navigate?direction=FIRST");
        add(ADMIN, "ANULAR_DOCUMENTO", post, "/api/purchases/annulment/{module}/{id}", "/api/purchases/annulment/DRY/1");

        // Compras a Futuro: RF-21.x (RF-21.1 Asignar Cupo: Administrador y Usuario).
        add(ADMIN_USER, post, "/api/purchases/future");
        add(ADMIN_USER, get, "/api/purchases/future/{id}", "/api/purchases/future/1");
        add(ADMIN_USER, get, "/api/announcement-quotas");
        add(ADMIN_USER, get, "/api/announcement-quotas/{announcementNumber}", "/api/announcement-quotas/1");
        add(ADMIN_USER, put, "/api/announcement-quotas/{announcementNumber}", "/api/announcement-quotas/1");

        // Remesa (RF-11) e Inventarios (RF-22, roles por definir: como hoy).
        add(ADMIN_USER, post, "/api/remissions");
        add(ADMIN_USER, get, "/api/remissions");
        add(ADMIN_USER, get, "/api/remissions/{id}", "/api/remissions/1");
        add(ADMIN_USER, post, "/api/remissions/mark-exported");
        add(ADMIN_USER, get, "/api/inventory-movements");
        add(ADMIN_USER, get, "/api/inventory-movements/{id}", "/api/inventory-movements/1");
        add(ADMIN_USER, get, "/api/inventory-report");
        add(ADMIN_USER, get, "/api/inventory-report/product-codes");

        // Terceros y conductores (RF-28.1).
        add(ADMIN_USER, post, "/api/growers");
        add(ADMIN_USER, get, "/api/growers/{idNumber}", "/api/growers/1");
        add(ADMIN_USER, get, "/api/growers/{idNumber}/program", "/api/growers/1/program");
        add(ADMIN_USER, get, "/api/growers/conductors", "/api/growers/conductors?idNumber=1");
        add(ADMIN_USER, get, "/api/growers/conductors/{idNumber}", "/api/growers/conductors/1");
        add(ADMIN_USER, put, "/api/growers/conductors/{idNumber}", "/api/growers/conductors/1");

        // Suministros: RF-27.x.
        for (String entry : List.of("cash", "cash-adjustments", "petty-cash", "petty-cash-expenses", "supplies",
                "issued-checks", "packaging", "packaging-loans")) {
            add(ADMIN_USER, post, "/api/supplies/" + entry);
        }
        add(ADMIN_USER, get, "/api/supplies/reports/cash");
        add(ADMIN_USER, get, "/api/supplies/reports/petty-cash");
        add(ADMIN_USER, get, "/api/supplies/reports/supplies", "/api/supplies/reports/supplies?fund=RP");
        add(ADMIN_USER, get, "/api/supplies/reports/packaging");
        add(ADMIN_USER, post, "/api/supplies/reports/checks");
        add(ADMIN_USER, get, "/api/supplies/reports/payment-methods",
                "/api/supplies/reports/payment-methods?from=2026-01-01&to=2026-01-31");

        // Vendedores: RF-28.x.
        add(ADMIN_USER, post, "/api/vendors");
        add(ADMIN_USER, get, "/api/vendors/associates");
        add(ADMIN_USER, get, "/api/vendors/associates/{idNumber}", "/api/vendors/associates/1");
        add(ADMIN_USER, get, "/api/vendors/reports/beneficiary");
        add(ADMIN_USER, get, "/api/vendors/ness-quotas");
        add(ADMIN_USER, get, "/api/vendors/ness-quota-balances");

        // Sesion y boton Agencias (RF-05.1, RN-05.2: solo el Administrador elige agencia).
        add(ALL, get, "/api/me");
        add(ADMIN, put, "/api/me/working-agency");

        // Inicio de sesion: publico.
        add(ALL, post, "/api/auth/login");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private SecurityUtils securityUtils;
    @MockitoBean private CurrentAgencyProvider currentAgencyProvider;
    @MockitoBean private WorkingAgencyService workingAgencyService;
    @MockitoBean private AgencyRepository agencyRepository;
    @MockitoBean private AgencyService agencyService;
    @MockitoBean private ChangeHistoryService changeHistoryService;
    @MockitoBean private FundRepository fundRepository;
    @MockitoBean private RoleRepository roleRepository;
    @MockitoBean private AuthService authService;
    @MockitoBean private UserService userService;
    @MockitoBean private ControlRecordService controlRecordService;
    @MockitoBean private InventoryMovementService inventoryMovementService;
    @MockitoBean private InventoryReportService inventoryReportService;
    @MockitoBean private RemissionService remissionService;
    @MockitoBean private PurchaseAnnulmentService purchaseAnnulmentService;
    @MockitoBean private PurchaseReportService purchaseReportService;
    @MockitoBean private PurchaseQueryService purchaseQueryService;
    @MockitoBean private DryCoffeePurchaseService dryCoffeePurchaseService;
    @MockitoBean private AnnouncementService announcementService;
    @MockitoBean private AnnouncementQuotaService announcementQuotaService;
    @MockitoBean private FuturePurchaseService futurePurchaseService;
    @MockitoBean private GreenCoffeePurchaseService greenCoffeePurchaseService;
    @MockitoBean private HuskPurchaseService huskPurchaseService;
    @MockitoBean private GrowerService growerService;
    @MockitoBean private SuppliesEntryService suppliesEntryService;
    @MockitoBean private SuppliesReportService suppliesReportService;
    @MockitoBean private VendorService vendorService;
    @MockitoBean private VendorReportService vendorReportService;
    @MockitoBean private PermissionService permissionService;
    @MockitoBean private TableAccessService tableAccessService;

    /** Los controladores que limitan a la propia agencia preguntan si es Administrador y cual es su agencia. */
    @BeforeEach
    void currentUser() {
        when(securityUtils.isAdmin()).thenAnswer(invocation -> SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + A)));
        when(currentAgencyProvider.currentAgencyId()).thenReturn(1L);
    }

    /**
     * Cada rol, y ademas Usuario y Operaciones con las 5 pantallas concedidas: solo entran a los
     * endpoints de su rol o de una pantalla concedida.
     */
    static Stream<Arguments> matrix() {
        List<String> profiles = new ArrayList<>(ROLES);
        profiles.add(U + "+" + String.join(",", SCREENS));
        profiles.add(O + "+" + String.join(",", SCREENS));
        return MATRIX.stream().flatMap(endpoint -> profiles.stream().map(profile -> Arguments.of(endpoint, profile)));
    }

    @ParameterizedTest(name = "{1} {0}")
    @MethodSource("matrix")
    void eachRoleReachesOnlyItsEndpoints(Endpoint endpoint, String profile) throws Exception {
        String[] parts = profile.split("\\+");
        String role = parts[0];
        List<String> screens = parts.length > 1 ? List.of(parts[1].split(",")) : List.of();
        List<String> authorities = new ArrayList<>(List.of("ROLE_" + role));
        screens.forEach(screen -> authorities.add("SCREEN_" + screen));

        String body = BODIES.getOrDefault(endpoint.pattern(), "{}");
        int status = mockMvc.perform(request(endpoint.method(), endpoint.url())
                        .with(user("prueba").authorities(authorities.stream()
                                .map(SimpleGrantedAuthority::new).toList()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();

        if (endpoint.roles().contains(role) || endpoint.screen() != null && screens.contains(endpoint.screen())) {
            assertThat(status).as("%s con %s", endpoint, profile).isBetween(200, 299);
        } else {
            assertThat(status).as("%s con %s", endpoint, profile).isEqualTo(403);
        }
    }

    /** Un endpoint nuevo sin fila en la matriz hace fallar este test: nadie queda sin revisar. */
    @Test
    void everyEndpointIsInTheMatrix() {
        Set<String> declared = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((RequestMappingInfo info, HandlerMethod handler) -> {
            if (handler.getBeanType().getPackageName().startsWith("com.cafeoccidente")) {
                info.getMethodsCondition().getMethods().forEach(method ->
                        info.getPatternValues().forEach(pattern -> declared.add(method + " " + pattern)));
            }
        });
        Set<String> inMatrix = new TreeSet<>();
        MATRIX.forEach(endpoint -> inMatrix.add(endpoint.toString()));

        assertThat(inMatrix).isEqualTo(declared);
    }

    @TestConfiguration
    static class NoBodyValidation implements WebMvcConfigurer {
        @Override
        public Validator getValidator() {
            return new Validator() {
                @Override
                public boolean supports(Class<?> clazz) {
                    return true;
                }

                @Override
                public void validate(Object target, Errors errors) {
                    // Sin validacion: este test solo mira la autorizacion.
                }
            };
        }
    }
}
