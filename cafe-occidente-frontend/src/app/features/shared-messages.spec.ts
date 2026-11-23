import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Location } from '@angular/common';
import { Type, WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { ContentService } from '../core/services/content.service';
import { NavigationService } from '../core/services/navigation.service';
import { ConfirmDialogComponent } from '../shared/ui/confirm-dialog/confirm-dialog';
import { AnnouncementUpdateComponent } from './announcement-update/announcement-update';
import { AnnouncementUpdateHuskComponent } from './announcement-update-husk/announcement-update-husk';
import { ConductorFormComponent } from './conductor-form/conductor-form';
import { ControlRecordComponent } from './control-record/control-record';
import { NessQuotaUploadComponent } from './ness-quota-upload/ness-quota-upload';
import { PermissionsComponent } from './permissions/permissions';
import { PurchaseAnnulmentComponent } from './purchase-annulment/purchase-annulment';
import { PurchaseCorrectionComponent } from './purchase-correction/purchase-correction';
import { QuotaAssignmentComponent } from './quota-assignment/quota-assignment';
import { RemissionFormComponent } from './remission-form/remission-form';
import { SuppliesEntryComponent } from './supplies-entry/supplies-entry';
import { UserManagementComponent } from './user-management/user-management';
import { VendorEntryComponent } from './vendor-entry/vendor-entry';
import announcementUpdate from '../../../public/assets/data/announcement-update.json';
import announcementUpdateHusk from '../../../public/assets/data/announcement-update-husk.json';
import controlRecord from '../../../public/assets/data/control-record.json';
import nessQuotaUpload from '../../../public/assets/data/ness-quota-upload.json';
import permissions from '../../../public/assets/data/permissions.json';
import purchaseAnnulment from '../../../public/assets/data/purchase-annulment.json';
import purchaseCorrection from '../../../public/assets/data/purchase-correction.json';
import purchaseQuery from '../../../public/assets/data/purchase-query.json';
import quotaAssignment from '../../../public/assets/data/quota-assignment.json';
import remissionForm from '../../../public/assets/data/remission-form.json';
import suppliesEntry from '../../../public/assets/data/supplies-entry.json';
import userManagement from '../../../public/assets/data/user-management.json';
import vendorEntry from '../../../public/assets/data/vendor-entry.json';

const CONTENT: Record<string, unknown> = {
  'announcement-update': announcementUpdate,
  'announcement-update-husk': announcementUpdateHusk,
  'control-record': controlRecord,
  'ness-quota-upload': nessQuotaUpload,
  permissions,
  'purchase-annulment': purchaseAnnulment,
  'purchase-correction': purchaseCorrection,
  'purchase-query': purchaseQuery,
  'quota-assignment': quotaAssignment,
  'remission-form': remissionForm,
  'supplies-entry': suppliesEntry,
  'user-management': userManagement,
  'vendor-entry': vendorEntry,
};

/** Pantalla y la señal con la que muestra su mensaje (el resultado de guardar o el error). */
const SCREENS: [string, Type<unknown>, string][] = [
  ['Asignar Cupo', QuotaAssignmentComponent, 'savedMessage'],
  ['Asignar Cupo (error)', QuotaAssignmentComponent, 'errorMessage'],
  ['Corregir Compra', PurchaseCorrectionComponent, 'savedNotice'],
  ['Anular', PurchaseAnnulmentComponent, 'message'],
  ['Registrar Salidas (error)', RemissionFormComponent, 'errorMessage'],
  ['Ingresar Conductores', ConductorFormComponent, 'message'],
  ['Ingresar Conductores (error)', ConductorFormComponent, 'error'],
  ['Suministros, Caja Menor y Préstamo Empaques', SuppliesEntryComponent, 'message'],
  ['Catalogar Terceros', VendorEntryComponent, 'message'],
  ['Registro de Control', ControlRecordComponent, 'savedMessage'],
  ['Registro de Control (error)', ControlRecordComponent, 'errorMessage'],
  ['Actualizar Anuncios', AnnouncementUpdateComponent, 'message'],
  ['Actualizar Anuncios (error)', AnnouncementUpdateComponent, 'error'],
  ['Actualizar Anuncios de pasilla', AnnouncementUpdateHuskComponent, 'message'],
  ['Actualizar Anuncios de pasilla (error)', AnnouncementUpdateHuskComponent, 'error'],
  ['Usuarios (editar)', UserManagementComponent, 'editNotice'],
  ['Usuarios (error al editar)', UserManagementComponent, 'editError'],
  ['Usuarios (error al crear)', UserManagementComponent, 'userError'],
  ['Usuarios (agencia)', UserManagementComponent, 'agencyNotice'],
  ['Usuarios (error de agencia)', UserManagementComponent, 'agencyError'],
  ['Permisos', PermissionsComponent, 'message'],
  ['Permisos (error)', PermissionsComponent, 'error'],
  ['Actualiza Cupos NESS', NessQuotaUploadComponent, 'message'],
];

/** 18-nov: todo mensaje sale en el mensaje compartido, que se cierra con Enter o Esc y devuelve el foco. */
describe('every screen shows its messages in the shared dialog', () => {
  for (const [name, component, signalName] of SCREENS) {
    it(`${name}: the message opens the shared dialog and Enter closes it`, async () => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: ContentService, useValue: { loadJson: (n: string) => of(CONTENT[n]) } },
          // Catalogar Terceros cierra al aceptar el guardado (demo del 23-nov): aca no se navega.
          { provide: NavigationService, useValue: { goTo: () => undefined } },
        ],
      });
      const fixture = TestBed.createComponent(component);
      document.body.appendChild(fixture.nativeElement);
      fixture.detectChanges();
      await Promise.resolve();
      TestBed.tick();
      const shown = (fixture.componentInstance as Record<string, WritableSignal<unknown>>)[signalName];

      shown.set('Mensaje de prueba');
      fixture.detectChanges();
      const dialog = (fixture.nativeElement as HTMLElement).querySelector('app-confirm-dialog');
      expect(dialog?.textContent).toContain('Mensaje de prueba');

      (document.activeElement as HTMLElement).blur();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
      fixture.detectChanges();
      expect(shown()).toBeFalsy();
      expect((fixture.nativeElement as HTMLElement).querySelector('app-confirm-dialog')).toBeNull();
      fixture.nativeElement.remove();
    });
  }
});

