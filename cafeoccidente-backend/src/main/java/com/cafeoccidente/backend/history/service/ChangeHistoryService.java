package com.cafeoccidente.backend.history.service;

import com.cafeoccidente.backend.history.dto.ChangeHistoryFilter;
import com.cafeoccidente.backend.history.dto.ChangeHistoryPage;
import java.util.Map;

/**
 * Historial de cambios del Administrador (area de compras, 2026-10-22). Se llama dentro de la
 * transaccion de cada servicio: si el cambio falla, no queda fila.
 */
public interface ChangeHistoryService {

    /** Entidades que se registran (ck_historial_cambios_entidad). */
    enum Entity { REGISTRO_CONTROL, ANUNCIO, USUARIO, AGENCIA }

    /** Acciones que se registran (ck_historial_cambios_accion). */
    enum Action { CREAR, MODIFICAR, REASIGNAR, ACTIVAR, DESACTIVAR, PUBLICAR }

    /**
     * Guarda solo los campos que cambiaron entre {@code before} y {@code after} (null en una creacion).
     * Sin usuario autenticado (tareas del sistema) o sin cambios no guarda nada y no falla.
     */
    void record(Entity entity, Long entityId, Action action, Long agencyId,
            Map<String, String> before, Map<String, String> after);

    ChangeHistoryPage search(ChangeHistoryFilter filter);
}
