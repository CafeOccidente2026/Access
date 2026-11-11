package com.cafeoccidente.backend.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.sql.SQLException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Prueba del 11-nov (punto 4): ninguna respuesta de error queda sin motivo. La pantalla muestra el "message" del
 * servidor, asi que este siempre dice que falta o que esta mal, en español.
 */
class GlobalExceptionHandlerTest {

    record Payment(@NotNull BigDecimal cashAmount) {
    }

    record Purchase(@NotNull @Positive BigDecimal grossKg, @NotBlank String idNumber, @NotNull String unknownField,
            @Valid @NotNull Payment payment) {
    }

    @RestController
    static class Endpoints {
        @PostMapping("/purchase")
        void purchase(@Valid @RequestBody Purchase purchase) {
        }

        @GetMapping("/duplicate")
        void duplicate() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new SQLException("ERROR: duplicate key value violates unique constraint \"uq_terceros_identificacion\""));
        }

        @GetMapping("/related")
        void related() {
            throw new DataIntegrityViolationException("x",
                    new SQLException("update or delete on table \"agencias\" violates foreign key constraint \"fk_usuarios_agencia\""));
        }

        @GetMapping("/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("sin nombre de restriccion");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("detalle interno que no debe salir");
        }

        @GetMapping("/missing")
        void missing() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        @GetMapping("/number")
        void number(@RequestParam Integer invoiceNumber) {
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Endpoints())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void aValidationErrorSaysWhichFieldsAreMissingOrWrongInSpanish() throws Exception {
        mvc.perform(post("/purchase").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grossKg\": -5, \"idNumber\": \"\", \"payment\": {}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta o está mal: Cédula, Efectivo, Kilos Brutos, unknownField"))
                .andExpect(jsonPath("$.fieldErrors.grossKg").exists());
    }

    @Test
    void unreadableDataSaysSo() throws Exception {
        mvc.perform(post("/purchase").contentType(MediaType.APPLICATION_JSON).content("{\"grossKg\": \"abc\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.UNREADABLE_MESSAGE));
    }

    @Test
    void aWrongOrMissingParameterIsNamed() throws Exception {
        mvc.perform(get("/number").param("invoiceNumber", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Valor no válido para invoiceNumber: abc"));
        mvc.perform(get("/number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el dato invoiceNumber"));
    }

    @Test
    void aDatabaseRuleSaysWhichOne() throws Exception {
        mvc.perform(get("/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe un registro con ese dato (uq_terceros_identificacion)"));
        mvc.perform(get("/related"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El registro está relacionado con otros datos (fk_usuarios_agencia)"));
        mvc.perform(get("/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.INTEGRITY_MESSAGE));
    }

    @Test
    void anUnexpectedErrorGivesAReferenceButNotTheInternalDetail() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(Matchers.matchesPattern("Error en el servidor \\(ref\\. [0-9a-f]{8}\\)")))
                .andExpect(jsonPath("$.message").value(Matchers.not(Matchers.containsString("detalle interno"))));
    }

    @Test
    void anHttpStatusErrorKeepsItsStatus() throws Exception {
        mvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No se encontró lo que se pidió"));
    }
}
