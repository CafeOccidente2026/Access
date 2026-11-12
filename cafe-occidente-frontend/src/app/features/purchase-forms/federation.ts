import { GrowerProgram } from '../../core/models/grower.model';
import { GrowerService } from '../../core/services/grower.service';

/** Campos del bloque "Información Federación": solo los llena NESS, nunca el usuario. */
export const FEDERATION_KEYS = ['idNumber', 'fullName', 'program', 'quota'];

/**
 * C3 (prueba del 11-nov, punto 3): el bloque muestra la cédula, el nombre, el programa y el cupo de NESS.
 * Si la cédula no está en NESS queda vacío; los datos del vendedor siguen abajo, del tercero.
 */
export function federationValues(program: GrowerProgram | null): Record<string, string> {
  return {
    idNumber: program?.cedula ?? '',
    fullName: program?.nombres ?? '',
    program: program?.programa ?? '',
    quota: program?.cupo ?? '',
  };
}

/** Busca el programa NESS de la cedula (y del Especial, si ya se eligio) y llena el bloque; nunca bloquea. */
export function loadFederation(
  growers: GrowerService,
  idPart: string | undefined,
  special: string | undefined,
  model: Record<string, string | undefined>,
  repaint: () => void,
): void {
  const idNumber = (idPart ?? '').trim();
  if (!idNumber) {
    return;
  }
  growers.findProgram(idNumber, special).subscribe((program) => {
    Object.assign(model, federationValues(program));
    repaint();
  });
}
