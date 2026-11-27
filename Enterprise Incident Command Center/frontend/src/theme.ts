import { createTheme } from '@mui/material/styles';

/**
 * The palette (chosen by the team): five blues from pale to slate.
 *
 *   Pale Sky     #cee5f2  surfaces: banners, highlights (dark text on it 11.2)
 *   Pale Sky 2   #accbe1  surfaces: finished steps, banners (dark text on it 8.6)
 *   Steel Blue   #7c98b3  decoration and large text only (white on it is 3.0)
 *   Blue Slate   #637081  secondary buttons and text (white on it 5.0)
 *   Blue Slate 2 #536b78  primary buttons, tabs, active step (white on it 5.6)
 *
 * 4.5 is the accessible minimum for normal text. Status colours (green, yellow, red) stay as they are, because colour must still
 * tell "done" from "failed". The shell (top bar, menu, login) uses a deeper shade of the same blue.
 */
export const PALETTE = {
  paleSky: '#cee5f2',
  paleSky2: '#accbe1',
  steelBlue: '#7c98b3',
  blueSlate: '#637081',
  blueSlate2: '#536b78',
} as const;

/** Text colour on every light surface. */
export const INK = '#1f2a33';
/** The dark shell: top bar, menu, login page (a deep shade of Blue Slate 2). */
export const NAVY = '#30414b';
export const NAVY_LIGHT = '#3d515c';
/** The strip under the top bar (record header, list toolbar): a light tint of Pale Sky. */
export const STRIP = '#e2edf3';
/** The page behind the panels: a very light tint of Pale Sky. */
export const PAGE = '#f3f9fc';

// One place for colours and shape. Screens use the theme, they do not set their own colours.
// The look follows common enterprise-console conventions (dark banner, compact tables, flat panels,
// two-column record forms). It is inspired by them, not a copy (D29): own name, own colours, no logo.
export const theme = createTheme({
  palette: {
    primary: { main: PALETTE.blueSlate2 },
    secondary: { main: PALETTE.blueSlate },
    success: { main: '#2c6341' },
    info: { main: '#2e638a' },
    background: { default: PAGE },
    text: { primary: INK },
    divider: '#cfdbe3',
  },
  shape: { borderRadius: 8 },
  typography: {
    fontFamily: '"Segoe UI", "Helvetica Neue", Arial, sans-serif',
    fontSize: 14,
    h5: { fontWeight: 600 },
    h6: { fontWeight: 600 },
  },
  components: {
    MuiCssBaseline: { styleOverrides: { a: { color: '#2e638a' } } },
    MuiToggleButton: { styleOverrides: { root: { textTransform: 'none', fontWeight: 600 } } },
    MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { textTransform: 'none', fontWeight: 600 } } },
    MuiPaper: { defaultProps: { variant: 'outlined' } },
    MuiTextField: { defaultProps: { size: 'small' } },
    MuiChip: { styleOverrides: { root: { fontWeight: 600 } } },
    MuiTableCell: {
      styleOverrides: {
        root: { paddingTop: 8, paddingBottom: 8 },
        head: { fontWeight: 700, backgroundColor: '#fff', whiteSpace: 'nowrap' },
      },
    },
    MuiTableRow: { styleOverrides: { root: { '&.MuiTableRow-hover:hover': { backgroundColor: '#edf4f8' } } } },
    MuiTab: { styleOverrides: { root: { textTransform: 'none', fontWeight: 600, minHeight: 40 } } },
  },
});
