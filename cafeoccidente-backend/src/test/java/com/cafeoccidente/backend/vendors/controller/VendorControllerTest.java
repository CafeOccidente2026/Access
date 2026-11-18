package com.cafeoccidente.backend.vendors.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.vendors.service.VendorReportService;
import com.cafeoccidente.backend.vendors.service.VendorService;
import org.junit.jupiter.api.Test;

class VendorControllerTest {

    private final VendorReportService reports = mock(VendorReportService.class);
    private final SecurityUtils security = mock(SecurityUtils.class);
    private final CurrentAgencyProvider agency = mock(CurrentAgencyProvider.class);
    private final VendorController controller =
            new VendorController(mock(VendorService.class), reports, security, agency);

    @Test
    void rn2824UserAskingForAllAgenciesOnlyGetsItsOwnAgency() {
        when(security.isAdmin()).thenReturn(false);
        when(agency.currentAgencyId()).thenReturn(4L);

        controller.beneficiary(true, "99124124", null, null);

        verify(reports).beneficiary(4L, "99124124", null, null);
    }

    @Test
    void rn2824AdminAskingForAllAgenciesGetsEveryAgency() {
        when(security.isAdmin()).thenReturn(true);

        controller.beneficiary(true, "99124124", null, null);

        verify(reports).beneficiary(null, "99124124", null, null);
    }

    @Test
    void rn2823AdminWithoutAllAgenciesGetsTheWorkingAgency() {
        when(security.isAdmin()).thenReturn(true);
        when(agency.currentAgencyId()).thenReturn(4L);

        controller.beneficiary(false, "", null, null);

        verify(reports).beneficiary(4L, "", null, null);
    }
}
