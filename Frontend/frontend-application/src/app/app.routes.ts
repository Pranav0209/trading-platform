import { Routes } from '@angular/router';
import { SignIn } from './features/auth/sign-in/sign-in';
import { Register } from './features/auth/register/register';
import { adminGuard } from './core/auth/guards/admin.guard';
import { authGuard } from './core/auth/guards/auth.guard';
import { OrderTicket } from './features/trade/order-ticket/order-ticket';

export const routes: Routes = [
  { path: 'sign-in', component: SignIn },
  { path: 'register', component: Register },
  {
    path: '',
    loadComponent: () => import('./core/layout/layout').then(m => m.LayoutComponent),
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', loadComponent: () => import('./features/dashboard/dashboard').then(m => m.Dashboard) },
      { path: 'trade', component: OrderTicket },
      { path: 'admin', loadComponent: () => import('./features/admin/admin-dashboard').then(m => m.AdminDashboard), canActivate: [adminGuard] },
      { path: 'orders', loadComponent: () => import('./features/trade/order-blotter/order-blotter').then(m => m.OrderBlotter) },
      { path: 'positions', loadComponent: () => import('./features/trade/positions/positions').then(m => m.PositionsComponent) },
      { path: 'holdings', loadComponent: () => import('./features/trade/positions/positions').then(m => m.PositionsComponent) },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: '/dashboard' }
];
