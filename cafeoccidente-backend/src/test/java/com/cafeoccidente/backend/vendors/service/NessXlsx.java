package com.cafeoccidente.backend.vendors.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

/** Arma en memoria un .xlsx de prueba: una fila por arreglo; null deja la celda vacia. */
public final class NessXlsx {

    public static final Object[] HEADER = {"CEDULA", "NOMBRES", "PROGRAMA", "CUPO"};

    private NessXlsx() {
    }

    public static byte[] of(Object[]... rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Workbook workbook = new Workbook(out, "Pruebas", "1.0");
            Worksheet sheet = workbook.newWorksheet("NESS");
            for (int r = 0; r < rows.length; r++) {
                for (int c = 0; c < rows[r].length; c++) {
                    Object value = rows[r][c];
                    if (value instanceof Number number) {
                        sheet.value(r, c, number);
                    } else if (value != null) {
                        sheet.value(r, c, value.toString());
                    }
                }
            }
            workbook.finish();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
