import { WritableSignal } from '@angular/core';

import { Grower } from '../../core/models/grower.model';
import { GrowerService } from '../../core/services/grower.service';

/** Lo que usa la busqueda del vendedor de un formulario de compra. */
interface SellerLookupForm {
  readonly model: Record<string, string | undefined>;
  readonly errorMessage: WritableSignal<string | null>;
  readonly blockedByDeceased: WritableSignal<boolean>;
  readonly messages: () => { readonly deceasedBlocked?: string; readonly idNumberNotFound?: string } | undefined;
}

/**
 * Bloquea los datos del vendedor que vinieron llenos. Uno vacio (p.ej. un caficultor historico sin celular)
 * queda editable: si se bloqueara vacio, como esta en REQUIRED, la cascada de calculo nunca se dispararia.
 */
export function lockFilled(model: Record<string, string | undefined>, locked: Set<string>, keys: readonly string[]): void {
  keys.filter((k) => (model[k] ?? '').trim() !== '').forEach((k) => locked.add(k));
}

/** Seco, verde y pasilla: el renglon del vendedor como en Access (nombres y apellidos en dos cuadros). */
export function fillSeller(model: Record<string, string | undefined>, locked: Set<string>, grower: Grower): void {
  model['firstName'] = [grower.firstName, grower.secondName].filter(Boolean).join(' ');
  model['lastName'] = [grower.lastName, grower.secondLastName].filter(Boolean).join(' ');
  model['idType'] = grower.growerType;
  model['address'] = grower.address;
  model['cellphone'] = grower.phone;
  locked.add('idPart1');
  lockFilled(model, locked, ['firstName', 'lastName', 'idType', 'address', 'cellphone']);
}

/**
 * Seco, verde y pasilla (paso 4): busca el vendedor por cedula. Un fallecido bloquea el formulario; una cedula
 * que no existe se avisa y no deja avanzar hasta que se corrija. Con el vendedor lleno, sigue con "found".
 */
export function lookupSeller(
  form: SellerLookupForm,
  growers: GrowerService,
  locked: Set<string>,
  repaint: () => void,
  found: () => void,
): void {
  const idNumber = (form.model['idPart1'] ?? '').trim();
  if (!idNumber) {
    return;
  }
  growers.findByIdNumber(idNumber).subscribe({
    next: (grower) => {
      if (grower.deceased) {
        form.blockedByDeceased.set(true);
        form.errorMessage.set(form.messages()?.deceasedBlocked ?? null);
      } else {
        fillSeller(form.model, locked, grower);
      }
      repaint();
      if (!grower.deceased) {
        found();
      }
    },
    error: () => {
      form.errorMessage.set(form.messages()?.idNumberNotFound ?? 'Este número de cédula no existe.');
      repaint();
    },
  });
}
