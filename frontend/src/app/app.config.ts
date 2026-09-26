import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';

// Si `ng new` generó otros providers (por ejemplo provideRouter), conserve los suyos
// y solo asegúrese de que provideHttpClient() esté en la lista.
export const appConfig: ApplicationConfig = {
  providers: [provideBrowserGlobalErrorListeners(), provideHttpClient()],
};
