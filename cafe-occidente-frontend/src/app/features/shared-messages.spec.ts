import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Type, WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it } from 'vitest';

import { ContentService } from '../core/services/content.service';
import { AnnouncementUpdateComponent } from './announcement-update/announcement-update';
import { AnnouncementUpdateHuskComponent } from './announcement-update-husk/announcement-update-husk';
import { ConductorFormComponent } from './conductor-form/conductor-form';
import { ControlRecordComponent } from './control-record/control-record';
import { NessQuotaUploadComponent } from './ness-quota-upload/ness-quota-upload';
import { PermissionsComponent } from './permissions/permissions';
import { PurchaseAnnulmentComponent } from './purchase-annulment/purchase-annulment';
import { PurchaseCorrectionComponent } from './purchase-correction/purchase-correction';
import { QuotaAssignmentComponent } from './quota-assignment/quota-assignment';
import { RemittanceComponent } from './remittance/remittance';
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
import remittance from '../../../public/assets/data/remittance.json';
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
  remittance,
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
  ['Genera Remesa', RemittanceComponent, 'message'],
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
