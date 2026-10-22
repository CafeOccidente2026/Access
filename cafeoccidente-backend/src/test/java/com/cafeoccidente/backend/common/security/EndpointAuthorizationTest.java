package com.cafeoccidente.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.cafeoccidente.backend.common.config.CorsConfig;
import com.cafeoccidente.backend.common.config.SecurityConfig;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.dataexport.service.DataExportService;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.inventory.service.InventoryReportService;
import com.cafeoccidente.backend.inventory.service.RemissionService;
import com.cafeoccidente.backend.purchases.annulment.service.PurchaseAnnulmentService;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementQuotaService;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.future.service.FertiFuturoPurchaseService;
import com.cafeoccidente.backend.purchases.future.service.FuturePurchaseService;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseService;
import com.cafeoccidente.backend.purchases.othercoffee.service.OtherCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import com.cafeoccidente.backend.supplies.service.SuppliesEntryService;
import com.cafeoccidente.backend.supplies.service.SuppliesReportService;
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

    record Endpoint(HttpMethod method, String pattern, String url, Set<String> roles) {
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

    private static void add(Set<String> roles, HttpMethod method, String pattern, String url) {
        MATRIX.add(new Endpoint(method, pattern, url, roles));
    }

    private static void add(Set<String> roles, HttpMethod method, String pattern) {
        add(roles, method, pattern, pattern);
    }

    static {
        HttpMethod get = HttpMethod.GET;
        HttpMethod post = HttpMethod.POST;
        HttpMethod put = HttpMethod.PUT;

        // Administracion: RF-06 Registro de Control, RF-07 usuarios.
        add(ADMIN, get, "/api/control-records");
        add(ADMIN, get, "/api/control-records/{agencyId}", "/api/control-records/1");
        add(ADMIN, post, "/api/control-records/{agencyId}", "/api/control-records/1");
        add(ADMIN, put, "/api/control-records/{agencyId}", "/api/control-records/1");
        add(ADMIN, get, "/api/users");
        add(ADMIN, post, "/api/users");
        add(ADMIN, get, "/api/roles");

        // Actualizar Anuncios: RF-08 y RF-09 (Administrador y Operaciones).
        add(ADMIN_OPS, post, "/api/announcements");
        add(ADMIN_OPS, post, "/api/announcements/husk");
        add(ADMIN_OPS, get, "/api/announcements/history");
        add(ALL, get, "/api/funds");
        add(ADMIN_USER, get, "/api/announcements/latest", "/api/announcements/latest?fundId=1&specialType=X");
        add(ADMIN, get, "/api/agencies");

        // Compras: RF-13 a RF-20 (seco, verde, pasilla) y Cafes Otros (Q5).
        for (String module : List.of("dry-coffee", "green-coffee", "husk", "other-coffee", "ferti-futuro")) {
            String base = "/api/purchases/" + module;
            add(ADMIN_USER, post, base);
            add(ADMIN_USER, get, base + "/{id}", base + "/1");
            add(ADMIN_USER, post, base + "/preview");
            add(ADMIN_USER, get, base + "/next-invoice-number");
        }
        for (String module : List.of("dry-coffee", "other-coffee")) {
            String base = "/api/purchases/" + module;
            add(ADMIN_USER, get, base + "/special-info", base + "/special-info?fundId=1&specialType=X");
            add(ADMIN_USER, get, base + "/quality-percentages");
        }
        add(ADMIN_USER, get, "/api/purchases/green-coffee/announcement-info");
        add(ADMIN_USER, get, "/api/purchases/husk/announcement-info");
        // RF-24 Generar Reporte Anuncio: solo el Administrador.
        add(ADMIN, get, "/api/purchases/dry-coffee/by-date", "/api/purchases/dry-coffee/by-date?date=2026-01-01");
        // RN-23.1 Anular Documento: solo el Administrador.
        add(ADMIN, get, "/api/purchases/annulment", "/api/purchases/annulment?invoiceNumber=1");
        add(ADMIN, post, "/api/purchases/annulment/{module}/{id}", "/api/purchases/annulment/DRY/1");

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

        // Exportar Informacion y Exportado Especial: solo Administrador (se eliminaran).
        add(ADMIN, post, "/api/data-export");
        add(ADMIN, post, "/api/data-export/special", "/api/data-export/special?from=2026-01-01&to=2026-01-31");

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
    @MockitoBean private FundRepository fundRepository;
    @MockitoBean private RoleRepository roleRepository;
    @MockitoBean private AuthService authService;
    @MockitoBean private UserService userService;
    @MockitoBean private ControlRecordService controlRecordService;
    @MockitoBean private DataExportService dataExportService;
    @MockitoBean private InventoryMovementService inventoryMovementService;
    @MockitoBean private InventoryReportService inventoryReportService;
    @MockitoBean private RemissionService remissionService;
    @MockitoBean private PurchaseAnnulmentService purchaseAnnulmentService;
    @MockitoBean private DryCoffeePurchaseService dryCoffeePurchaseService;
    @MockitoBean private AnnouncementService announcementService;
    @MockitoBean private AnnouncementQuotaService announcementQuotaService;
    @MockitoBean private FertiFuturoPurchaseService fertiFuturoPurchaseService;
    @MockitoBean private FuturePurchaseService futurePurchaseService;
    @MockitoBean private GreenCoffeePurchaseService greenCoffeePurchaseService;
    @MockitoBean private HuskPurchaseService huskPurchaseService;
    @MockitoBean private OtherCoffeePurchaseService otherCoffeePurchaseService;
    @MockitoBean private GrowerService growerService;
    @MockitoBean private SuppliesEntryService suppliesEntryService;
    @MockitoBean private SuppliesReportService suppliesReportService;
    @MockitoBean private VendorService vendorService;
    @MockitoBean private VendorReportService vendorReportService;

    static Stream<Arguments> matrix() {
        return MATRIX.stream().flatMap(endpoint -> ROLES.stream().map(role -> Arguments.of(endpoint, role)));
    }

    @ParameterizedTest(name = "{1} {0}")
    @MethodSource("matrix")
    void eachRoleReachesOnlyItsEndpoints(Endpoint endpoint, String role) throws Exception {
        String body = BODIES.getOrDefault(endpoint.pattern(), "{}");
        int status = mockMvc.perform(request(endpoint.method(), endpoint.url())
                        .with(user("prueba").roles(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();

        if (endpoint.roles().contains(role)) {
            assertThat(status).as("%s con %s", endpoint, role).isBetween(200, 299);
        } else {
            assertThat(status).as("%s con %s", endpoint, role).isEqualTo(403);
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
