import { Outlet } from 'react-router-dom';

export function AppLayout() {
  return (
    <div className="h-screen w-screen overflow-hidden bg-surface text-text font-sans">
      <Outlet />
    </div>
  );
}
