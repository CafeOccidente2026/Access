import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Unica responsabilidad: obtener contenido estatico (textos, opciones,
 * datos de ejemplo) desde los archivos JSON de /assets/data.
 * Ninguna pantalla debe usar HttpClient directamente; siempre pasa por aqui.
 */
@Injectable({ providedIn: 'root' })
export class ContentService {
  private readonly http = inject(HttpClient);
  private readonly basePath = 'assets/data';

  loadJson<T>(fileName: string): Observable<T> {
    return this.http.get<T>(`${this.basePath}/${fileName}.json`);
  }
}
