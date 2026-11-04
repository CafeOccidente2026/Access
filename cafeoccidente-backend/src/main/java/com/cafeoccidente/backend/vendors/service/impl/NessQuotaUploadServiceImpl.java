package com.cafeoccidente.backend.vendors.service.impl;

import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.vendors.dto.NessQuotaFile;
import com.cafeoccidente.backend.vendors.dto.NessQuotaRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaUploadRecord;
import com.cafeoccidente.backend.vendors.dto.NessQuotaUploadResult;
import com.cafeoccidente.backend.vendors.repository.NessQuotaUploadRepository;
import com.cafeoccidente.backend.vendors.service.NessQuotaFileReader;
import com.cafeoccidente.backend.vendors.service.NessQuotaUploadService;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access ("ImportarNess") borraba NESS y despues importaba el Excel, sin transaccion: si fallaba, la tabla
 * quedaba vacia o a medias. Aqui todo va en una transaccion (RN-28.4.6) y se guarda quien cargo la lista.
 */
@Service
public class NessQuotaUploadServiceImpl implements NessQuotaUploadService {

    private static final int MAX_FILE_NAME = 255;

    private final NessQuotaFileReader reader;
    private final NessQuotaUploadRepository repository;
    private final SecurityUtils securityUtils;

    public NessQuotaUploadServiceImpl(
            NessQuotaFileReader reader, NessQuotaUploadRepository repository, SecurityUtils securityUtils) {
        this.reader = reader;
        this.repository = repository;
        this.securityUtils = securityUtils;
    }

    @Override
    @Transactional(readOnly = true)
    public NessQuotaUploadResult preview(String fileName, byte[] content) {
        NessQuotaFile file = read(fileName, content);
        return file.errors().isEmpty() ? compare(file, repository.currentQuotas()) : NessQuotaUploadResult.rejected(file);
    }

    @Override
    @Transactional
    public NessQuotaUploadResult apply(String fileName, byte[] content) {
        NessQuotaFile file = read(fileName, content);
        if (!file.errors().isEmpty()) {
            return NessQuotaUploadResult.rejected(file);
        }
        repository.lockNess();
        NessQuotaUploadResult result = compare(file, repository.currentQuotas());
        repository.replaceAll(file.lines());
        String name = fileName.length() > MAX_FILE_NAME ? fileName.substring(0, MAX_FILE_NAME) : fileName;
        repository.recordUpload(securityUtils.getCurrentUserId(), name, result);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NessQuotaUploadRecord> history() {
        return repository.history();
    }

    private NessQuotaFile read(String fileName, byte[] content) {
        return reader.read(fileName, new ByteArrayInputStream(content == null ? new byte[0] : content));
    }

    /** Por (cedula, programa): cambia si los nombres o el cupo son distintos; se retira lo que no viene. */
    static NessQuotaUploadResult compare(NessQuotaFile file, List<NessQuotaRow> current) {
        Map<String, NessQuotaRow> before = new HashMap<>();
        current.forEach(row -> before.putIfAbsent(key(row), row));
        Set<String> incoming = new HashSet<>();
        int added = 0;
        int changed = 0;
        for (NessQuotaRow line : file.lines()) {
            incoming.add(key(line));
            NessQuotaRow old = before.get(key(line));
            if (old == null) {
                added++;
            } else if (!same(old, line)) {
                changed++;
            }
        }
        int unchanged = file.lines().size() - added - changed;
        int removed = (int) before.keySet().stream().filter(k -> !incoming.contains(k)).count();
        return new NessQuotaUploadResult(
                List.of(), file.warnings(), file.lines().size(), added, changed, unchanged, removed);
    }

    private static String key(NessQuotaRow row) {
        return row.idNumber() + "|" + row.program();
    }

    private static boolean same(NessQuotaRow old, NessQuotaRow line) {
        return line.names().equals(old.names()) && old.quota() != null && old.quota().compareTo(line.quota()) == 0;
    }
}
