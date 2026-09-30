import { Routes } from '@angular/router';
import { SignIn } from './core/auth/sign-in/sign-in';
import { Register } from './core/auth/register/register';
import { adminGuard } from './core/auth/guards/admin.guard';
import { authGuard } from './core/auth/guards/auth.guard';
import { OrderTicket } from './core/trade/order-ticket/order-ticket';

export const routes: Routes = [
  { path: 'sign-in', component: SignIn },
  { path: 'register', component: Register, canActivate: [authGuard] },
  { path: 'trade', component: OrderTicket, canActivate: [authGuard] },
  { path: 'admin', loadComponent: () => import('./core/admin/admin-dashboard').then(m => m.AdminDashboard), canActivate: [adminGuard] },
  { path: '', redirectTo: '/trade', pathMatch: 'full' }
];
