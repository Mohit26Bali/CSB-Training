import { Box, Typography } from '@mui/material';
import { NAVY, PALETTE } from '../theme';

/** The accent green of the status colours, used here for the "all good" details of the drawings. */
const GREEN = '#63d471';

/**
 * A drawing of a dashboard: a window with a progress bar, a gauge and a list, a check tile and an arrow bar.
 * It is vector, drawn in the team palette, and decorative only (hidden from screen readers).
 */
export function HeroIllustration({ width = 480 }: { width?: number }) {
  return (
    <svg viewBox="0 0 520 340" width={width} aria-hidden="true" focusable="false" style={{ maxWidth: '100%', height: 'auto', display: 'block' }}>
      <rect x="310" y="30" width="190" height="150" rx="10" fill="none" stroke={PALETTE.paleSky2} strokeOpacity="0.5" strokeWidth="2" />
      <rect x="120" y="44" width="300" height="236" rx="12" fill={PALETTE.paleSky} />
      <rect x="120" y="44" width="300" height="24" rx="12" fill={PALETTE.steelBlue} />
      <rect x="120" y="56" width="300" height="12" fill={PALETTE.steelBlue} />
      <circle cx="137" cy="56" r="4" fill={GREEN} />
      <rect x="60" y="84" width="44" height="6" rx="3" fill={PALETTE.paleSky2} fillOpacity="0.9" />

      <rect x="40" y="104" width="190" height="72" rx="10" fill="#fff" />
      <rect x="58" y="126" width="124" height="8" rx="4" fill={PALETTE.paleSky2} />
      <rect x="58" y="126" width="72" height="8" rx="4" fill={GREEN} />
      <rect x="190" y="116" width="20" height="32" rx="10" fill="none" stroke={GREEN} strokeWidth="3" />
      <rect x="58" y="148" width="86" height="6" rx="3" fill={PALETTE.paleSky} />

      <rect x="230" y="124" width="210" height="148" rx="10" fill="#fff" />
      <circle cx="302" cy="198" r="34" fill="none" stroke={PALETTE.paleSky2} strokeWidth="14" />
      <circle cx="302" cy="198" r="34" fill="none" stroke={GREEN} strokeWidth="14" strokeDasharray="150 214" transform="rotate(-90 302 198)" />
      <rect x="290" y="190" width="24" height="16" rx="3" fill={PALETTE.blueSlate2} />
      {[0, 1, 2].map((i) => (
        <g key={i}>
          <rect x="356" y={162 + i * 28} width="11" height="11" rx="2" fill="none" stroke={PALETTE.steelBlue} strokeWidth="2" />
          <rect x="374" y={165 + i * 28} width="50" height="5" rx="2.5" fill={PALETTE.paleSky2} />
        </g>
      ))}

      <rect x="64" y="196" width="78" height="78" rx="8" fill={NAVY} />
      <path d="M87 236l15 15 27-31" stroke="#fff" strokeWidth="6" fill="none" strokeLinecap="round" strokeLinejoin="round" />

      <rect x="170" y="288" width="200" height="30" rx="6" fill={PALETTE.blueSlate2} />
      <path d="M190 303h116" stroke="#fff" strokeWidth="3" strokeLinecap="round" />
      <path d="M298 294l10 9-10 9" stroke="#fff" strokeWidth="3" fill="none" strokeLinecap="round" strokeLinejoin="round" />
      <rect x="430" y="296" width="50" height="6" rx="3" fill="#f2e08a" />
    </svg>
  );
}

/** A small "nothing here" drawing: an empty window with a question mark. */
export function EmptyIllustration({ width = 130 }: { width?: number }) {
  return (
    <svg viewBox="0 0 200 140" width={width} aria-hidden="true" focusable="false" style={{ display: 'block' }}>
      <ellipse cx="100" cy="124" rx="56" ry="7" fill={PALETTE.paleSky} />
      <rect x="40" y="16" width="120" height="92" rx="9" fill="#fff" stroke={PALETTE.steelBlue} strokeWidth="3" />
      <path d="M40 25a9 9 0 0 1 9-9h102a9 9 0 0 1 9 9v7H40z" fill={PALETTE.paleSky2} />
      <circle cx="53" cy="24" r="2.6" fill="#fff" />
      <circle cx="63" cy="24" r="2.6" fill="#fff" />
      <circle cx="100" cy="68" r="24" fill="none" stroke={PALETTE.blueSlate2} strokeWidth="4" />
      <path d="M91 61a9 9 0 1 1 13 8c-3 2-4 4-4 8" fill="none" stroke={PALETTE.blueSlate2} strokeWidth="4" strokeLinecap="round" />
      <circle cx="100" cy="86" r="2.8" fill={PALETTE.blueSlate2} />
    </svg>
  );
}

/** What a list shows when nothing matches: the drawing and a sentence. */
export function EmptyState({ message }: { message: string }) {
  return (
    <Box sx={{ py: 4, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5, color: 'text.secondary' }}>
      <EmptyIllustration />
      <Typography color="text.secondary">{message}</Typography>
    </Box>
  );
}