/** 18-nov: tras guardar en Suministros (y Caja Menor, Prestamo Empaques) y Catalogar Terceros el cursor va al primer
 *  campo para el siguiente registro, como en las compras; tras un error vuelve al campo donde estaba. */
describe('saved messages: the next record on the first field, or close the screen (demo del 23-nov)', () => {
  const navigation = { goTo: vi.fn() };
  const location = { back: vi.fn() };

  async function open(component: Type<unknown>, screenKey?: string) {
    navigation.goTo.mockClear();
    location.back.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: (n: string) => of(CONTENT[n]) } },
        { provide: NavigationService, useValue: navigation },
        { provide: Location, useValue: location },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { screen: screenKey } }, queryParamMap: of(convertToParamMap({})) } },
      ],
    });
    const fixture = TestBed.createComponent(component);
    fixture.detectChanges();
    await Promise.resolve();
    TestBed.tick();
    const screen = fixture.componentInstance as Record<string, WritableSignal<unknown>>;
    const dialog = () => fixture.debugElement.query(By.directive(ConfirmDialogComponent)).componentInstance as ConfirmDialogComponent;
    return { fixture, screen, dialog };
  }

  it('Suministros (not Caja nor Empaque): saved goes to the first field, an error goes back where it was', async () => {
    const { fixture, screen, dialog } = await open(SuppliesEntryComponent, 'supply');
    screen['error'].set(false);
    screen['message'].set('Guardado');
    fixture.detectChanges();
    expect(dialog().focusFirstFieldOnClose).toBe(true);
    dialog().confirmed.emit();
    expect(navigation.goTo).not.toHaveBeenCalled();

    screen['error'].set(true);
    screen['message'].set('No se pudo guardar');
    fixture.detectChanges();
    expect(dialog().focusFirstFieldOnClose).toBe(false);
  });

  for (const key of ['cash', 'packaging']) {
    it(`Suministros ${key}: Aceptar on the saved message closes the screen back to its menu; an error stays`, async () => {
      const { fixture, screen, dialog } = await open(SuppliesEntryComponent, key);
      screen['error'].set(true);
      screen['message'].set('No se pudo guardar');
      fixture.detectChanges();
      dialog().confirmed.emit();
      fixture.detectChanges();
      expect(navigation.goTo).not.toHaveBeenCalled();

      screen['error'].set(false);
      screen['message'].set('Guardado');
      fixture.detectChanges();
      dialog().confirmed.emit();
      expect(navigation.goTo).toHaveBeenCalledWith('/suministros');
    });
  }

  it('Catalogar Terceros: Aceptar on the saved message closes the screen back to its menu; an error stays', async () => {
    const { fixture, screen, dialog } = await open(VendorEntryComponent);
    screen['error'].set(true);
    screen['message'].set('No se pudo guardar');
    fixture.detectChanges();
    dialog().confirmed.emit();
    fixture.detectChanges();
    expect(navigation.goTo).not.toHaveBeenCalled();

    screen['error'].set(false);
    screen['message'].set('Guardado');
    fixture.detectChanges();
    dialog().confirmed.emit();
    expect(navigation.goTo).toHaveBeenCalledWith((vendorEntry as { backRoute: string }).backRoute);
  });

  it('Ingresar Conductores: Aceptar on the saved message goes back where it came from; an error stays', async () => {
    const { fixture, screen, dialog } = await open(ConductorFormComponent);
    screen['error'].set('Cédula, Nombre y Apellidos son obligatorios');
    fixture.detectChanges();
    dialog().confirmed.emit();
    expect(location.back).not.toHaveBeenCalled();

    screen['message'].set('Conductor registrado');
    fixture.detectChanges();
    dialog().confirmed.emit();
    expect(location.back).toHaveBeenCalledTimes(1);
  });
});
