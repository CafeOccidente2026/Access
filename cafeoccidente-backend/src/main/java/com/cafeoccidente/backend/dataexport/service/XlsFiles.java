package com.cafeoccidente.backend.dataexport.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Los archivos que Access dejaba en D:\AplicComprasArchivos, juntos en un .zip para descargar. */
public class XlsFiles {

    private final Map<String, byte[]> files = new LinkedHashMap<>();

    public void add(String fileName, String sheetName, List<String> columns, List<List<Object>> rows) {
        files.put(fileName + ".xls", XlsSheetWriter.write(sheetName, columns, rows));
    }

    public byte[] zip() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
