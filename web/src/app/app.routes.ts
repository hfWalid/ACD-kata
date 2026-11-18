import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'documents' },
  {
    path: 'documents',
    title: 'Documents',
    loadComponent: () =>
      import('./documents/document-list/document-list').then((module) => module.DocumentList),
  },
  {
    path: 'documents/new',
    title: 'Nouveau document',
    loadComponent: () =>
      import('./documents/document-create/document-create').then((module) => module.DocumentCreate),
  },
  {
    path: 'documents/:id',
    title: 'Document',
    loadComponent: () =>
      import('./documents/document-detail/document-detail').then((module) => module.DocumentDetail),
  },
  { path: '**', redirectTo: 'documents' },
];
