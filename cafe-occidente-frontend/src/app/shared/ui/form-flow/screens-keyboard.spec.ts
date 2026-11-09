import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

/**
 * Prueba del 9-nov (segunda), punto 1: toda pantalla con formulario abre con el foco en el primer campo y Enter (o elegir
 * en una lista) pasa al siguiente; despues del ultimo, Enter usa el boton principal. Lo hace appFormFlow: aqui se
 * revisa que cada plantilla lo tenga y que marque su boton. Las de compra, Login, Usuarios y Anuncios tienen el suyo.
 */
const SCREENS: [string, number][] = [
  // [plantilla, cuantos bloques con appFormFlow y boton data-flow-submit]
  ['quota-assignment/quota-assignment.html', 1],
  ['control-record/control-record.html', 1],
  ['announcement-report/announcement-report.html', 1],
  ['future-report/future-report.html', 1],
  ['date-range-dialog/date-range-dialog.html', 1],
  ['history/history.html', 1],
  ['inventory-report/inventory-report.html', 1],
  ['future-restore/future-restore.html', 1],
  ['remission-reprint/remission-reprint.html', 1],
  ['conductor-form/conductor-form.html', 2],
  ['remission-form/remission-form.html', 1],
  ['remittance/remittance.html', 2],
];

const count = (text: string, part: string) => text.split(part).length - 1;

describe('pantallas con teclado de formulario', () => {
  it.each(SCREENS)('%s', (path, blocks) => {
    const html = readFileSync(`src/app/features/${path}`, 'utf-8');
    expect(count(html, 'appFormFlow'), 'appFormFlow').toBeGreaterThanOrEqual(blocks);
    expect(count(html, 'data-flow-submit'), 'data-flow-submit').toBeGreaterThanOrEqual(blocks);
  });

  // Sin boton de Enter: Inventario filtra al escribir; en Permisos despues del usuario vienen casillas.
  it.each(['inventory-consulta/inventory-consulta.html', 'permissions/permissions.html'])('%s: abre con el foco en el primer campo', (path) => {
    expect(readFileSync(`src/app/features/${path}`, 'utf-8')).toContain('appFormFlow');
  });
});
