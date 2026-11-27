import { Link as RouterLink } from 'react-router-dom';
import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';
import { INK, NAVY_LIGHT, PALETTE, STRIP } from '../theme';

/**
 * The strip on top of a record page: the record type and number on the left, the actions on the right.
 * It stays visible while the page scrolls, so Resolve or Close is always within reach.
 */
export function RecordHeader({ kind, title, back, actions }: { kind: string; title: string; back: ReactNode; actions?: ReactNode }) {
  return (
    <Box
      sx={{
        position: 'sticky',
        top: { xs: 56, sm: 64 },
        zIndex: 5,
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        flexWrap: 'wrap',
        px: 2,
        py: 1,
        bgcolor: STRIP,
        borderBottom: 1,
        borderColor: 'divider',
      }}
    >
      {back}
      <Box sx={{ mr: 'auto' }}>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', lineHeight: 1.2 }}>
          {kind}
        </Typography>
        <Typography variant="subtitle1" component="h1" sx={{ lineHeight: 1.2, fontWeight: 700 }}>
          {title}
        </Typography>
      </Box>
      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>{actions}</Box>
    </Box>
  );
}

/** A record form: label on the left (right-aligned), value on the right, two columns on a wide screen. */
export function RecordForm({ children }: { children: ReactNode }) {
  return <Box sx={{ display: 'grid', columnGap: 6, rowGap: 1.5, p: 3, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>{children}</Box>;
}

/** stacked: the label sits above the value, for narrow side panels. */
export function FormRow({ label, children, wide = false, stacked = false }: { label: string; children: ReactNode; wide?: boolean; stacked?: boolean }) {
  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: stacked ? '1fr' : { xs: '1fr', sm: '140px 1fr' },
        columnGap: 2,
        alignItems: 'start',
        gridColumn: wide ? { md: '1 / -1' } : undefined,
      }}
    >
      <Typography color="text.secondary" sx={{ textAlign: stacked ? 'left' : { sm: 'right' }, pt: stacked ? 0 : '6px', fontSize: stacked ? 13 : undefined }}>
        {label}
      </Typography>
      <Box sx={{ minHeight: 32, py: '6px', px: 1.25, bgcolor: '#eef5f9', border: 1, borderColor: 'divider', borderRadius: 1, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{children}</Box>
    </Box>
  );
}

/**
 * The status as a row of arrows, like the state bar of a record: finished steps light green, the current step solid green,
 * the next ones grey. It only shows where the record is; the buttons in the header move it.
 */
export function StatusBar({ steps, active }: { steps: { key: string; label: string }[]; active: number }) {
  const arrow = (first: boolean, last: boolean) =>
    `polygon(0 0, ${last ? '100%' : 'calc(100% - 16px)'} 0, 100% 50%, ${last ? '100%' : 'calc(100% - 16px)'} 100%, 0 100%${first ? '' : ', 16px 50%'})`;
  return (
    <Box role="list" aria-label="Status" sx={{ display: 'flex', gap: '2px', px: 3, pt: 2 }}>
      {steps.map((s, i) => {
        const current = i === active;
        const done = i < active;
        return (
          <Box
            key={s.key}
            role="listitem"
            aria-current={current ? 'step' : undefined}
            sx={{
              flex: 1,
              py: 1.25,
              px: 3,
              textAlign: 'center',
              fontWeight: current ? 700 : 500,
              color: current ? '#fff' : INK,
              bgcolor: current ? 'primary.main' : done ? PALETTE.paleSky2 : STRIP,
              clipPath: arrow(i === 0, i === steps.length - 1),
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {s.label}
          </Box>
        );
      })}
    </Box>
  );
}

/** The colour of the bar on the left of an activity entry: yellow for an internal work note, dark for a comment, light for the system. */
const ENTRY_BAR = { note: '#f2c200', comment: NAVY_LIGHT, system: PALETTE.steelBlue } as const;

/**
 * One entry of an activity stream (comments of an incident, timeline of a major incident): who, what kind, when, and the text.
 * The bar on the left tells the kind at a glance, and the label says it in words (colour alone is never the signal).
 */
export function ActivityEntry({ author, label, time, kind, extra, children }: { author: string; label: string; time: string; kind: keyof typeof ENTRY_BAR; extra?: ReactNode; children: ReactNode }) {
  return (
    <Box sx={{ border: 1, borderColor: 'divider', borderLeft: `4px solid ${ENTRY_BAR[kind]}`, borderRadius: 1, p: 1.5, bgcolor: '#fff' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap', mb: 0.5 }}>
        <Typography variant="subtitle2" sx={{ mr: 'auto' }}>
          {author}
        </Typography>
        {extra}
        <Typography variant="caption" color="text.secondary">
          {label} &bull; {time}
        </Typography>
      </Box>
      <Typography sx={{ whiteSpace: 'pre-wrap' }}>{children}</Typography>
    </Box>
  );
}

/** "Home > Requests": where you are, with a link back for each step except the last. */
export function Breadcrumb({ items }: { items: { label: string; to?: string }[] }) {
  return (
    <Box component="nav" aria-label="Breadcrumb" sx={{ display: 'flex', gap: 1, alignItems: 'center', color: 'text.secondary' }}>
      {items.map((item, i) => (
        <Box key={item.label} component="span" sx={{ display: 'inline-flex', gap: 1, alignItems: 'center' }}>
          {i > 0 && <span aria-hidden>&rsaquo;</span>}
          {item.to ? (
            <RouterLink to={item.to}>{item.label}</RouterLink>
          ) : (
            <Box component="span" sx={{ color: 'text.primary', fontWeight: 700 }} aria-current="page">
              {item.label}
            </Box>
          )}
        </Box>
      ))}
    </Box>
  );
}
