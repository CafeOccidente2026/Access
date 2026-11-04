package com.cafeoccidente.backend.vendors.service;

import com.cafeoccidente.backend.purchases.shared.service.NessPrograms;
import com.cafeoccidente.backend.vendors.dto.NessQuotaFile;
import com.cafeoccidente.backend.vendors.dto.NessQuotaRow;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.springframework.stereotype.Component;

/**
 * Lee el Excel de Actualiza Cupos NESS (HU-54, RF-28.4): primera hoja, encabezados en la primera fila. Como
 * el TransferSpreadsheet de Access ("ImportarNess"), cruza las columnas por nombre sin importar mayusculas y
 * no le importa el orden; las columnas de mas se ignoran. Solo valida: no toca la base.
 */
@Component
public class NessQuotaFileReader {

    static final int MAX_ERRORS = 50;
    static final String NOT_XLSX = "Solo se acepta archivo Excel (.xlsx)";
    static final String UNREADABLE = "No se pudo leer el archivo; verifique que sea un Excel (.xlsx)";

    private static final String CEDULA = "CEDULA";
    private static final String NOMBRES = "NOMBRES";
    private static final String PROGRAMA = "PROGRAMA";
    private static final String CUPO = "CUPO";
    private static final List<String> COLUMNS = List.of(CEDULA, NOMBRES, PROGRAMA, CUPO);
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    public NessQuotaFile read(String fileName, InputStream content) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            return failed(List.of(NOT_XLSX));
        }
        List<Row> rows;
        try (ReadableWorkbook workbook = new ReadableWorkbook(content)) {
            rows = workbook.getFirstSheet().read();
        } catch (IOException | RuntimeException e) {
            // fastexcel avisa un archivo que no es .xlsx con excepciones no chequeadas.
            return failed(List.of(UNREADABLE));
        }
        return parse(rows);
    }

    private static NessQuotaFile parse(List<Row> rows) {
        Map<String, Integer> columns = rows.isEmpty() ? Map.of() : headerIndexes(rows.get(0));
        List<String> missing = COLUMNS.stream().filter(c -> !columns.containsKey(c))
                .map(c -> "Falta la columna " + c).toList();
        if (!missing.isEmpty()) {
            return failed(missing);
        }
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, NumberedRow> seen = new LinkedHashMap<>();
        for (Row row : rows.subList(1, rows.size())) {
            NessQuotaRow line = toLine(row, columns, errors);
            if (line != null) {
                keepOnce(new NumberedRow(row.getRowNum(), line), seen, errors, warnings);
            }
        }
        if (errors.isEmpty() && seen.isEmpty()) {
            errors.add("El archivo no trae cupos");
        }
        if (!errors.isEmpty()) {
            return new NessQuotaFile(List.of(), capped(errors), warnings);
        }
        return new NessQuotaFile(seen.values().stream().map(NumberedRow::line).toList(), List.of(), warnings);
    }

    private static Map<String, Integer> headerIndexes(Row header) {
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 0; i < header.getCellCount(); i++) {
            indexes.putIfAbsent(text(header, i).toUpperCase(Locale.ROOT), i);
        }
        return indexes;
    }

    /** La fila convertida, o null si esta vacia o tiene errores (que quedan en {@code errors}). */
    private static NessQuotaRow toLine(Row row, Map<String, Integer> columns, List<String> errors) {
        String rawId = text(row, columns.get(CEDULA));
        String names = text(row, columns.get(NOMBRES));
        String rawProgram = text(row, columns.get(PROGRAMA));
        String rawQuota = text(row, columns.get(CUPO));
        if (rawId.isEmpty() && names.isEmpty() && rawProgram.isEmpty() && rawQuota.isEmpty()) {
            return null;
        }
        String prefix = "Fila " + row.getRowNum() + ": ";
        int before = errors.size();
        String idNumber = idNumber(rawId);
        if (idNumber == null) {
            errors.add(prefix + "la cédula «" + rawId + "» no es válida");
        }
        if (names.isEmpty()) {
            errors.add(prefix + "faltan los nombres");
        }
        String program = program(rawProgram);
        if (program == null) {
            errors.add(prefix + "el programa «" + rawProgram + "» no es " + NessPrograms.NESPRESSO + ", "
                    + NessPrograms.LATE_HARVEST + " ni " + NessPrograms.REGIONAL_NARINO_4C);
        }
        BigDecimal quota = quota(rawQuota);
        if (quota == null) {
            errors.add(prefix + "el cupo «" + rawQuota + "» no es un número válido");
        } else if (quota.signum() < 0) {
            errors.add(prefix + "el cupo «" + rawQuota + "» no puede ser negativo");
        }
        return errors.size() == before ? new NessQuotaRow(idNumber, names, program, quota) : null;
    }

    /** Repetida = misma cedula en el mismo programa: con el mismo cupo se carga una vez; con otro, es error. */
    private static void keepOnce(
            NumberedRow current, Map<String, NumberedRow> seen, List<String> errors, List<String> warnings) {
        NessQuotaRow line = current.line();
        NumberedRow first = seen.putIfAbsent(line.idNumber() + "|" + line.program(), current);
        if (first == null) {
            return;
        }
        String where = "Filas " + first.number() + " y " + current.number() + ": la cédula " + line.idNumber();
        if (first.line().quota().compareTo(line.quota()) != 0) {
            errors.add(where + " tiene dos cupos distintos en " + line.program() + " ("
                    + first.line().quota().toPlainString() + " y " + line.quota().toPlainString() + ")");
            return;
        }
        String warning = where + " se repite en " + line.program() + " con el mismo cupo; se carga una sola vez";
        if (!first.line().names().equals(line.names())) {
            warning += " (nombres distintos: «" + first.line().names() + "» y «" + line.names() + "»; queda «"
                    + first.line().names() + "»)";
        }
        warnings.add(warning);
    }

    private static String text(Row row, int index) {
        if (index >= row.getCellCount()) {
            return "";
        }
        String text = row.getOptionalCell(index).map(Cell::getText).orElse(null);
        return text == null ? "" : text.trim();
    }

    /** Solo digitos; un numero de Excel ("98376216.0" o "9.8376216E7") se vuelve entero si no tiene decimales. */
    private static String idNumber(String raw) {
        String candidate = raw;
        if (!DIGITS.matcher(raw).matches()) {
            try {
                candidate = new BigDecimal(raw).toBigIntegerExact().toString();
            } catch (NumberFormatException | ArithmeticException e) {
                return null;
            }
        }
        return DIGITS.matcher(candidate).matches() ? candidate : null;
    }

    /** El nombre exacto de NessPrograms, sin importar mayusculas ni espacios de mas. */
    private static String program(String raw) {
        String normalized = SPACES.matcher(raw).replaceAll(" ").toUpperCase(Locale.ROOT);
        return NessPrograms.ALL.stream().filter(normalized::equals).findFirst().orElse(null);
    }

    /** Kilos con 2 decimales (Q3), sin ceros de sobra: 30000.00 -> 30000, 994.50 -> 994.5. */
    private static BigDecimal quota(String raw) {
        try {
            BigDecimal quota = new BigDecimal(raw.replace(',', '.')).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
            return quota.scale() < 0 ? quota.setScale(0) : quota;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> capped(List<String> errors) {
        if (errors.size() <= MAX_ERRORS) {
            return errors;
        }
        List<String> capped = new ArrayList<>(errors.subList(0, MAX_ERRORS));
        capped.add("… y " + (errors.size() - MAX_ERRORS) + " errores más");
        return capped;
    }

    private static NessQuotaFile failed(List<String> errors) {
        return new NessQuotaFile(List.of(), errors, List.of());
    }

    private record NumberedRow(int number, NessQuotaRow line) {
    }
}
