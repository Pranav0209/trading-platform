import { Routes } from '@angular/router';
import { SignIn } from './core/auth/sign-in/sign-in';
import { Register } from './core/auth/register/register';
import { adminGuard } from './core/auth/guards/admin.guard';
import { authGuard } from './core/auth/guards/auth.guard';
import { OrderTicket } from './core/trade/order-ticket/order-ticket';

export const routes: Routes = [
  { path: 'sign-in', component: SignIn },
  { path: 'register', component: Register },
  { path: 'trade', component: OrderTicket, canActivate: [authGuard] },
  { path: 'admin', loadComponent: () => import('./core/admin/admin-dashboard').then(m => m.AdminDashboard), canActivate: [adminGuard] },
  { path: 'dashboard', loadComponent: () => import('./core/dashboard/dashboard').then(m => m.Dashboard), canActivate: [authGuard] },
  { path: 'orders', loadComponent: () => import('./core/trade/order-blotter/order-blotter').then(m => m.OrderBlotter), canActivate: [authGuard] },
  { path: 'positions', loadComponent: () => import('./core/trade/positions/positions').then(m => m.PositionsComponent), canActivate: [authGuard] },
  { path: '', redirectTo: '/sign-in', pathMatch: 'full' },
  { path: '**', redirectTo: '/dashboard' }
];
