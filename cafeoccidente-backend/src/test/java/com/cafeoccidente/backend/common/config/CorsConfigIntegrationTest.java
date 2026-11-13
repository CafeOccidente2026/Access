package com.cafeoccidente.backend.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** S5: CORS solo para el origen del frontend, con las cabeceras y los metodos que la app usa. */
@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigIntegrationTest {

    private static final String ORIGIN = "http://localhost:4200";

    @Autowired
    private MockMvc mockMvc;

    private ResultActions preflight(String origin, String method, String headers) throws Exception {
        return mockMvc.perform(options("/api/auth/login").header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", headers));
    }

    @Test
    void theFrontendMaySendItsTokenAndJson() throws Exception {
        preflight(ORIGIN, "POST", "authorization,content-type")
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Headers", "authorization, content-type"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void anotherHeaderIsRejected() throws Exception {
        preflight(ORIGIN, "POST", "x-otra").andExpect(status().isForbidden());
    }

    @Test
    void anUnusedMethodIsRejected() throws Exception {
        preflight(ORIGIN, "PATCH", "authorization").andExpect(status().isForbidden());
    }

    @Test
    void anotherOriginIsRejected() throws Exception {
        preflight("https://otro.ejemplo.com", "GET", "authorization").andExpect(status().isForbidden());
    }
}
