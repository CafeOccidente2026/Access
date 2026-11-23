import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { authInterceptor } from './core/interceptors/auth.interceptor';
import { AuthService } from './core/services/auth.service';
import { installUppercaseInputs } from './shared/utils/uppercase-input';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(routes),
    provideAppInitializer(() => inject(AuthService).refreshSession()),
    // Demo del 23-nov: lo que se digita se ve en mayusculas mientras se escribe (menos usuario, clave y correo).
    provideAppInitializer(() => {
      installUppercaseInputs();
    }),
  ],
};
