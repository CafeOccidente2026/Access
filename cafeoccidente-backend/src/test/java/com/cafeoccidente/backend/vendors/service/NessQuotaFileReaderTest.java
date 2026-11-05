package com.cafeoccidente.backend.vendors.service;

import static com.cafeoccidente.backend.vendors.service.NessXlsx.HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.vendors.dto.NessQuotaFile;
import com.cafeoccidente.backend.vendors.dto.NessQuotaRow;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * HU-54 (RF-28.4): lectura y validacion del Excel de cupos NESS. Columnas CEDULA, NOMBRES, PROGRAMA y CUPO
 * (las de la tabla NESS de Access); los programas son los 3 que usa Compras; repetida = misma cedula en el
 * mismo programa (decisiones del usuario, 2026-11-04).
 */
class NessQuotaFileReaderTest {

    private final NessQuotaFileReader reader = new NessQuotaFileReader();

    private NessQuotaFile read(String fileName, byte[] content) {
        return reader.read(fileName, new ByteArrayInputStream(content));
    }

    private NessQuotaFile read(Object[]... rows) {
        return read("NESS.xlsx", NessXlsx.of(rows));
    }

    private static NessQuotaRow line(String idNumber, String names, String program, String quota) {
        return new NessQuotaRow(idNumber, names, program, new BigDecimal(quota));
    }

    @Test
    void readsAValidFileNormalizingCedulaProgramAndQuota() {
        NessQuotaFile file = read(HEADER,
                new Object[] {210514, "Paz Carolina Castelblanco", "NESPRESSO", 30000},
                new Object[] {"437702", " Sociedad Diaz ", " nespresso   late harvest ", "30000.00"},
                new Object[] {98376216.0, "Pablo Ruales", "Regional Nariño 4C", 994.5});

        assertThat(file.errors()).isEmpty();
        assertThat(file.warnings()).isEmpty();
        assertThat(file.lines()).containsExactly(
                line("210514", "Paz Carolina Castelblanco", "NESPRESSO", "30000"),
                line("437702", "Sociedad Diaz", "NESPRESSO LATE HARVEST", "30000"),
                line("98376216", "Pablo Ruales", "REGIONAL NARIÑO 4C", "994.5"));
    }

    @Test
    void acceptsHeadersInAnyCaseAndOrderAndIgnoresExtraColumns() {
        NessQuotaFile file = read(
                new Object[] {"cupo", " Cedula ", "OBSERVACION", "programa", "Nombres"},
                new Object[] {500, 210514, "x", "NESPRESSO", "Ana"});

        assertThat(file.errors()).isEmpty();
        assertThat(file.lines()).containsExactly(line("210514", "Ana", "NESPRESSO", "500"));
    }

    @Test
    void namesEveryMissingColumnAndLoadsNothing() {
        NessQuotaFile file = read(new Object[] {"CEDULA", "NOMBRES"}, new Object[] {210514, "Ana"});

        assertThat(file.errors()).containsExactly("Falta la columna PROGRAMA", "Falta la columna CUPO");
        assertThat(file.lines()).isEmpty();
    }

    @Test
    void rejectsAFileThatIsNotXlsx() {
        assertThat(read("NESS.csv", "CEDULA;NOMBRES;PROGRAMA;CUPO".getBytes()).errors())
                .containsExactly("Solo se acepta archivo Excel (.xlsx)");
        assertThat(read("NESS.xlsx", "no es un excel".getBytes()).errors())
                .containsExactly("No se pudo leer el archivo; verifique que sea un Excel (.xlsx)");
    }

    @Test
    void rejectsAFileWithoutQuotas() {
        assertThat(read(HEADER).errors()).containsExactly("El archivo no trae cupos");
    }

    @Test
    void skipsBlankRows() {
        NessQuotaFile file = read(HEADER, new Object[] {null, null, null, null},
                new Object[] {210514, "Ana", "NESPRESSO", 100});

        assertThat(file.errors()).isEmpty();
        assertThat(file.lines()).hasSize(1);
    }

