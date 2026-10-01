package com.cafeoccidente.backend.vendors.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.vendors.dto.BeneficiaryRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaBalanceRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaPage;
import com.cafeoccidente.backend.vendors.repository.VendorReadRepository;
import com.cafeoccidente.backend.vendors.service.VendorReportService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class VendorReportServiceImpl implements VendorReportService {

    static final int NESS_PAGE_SIZE = 50;

    private final VendorReadRepository vendorReadRepository;

    public VendorReportServiceImpl(VendorReadRepository vendorReadRepository) {
        this.vendorReadRepository = vendorReadRepository;
    }

    @Override
    public List<BeneficiaryRow> beneficiary(Long agencyId, String idNumberPrefix, LocalDate from, LocalDate to) {
        if ((from == null) != (to == null)) {
            throw new BusinessRuleException("Debe indicar Desde Fecha y Hasta Fecha");
        }
        if (from != null && from.isAfter(to)) {
            throw new BusinessRuleException("Desde Fecha no puede ser posterior a Hasta Fecha");
        }
        return vendorReadRepository.beneficiary(agencyId, idNumberPrefix, from, to);
    }

    @Override
    public NessQuotaPage nessQuotas(String idNumberPrefix, int page) {
        int safePage = Math.max(0, page);
        return new NessQuotaPage(
                vendorReadRepository.nessQuotas(idNumberPrefix, safePage * NESS_PAGE_SIZE, NESS_PAGE_SIZE),
                vendorReadRepository.countNessQuotas(idNumberPrefix));
    }

    @Override
    public List<NessQuotaBalanceRow> nessQuotaBalances(Long agencyId) {
        return vendorReadRepository.nessQuotaBalances(agencyId);
    }
}
