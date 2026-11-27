import { useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import {
  AppBar,
  Box,
  Button,
  Chip,
  Container,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Typography,
  useMediaQuery,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useAuth } from '../auth/AuthContext';
import { ROLE_LABEL, menuFor } from '../auth/access';
import { NavIcon } from '../components/icons';
import { NAVY, NAVY_LIGHT, PALETTE } from '../theme';

const DRAWER_WIDTH = 240;

// Menu entries: light text on the dark panel, the open page marked by a bar on the left.
const navItem = { color: 'rgba(255,255,255,0.8)', borderLeft: '4px solid transparent', '&.active': { bgcolor: NAVY_LIGHT, color: '#fff', borderLeftColor: PALETTE.paleSky2 }, '&:hover': { bgcolor: NAVY_LIGHT } };

/**
 * The employee's frame: a light top bar with the product name and three links, no side menu. Employees only report
 * and follow incidents, so the console menu would be mostly empty for them.
 */
function PortalShell() {
  const { user, logout } = useAuth();
  if (!user) return null;
  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <Box component="header" sx={{ bgcolor: '#fff', borderBottom: 1, borderColor: 'divider', borderTop: `4px solid ${NAVY}` }}>
        <Container maxWidth="xl" sx={{ display: 'flex', alignItems: 'center', gap: 3, flexWrap: 'wrap', py: 1 }}>
          <Typography component="div" sx={{ fontWeight: 700, fontSize: 20, color: NAVY, mr: 'auto' }}>
            Incident Command Center
          </Typography>
          <Box role="navigation" aria-label="Main menu" sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
            {menuFor(user.role).map((item) => (
              <Button key={item.path} component={NavLink} to={item.path} end color="inherit" sx={{ borderRadius: 0, borderBottom: '3px solid transparent', '&.active': { borderBottomColor: 'primary.main', color: 'primary.main' } }}>
                {item.label}
              </Button>
            ))}
          </Box>
          <Typography data-testid="who" sx={{ display: { xs: 'none', md: 'block' } }}>
            {user.fullName}
          </Typography>
          <Chip label={ROLE_LABEL[user.role]} size="small" sx={{ display: { xs: 'none', sm: 'inline-flex' } }} />
          <Button onClick={logout} sx={{ whiteSpace: 'nowrap' }}>
            Log out
          </Button>
        </Container>
      </Box>
      <Container maxWidth="lg" component="main" sx={{ py: 3 }}>
        <Outlet />
      </Container>
    </Box>
  );
}

/** The frame of every logged-in page: top bar with the user and logout, the role's menu, and the page itself. */
export function AppShell() {
  const { user, logout } = useAuth();
  const theme = useTheme();
  const permanent = useMediaQuery(theme.breakpoints.up('md'));
  const [open, setOpen] = useState(false);
  if (!user) return null;
  if (user.role === 'EMPLOYEE') return <PortalShell />;

  const menu = (
    <Box role="navigation" aria-label="Main menu" sx={{ width: DRAWER_WIDTH }}>
      <Toolbar />
      <List>
        {menuFor(user.role).map((item) =>
          item.ready ? (
            <ListItemButton key={item.path + item.label} component={NavLink} to={item.path} end onClick={() => setOpen(false)} sx={navItem}>
              <ListItemIcon sx={{ minWidth: 36, color: 'inherit' }}>
                <NavIcon path={item.path} />
              </ListItemIcon>
              <ListItemText primary={item.label} />
            </ListItemButton>
          ) : (
            <ListItemButton key={item.path + item.label} component={NavLink} to={item.path} end onClick={() => setOpen(false)} sx={navItem}>
              <ListItemIcon sx={{ minWidth: 36, color: 'inherit' }}>
                <NavIcon path={item.path} />
              </ListItemIcon>
              <ListItemText primary={item.label} />
              <Chip size="small" label="soon" variant="outlined" sx={{ color: 'inherit', borderColor: 'rgba(255,255,255,0.4)' }} />
            </ListItemButton>
          ),
        )}
      </List>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1, background: `linear-gradient(90deg, ${NAVY} 0%, #3a5260 100%)`, boxShadow: 'none' }}>
        <Toolbar>
          {!permanent && (
            <IconButton color="inherit" edge="start" aria-label="Open the menu" onClick={() => setOpen(true)} sx={{ mr: 1, fontSize: 16 }}>
              Menu
            </IconButton>
          )}
          <Typography variant="h6" component="div" noWrap sx={{ flexGrow: 1, fontSize: { xs: 16, md: 20 } }}>
            Incident Command Center
          </Typography>
          {permanent && (
            <Typography sx={{ mr: 1.5 }} data-testid="who">
              {user.fullName}
            </Typography>
          )}
          <Chip label={ROLE_LABEL[user.role]} size="small" sx={{ mr: 1.5, display: { xs: 'none', sm: 'inline-flex' }, bgcolor: 'rgba(255,255,255,0.18)', color: 'inherit' }} />
          <Button color="inherit" onClick={logout} sx={{ whiteSpace: 'nowrap', flexShrink: 0 }}>
            Log out
          </Button>
        </Toolbar>
      </AppBar>

      <Drawer
        variant={permanent ? 'permanent' : 'temporary'}
        open={permanent || open}
        onClose={() => setOpen(false)}
        ModalProps={{ keepMounted: true }}
        sx={{ width: permanent ? DRAWER_WIDTH : 0, flexShrink: 0, '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box', overflowX: 'hidden', bgcolor: NAVY, color: '#fff', borderRight: 0 } }}
      >
        {menu}
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, minWidth: 0 }}>
        <Toolbar />
        <Container maxWidth="xl" sx={{ py: 3 }}>
          <Outlet />
        </Container>
      </Box>
    </Box>
  );
}
