package com.cafeoccidente.backend.dataexport.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * Un libro .xls (Excel 97-2003, el formato de TransferSpreadsheet de Access) con una hoja: fila de
 * encabezados y una fila por registro. Numeros, fechas y Si/No como celdas tipadas; null = celda vacia.
 */
final class XlsSheetWriter {

    private XlsSheetWriter() {
    }

    static byte[] write(String sheetName, List<String> columns, List<List<Object>> rows) {
        try (HSSFWorkbook workbook = new HSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("d/mm/yyyy"));
            Sheet sheet = workbook.createSheet(sheetName);
            Row header = sheet.createRow(0);
            for (int c = 0; c < columns.size(); c++) {
                header.createCell(c).setCellValue(columns.get(c));
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Object> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    Object value = values.get(c);
                    if (value instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else if (value instanceof LocalDate date) {
                        row.createCell(c).setCellValue(date);
                        row.getCell(c).setCellStyle(dateStyle);
                    } else if (value instanceof Boolean flag) {
                        row.createCell(c).setCellValue(flag);
                    } else if (value != null) {
                        row.createCell(c).setCellValue(value.toString());
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
