import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'documents' },
  {
    path: 'documents',
    title: 'Documents',
    loadComponent: () =>
      import('./features/documents/pages/document-list-page').then(
        (module) => module.DocumentListPage,
      ),
  },
  {
    path: 'documents/new',
    title: 'Nouveau document',
    loadComponent: () =>
      import('./features/documents/pages/document-create-page').then(
        (module) => module.DocumentCreatePage,
      ),
  },
  {
    path: 'documents/:id',
    title: 'Document',
    loadComponent: () =>
      import('./features/documents/pages/document-detail-page').then(
        (module) => module.DocumentDetailPage,
      ),
  },
  { path: '**', redirectTo: 'documents' },
];