    @Test
    void reportsEachInvalidValueWithItsRow() {
        NessQuotaFile file = read(HEADER,
                new Object[] {"12a", "Ana", "NESPRESSO", 100},
                new Object[] {210514, null, "NESPRESSO", 100},
                new Object[] {210515, "Luis", "RN4C", 100},
                new Object[] {210516, "Eva", "NESPRESSO", "abc"},
                new Object[] {210517, "Rosa", "NESPRESSO", -5});

        assertThat(file.errors()).containsExactly(
                "Fila 2: la cédula «12a» no es válida",
                "Fila 3: faltan los nombres",
                "Fila 4: el programa «RN4C» no es NESPRESSO, NESPRESSO LATE HARVEST ni REGIONAL NARIÑO 4C",
                "Fila 5: el cupo «abc» no es un número válido",
                "Fila 6: el cupo «-5» no puede ser negativo");
    }

    @Test
    void loadsOnceACedulaRepeatedInTheSameProgramWithTheSameQuota() {
        NessQuotaFile file = read(HEADER,
                new Object[] {98376216, "Pablo Giraldo Ruales Portillo", "NESPRESSO", 30000},
                new Object[] {98376216, "Pablo Giraldo Ruales Portillo", "NESPRESSO", 30000});

        assertThat(file.errors()).isEmpty();
        assertThat(file.lines()).containsExactly(
                line("98376216", "Pablo Giraldo Ruales Portillo", "NESPRESSO", "30000"));
        assertThat(file.warnings()).containsExactly(
                "Filas 2 y 3: la cédula 98376216 se repite en NESPRESSO con el mismo cupo; se carga una sola vez");
    }

    @Test
    void keepsTheFirstNameAndWarnsWhenTheRepeatedNamesDiffer() {
        NessQuotaFile file = read(HEADER,
                new Object[] {98376216, "Pablo Giraldo Ruales Portillo", "NESPRESSO", 30000},
                new Object[] {98376216, "Pedro Giraldo Ruales", "NESPRESSO", 30000});

        assertThat(file.lines()).extracting(NessQuotaRow::names).containsExactly("Pablo Giraldo Ruales Portillo");
        assertThat(file.warnings()).containsExactly(
                "Filas 2 y 3: la cédula 98376216 se repite en NESPRESSO con el mismo cupo; se carga una sola vez"
                        + " (nombres distintos: «Pablo Giraldo Ruales Portillo» y «Pedro Giraldo Ruales»;"
                        + " queda «Pablo Giraldo Ruales Portillo»)");
    }

    @Test
    void rejectsACedulaRepeatedInTheSameProgramWithDifferentQuotas() {
        NessQuotaFile file = read(HEADER,
                new Object[] {210514, "Ana", "NESPRESSO", 30000},
                new Object[] {210515, "Luis", "NESPRESSO", 100},
                new Object[] {210514, "Ana", "nespresso", 25000});

        assertThat(file.errors()).containsExactly(
                "Filas 2 y 4: la cédula 210514 tiene dos cupos distintos en NESPRESSO (30000 y 25000)");
    }

    @Test
    void theSameCedulaInTwoProgramsIsNotRepeated() {
        NessQuotaFile file = read(HEADER,
                new Object[] {98376216, "Pablo Giraldo Ruales Portillo", "NESPRESSO", 30000},
                new Object[] {98376216, "Pablo Giraldo Ruales Portillo", "NESPRESSO LATE HARVEST", 30000});

        assertThat(file.errors()).isEmpty();
        assertThat(file.warnings()).isEmpty();
        assertThat(file.lines()).extracting(NessQuotaRow::program)
                .containsExactly("NESPRESSO", "NESPRESSO LATE HARVEST");
    }

    @Test
    void listsAtMostFiftyErrors() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(HEADER);
        for (int i = 0; i < 55; i++) {
            rows.add(new Object[] {"x", "Ana", "NESPRESSO", 1});
        }

        List<String> errors = read(rows.toArray(Object[][]::new)).errors();

        assertThat(errors).hasSize(51);
        assertThat(errors.get(50)).isEqualTo("… y 5 errores más");
    }
}
