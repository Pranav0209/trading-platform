import { Routes } from '@angular/router';
import { SignIn } from './core/auth/sign-in/sign-in';
import { Register } from './core/auth/register/register';
import { adminGuard } from './core/auth/admin.guard';

export const routes: Routes = [
  { path: 'sign-in', component: SignIn },
  { path: 'register', component: Register },
  { path: 'admin', loadComponent: () => import('./core/admin/admin-dashboard').then(m => m.AdminDashboard), canActivate: [adminGuard] },
  { path: '', redirectTo: '/sign-in', pathMatch: 'full' }
];
