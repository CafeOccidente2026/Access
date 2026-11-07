package com.cafeoccidente.backend.vendors.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.controlrecord.repository.ControlRecordRepository;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import com.cafeoccidente.backend.vendors.dto.AssociatePage;
import com.cafeoccidente.backend.vendors.dto.AssociateResponse;
import com.cafeoccidente.backend.vendors.dto.VendorCreateRequest;
import com.cafeoccidente.backend.vendors.mapper.AssociateMapper;
import com.cafeoccidente.backend.vendors.service.VendorService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendorServiceImpl implements VendorService {

    private final GrowerRepository growerRepository;
    private final AgencyRepository agencyRepository;
    private final ControlRecordRepository controlRecordRepository;
    private final Clock clock;

    public VendorServiceImpl(
            GrowerRepository growerRepository,
            AgencyRepository agencyRepository,
            ControlRecordRepository controlRecordRepository,
            Clock clock) {
        this.clock = clock;
        this.growerRepository = growerRepository;
        this.agencyRepository = agencyRepository;
        this.controlRecordRepository = controlRecordRepository;
    }

    /**
     * Catalogar Terceros (RF-28.1): FechaAfiliacion = hoy y Tipo = "C" fijos; Agencia = la de trabajo y
     * NumRegistro el de su RegControl, como en Access. La persona juridica guarda la Razon Social (tambien
     * en primer_nombre, que es el nombre que muestran Compras e informes) y la Clasificacion, con
     * Asociacion = Si como "Asociaciones1"; sin Sexo queda "E" (Empresa). Los Si/No que el formulario no
     * toca quedan en No, como en la tabla de Access.
     */
    @Override
    @Transactional
    public AssociateResponse create(VendorCreateRequest request, Long agencyId) {
        String name = request.legalPerson() ? blankToNull(request.businessName()) : blankToNull(request.firstName());
        if (name == null) {
            throw new BusinessRuleException(request.legalPerson() ? "Falta la Razón Social" : "Falta el 1er Nombre");
        }
        if (request.legalPerson() && blankToNull(request.classification()) == null) {
            throw new BusinessRuleException("Falta la Clasificación");
        }
        String idNumber = request.idNumber().trim();
        if (growerRepository.findByIdNumber(idNumber).isPresent()) {
            throw new BusinessRuleException("Ya existe un asociado o vendedor con esa cedula");
        }
        Agency agency = agencyRepository.findById(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));

        Grower grower = new Grower();
        grower.setIdNumber(idNumber);
        grower.setFirstName(name);
        grower.setAgency(agency);
        grower.setAffiliationDate(LocalDate.now(clock));
        grower.setGrowerType("C");
        grower.setAddress(trimOrEmpty(request.address()));
        grower.setRegistryNumber(controlRecordRepository.findByAgencyIdAndActiveTrue(agencyId)
                .map(c -> c.getControlNumber())
                .orElse(null));
        grower.setIsAssociation(request.legalPerson());
        grower.setActive(false);
        grower.setAccepted(false);
        grower.setEligible(false);
        grower.setExported(false);
        grower.setIsNew(false);
        grower.setPhone(trimOrEmpty(request.phone()));
        grower.setPostalCode(blankToNull(request.postalCode()));
        grower.setEmail(blankToNull(request.email()));
        String sex = blankToNull(request.sex());
        if (request.legalPerson()) {
            grower.setBusinessName(name);
            grower.setClassification(request.classification());
            grower.setSex(sex == null ? "E" : sex);
        } else {
            grower.setSecondName(blankToNull(request.secondName()));
            grower.setLastName(blankToNull(request.lastName()));
            grower.setSecondLastName(blankToNull(request.secondLastName()));
            grower.setSex(sex);
        }
        return AssociateMapper.toResponse(growerRepository.save(grower));
    }

    @Override
    public AssociatePage associateAt(long position) {
        Page<Grower> page = growerRepository.findAll(PageRequest.of((int) Math.max(0, position), 1, Sort.by("id")));
        if (page.isEmpty()) {
            throw new ResourceNotFoundException("No hay asociados en esa posicion");
        }
        return new AssociatePage(page.getNumber(), page.getTotalElements(), AssociateMapper.toResponse(page.getContent().get(0)));
    }

    @Override
    public AssociatePage findAssociate(String idNumber) {
        Grower grower = growerRepository.findByIdNumber(idNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("No existe un asociado con esa cedula"));
        return new AssociatePage(
                growerRepository.countByIdLessThan(grower.getId()),
                growerRepository.count(),
                AssociateMapper.toResponse(grower));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
