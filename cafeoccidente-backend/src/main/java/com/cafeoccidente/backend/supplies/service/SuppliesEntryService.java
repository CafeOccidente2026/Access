package com.cafeoccidente.backend.supplies.service;

import com.cafeoccidente.backend.supplies.dto.EntryResponse;
import com.cafeoccidente.backend.supplies.dto.LedgerEntryRequest;
import com.cafeoccidente.backend.supplies.dto.PackagingEntryRequest;

/** Altas de las pantallas de "MENUS SUMINISTROS". Cada metodo = un formulario de Access. */
public interface SuppliesEntryService {

    /** "Caja suministros" (Caja, Entradas). */
    EntryResponse createCashSupply(LedgerEntryRequest request);

    /** "Ajustes Caja" (Caja, Salidas, EFECTIVO). */
    EntryResponse createCashAdjustment(LedgerEntryRequest request);

    /** "Caja Menor Suministros" (CajaMenor, Entradas, RP, EFECTIVO). */
    EntryResponse createPettyCashSupply(LedgerEntryRequest request);

    /** "Gastos" (CajaMenor, Salidas, RP, EFECTIVO). */
    EntryResponse createPettyCashExpense(LedgerEntryRequest request);

    /** "SUMINISTROS" (Suministros, Valor_Suministro). */
    EntryResponse createSupply(LedgerEntryRequest request);

    /** "Cheques Girados" (Suministros, Vr_Gastos_o_Comp, CHEQUE). */
    EntryResponse createIssuedCheck(LedgerEntryRequest request);

    /** "Empaque suministros" (Empaque, Entradas). */
    EntryResponse createPackagingEntry(PackagingEntryRequest request);

    /** "Prestamo Empaques" (Empaque, Salidas): el asociado debe existir (RegSalEmp hace INNER JOIN). */
    EntryResponse createPackagingLoan(PackagingEntryRequest request);
}
