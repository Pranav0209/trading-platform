import { Routes } from '@angular/router';
import { SignIn } from './core/auth/sign-in/sign-in';
import { Register } from './core/auth/register/register';
import { adminGuard } from './core/auth/guards/admin.guard';
import { authGuard } from './core/auth/guards/auth.guard';

export const routes: Routes = [
  { path: 'sign-in', component: SignIn },
  { path: 'register', component: Register, canActivate: [authGuard] },
  { path: 'admin', loadComponent: () => import('./core/admin/admin-dashboard').then(m => m.AdminDashboard), canActivate: [adminGuard] },
  { path: '', redirectTo: '/sign-in', pathMatch: 'full' }
];
