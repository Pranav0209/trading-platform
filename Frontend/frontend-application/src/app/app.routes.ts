import { Routes } from '@angular/router';
import { SignIn } from './features/auth/sign-in/sign-in';
import { Register } from './features/auth/register/register';
import { adminGuard } from './core/auth/guards/admin.guard';
import { authGuard } from './core/auth/guards/auth.guard';
import { OrderTicket } from './features/trade/order-ticket/order-ticket';

import { customerGuard } from './core/auth/guards/customer.guard';

export const routes: Routes = [
  { path: 'sign-in', component: SignIn },
  { path: 'register', component: Register },
  {
    path: '',
    loadComponent: () => import('./core/layout/layout').then(m => m.LayoutComponent),
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', loadComponent: () => import('./features/dashboard/dashboard').then(m => m.Dashboard), canActivate: [customerGuard] },
      { path: 'watchlist', loadComponent: () => import('./features/trade/watchlist/watchlist').then(m => m.WatchlistComponent), canActivate: [customerGuard] },
      { path: 'trade', component: OrderTicket, canActivate: [customerGuard] },
      { path: 'admin', loadComponent: () => import('./features/admin/admin-dashboard').then(m => m.AdminDashboard), canActivate: [adminGuard] },
      { path: 'orders', loadComponent: () => import('./features/trade/order-blotter/order-blotter').then(m => m.OrderBlotter), canActivate: [customerGuard] },
      { path: 'positions', loadComponent: () => import('./features/trade/positions/positions').then(m => m.PositionsComponent), canActivate: [customerGuard] },
      { path: 'holdings', loadComponent: () => import('./features/trade/positions/positions').then(m => m.PositionsComponent), canActivate: [customerGuard] },
      { path: 'realised-pnl', loadComponent: () => import('./features/trade/realised-pnl/realised-pnl').then(m => m.RealisedPnlComponent), canActivate: [customerGuard] },
      { path: 'pnl', loadComponent: () => import('./features/trade/realised-pnl/realised-pnl').then(m => m.RealisedPnlComponent), canActivate: [customerGuard] },
      { path: 'profile', loadComponent: () => import('./features/profile/profile').then(m => m.Profile), canActivate: [customerGuard] },
      { path: 'account', loadComponent: () => import('./features/account/account').then(m => m.Account), canActivate: [customerGuard] },
      { path: 'advice', loadComponent: () => import('./features/advice/advice').then(m => m.Advice), canActivate: [customerGuard] },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: '/dashboard' }
];
