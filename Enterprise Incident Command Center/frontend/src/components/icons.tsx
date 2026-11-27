import type { ReactNode } from 'react';

/**
 * A small set of line icons, drawn here as SVG so no icon library is needed.
 * They take the colour of the text around them (currentColor) and are hidden from screen readers:
 * the words next to them already say what they mean.
 */
function Icon({ children, size = 20 }: { children: ReactNode; size?: number }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
      {children}
    </svg>
  );
}

export const HomeIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <path d="M3 11l9-8 9 8v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z" />
  </Icon>
);

export const DashboardIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <rect x="3" y="3" width="7" height="9" rx="1" />
    <rect x="14" y="3" width="7" height="5" rx="1" />
    <rect x="14" y="12" width="7" height="9" rx="1" />
    <rect x="3" y="16" width="7" height="5" rx="1" />
  </Icon>
);

export const ListIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" />
  </Icon>
);

export const PlusIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <path d="M12 5v14M5 12h14" />
  </Icon>
);

export const AlertIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <path d="M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" />
    <path d="M12 9v4M12 17h.01" />
  </Icon>
);

export const SearchCheckIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <circle cx="11" cy="11" r="7" />
    <path d="M21 21l-4.3-4.3M8.5 11l2 2 3.5-4" />
  </Icon>
);

export const SlidersIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <path d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6" />
  </Icon>
);

export const ClipboardIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <path d="M9 3h6a1 1 0 0 1 1 1v1H8V4a1 1 0 0 1 1-1z" />
    <rect x="5" y="5" width="14" height="17" rx="2" />
    <path d="M9 12h6M9 16h4" />
  </Icon>
);

export const LifebuoyIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <circle cx="12" cy="12" r="10" />
    <circle cx="12" cy="12" r="4" />
    <path d="M4.9 4.9l4.3 4.3M14.8 14.8l4.3 4.3M14.8 9.2l4.3-4.3M4.9 19.1l4.3-4.3" />
  </Icon>
);

export const InboxIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <path d="M22 12h-6l-2 3h-4l-2-3H2" />
    <path d="M5.5 5.1L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.5-6.9A2 2 0 0 0 16.7 4H7.3a2 2 0 0 0-1.8 1.1z" />
  </Icon>
);

export const CheckIcon = ({ size }: { size?: number }) => (
  <Icon size={size}>
    <path d="M5 12l5 5 9-10" />
  </Icon>
);

/** The icon for a menu entry, chosen by its address. */
export function NavIcon({ path }: { path: string }) {
  switch (path) {
    case '/':
      return <HomeIcon />;
    case '/dashboard':
      return <DashboardIcon />;
    case '/incidents/new':
      return <PlusIcon />;
    case '/incidents':
      return <ListIcon />;
    case '/major-incidents':
      return <AlertIcon />;
    case '/problems':
      return <SearchCheckIcon />;
    case '/admin/rules':
      return <SlidersIcon />;
    case '/admin/audit':
      return <ClipboardIcon />;
    default:
      return <ListIcon />;
  }
}
