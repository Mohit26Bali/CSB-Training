"""Gives a project its own colour palette, fonts and corner roundness.

    python tools/apply_theme.py <project> <palette> "<body font>" ["<heading font>"] [card-radius control-radius]

Rewrites the brand colour variables and their dark-mode overrides in frontend/src/index.css, the font variables, a small
"look" block (heading font and radii) at the end of that file, and the Google Fonts link in frontend/index.html.
Palettes: teal blue violet rose amber emerald indigo orange cyan fuchsia lime sky red slate pink navy green purple stone brown.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 50, 100, 600, 700, 800, dark glow rgb, dark text on 700, dark text on 800
PALETTES = {
    "teal": ("#f0fdfa", "#ccfbf1", "#0d9488", "#0f766e", "#115e59", "20 184 166", "#2dd4bf", "#5eead4"),
    "blue": ("#eff6ff", "#dbeafe", "#2563eb", "#1d4ed8", "#1e40af", "59 130 246", "#60a5fa", "#93c5fd"),
    "violet": ("#f5f3ff", "#ede9fe", "#7c3aed", "#6d28d9", "#5b21b6", "139 92 246", "#a78bfa", "#c4b5fd"),
    "rose": ("#fff1f2", "#ffe4e6", "#e11d48", "#be123c", "#9f1239", "244 63 94", "#fb7185", "#fda4af"),
    "amber": ("#fffbeb", "#fef3c7", "#d97706", "#b45309", "#92400e", "245 158 11", "#fbbf24", "#fcd34d"),
    "emerald": ("#ecfdf5", "#d1fae5", "#059669", "#047857", "#065f46", "16 185 129", "#34d399", "#6ee7b7"),
    "indigo": ("#eef2ff", "#e0e7ff", "#4f46e5", "#4338ca", "#3730a3", "99 102 241", "#818cf8", "#a5b4fc"),
    "orange": ("#fff7ed", "#ffedd5", "#ea580c", "#c2410c", "#9a3412", "249 115 22", "#fb923c", "#fdba74"),
    "cyan": ("#ecfeff", "#cffafe", "#0891b2", "#0e7490", "#155e75", "6 182 212", "#22d3ee", "#67e8f9"),
    "fuchsia": ("#fdf4ff", "#fae8ff", "#c026d3", "#a21caf", "#86198f", "217 70 239", "#e879f9", "#f0abfc"),
    "sky": ("#f0f9ff", "#e0f2fe", "#0284c7", "#0369a1", "#075985", "14 165 233", "#38bdf8", "#7dd3fc"),
    "red": ("#fef2f2", "#fee2e2", "#dc2626", "#b91c1c", "#991b1b", "239 68 68", "#f87171", "#fca5a5"),
    "slate": ("#f8fafc", "#f1f5f9", "#475569", "#334155", "#1e293b", "148 163 184", "#cbd5e1", "#e2e8f0"),
    "pink": ("#fdf2f8", "#fce7f3", "#db2777", "#be185d", "#9d174d", "236 72 153", "#f472b6", "#f9a8d4"),
    "navy": ("#eef2f9", "#d9e2f1", "#2b4c8c", "#223c70", "#1a2d56", "96 130 200", "#8fa8d8", "#b6c6e8"),
    "green": ("#f0fdf4", "#dcfce7", "#16a34a", "#15803d", "#166534", "34 197 94", "#4ade80", "#86efac"),
    "purple": ("#faf5ff", "#f3e8ff", "#9333ea", "#7e22ce", "#6b21a8", "168 85 247", "#c084fc", "#d8b4fe"),
    "stone": ("#fafaf9", "#f5f5f4", "#78716c", "#57534e", "#44403c", "168 162 158", "#d6d3d1", "#e7e5e4"),
    "brown": ("#fdf6ef", "#f6e7d4", "#a8601f", "#8a4d17", "#6f3d12", "205 133 63", "#d99a5b", "#e8b987"),
    "lime": ("#f7fee7", "#ecfccb", "#65a30d", "#4d7c0f", "#3f6212", "132 204 22", "#a3e635", "#bef264"),
}

LOOK_BEGIN = "/* look:begin */"
LOOK_END = "/* look:end */"


def apply(project, palette, font, display=None, card_radius="0.75rem", control_radius="0.5rem"):
    display = display or font
    c50, c100, c600, c700, c800, glow, d700, d800 = PALETTES[palette]

    css_path = os.path.join(ROOT, project, "frontend", "src", "index.css")
    css = open(css_path, encoding="utf-8").read()
    for name, value in (("50", c50), ("100", c100), ("600", c600), ("700", c700), ("800", c800)):
        css = re.sub(rf"--color-brand-{name}: #[0-9a-fA-F]+;", f"--color-brand-{name}: {value};", css)
    css = re.sub(r"(\.dark \.bg-brand-50 \{ background-color: rgb\()[\d ]+( / 0\.14\); \})", rf"\g<1>{glow}\g<2>", css)
    css = re.sub(r"(\.dark \.bg-brand-100 \{ background-color: rgb\()[\d ]+( / 0\.22\); \})", rf"\g<1>{glow}\g<2>", css)
    css = re.sub(r"(\.dark \.text-brand-700 \{ color: )#[0-9a-fA-F]+;", rf"\g<1>{d700};", css)
    css = re.sub(r"(\.dark \.text-brand-800 \{ color: )#[0-9a-fA-F]+;", rf"\g<1>{d800};", css)
    css = re.sub(r"(--tw-ring-color: rgb\()[\d ]+( / 0\.4\))", rf"\g<1>{glow}\g<2>", css)
    css = re.sub(r"--font-sans: '[^']+',", f"--font-sans: '{font}',", css)

    look = f"""{LOOK_BEGIN}
:root {{
  --font-display: '{display}', ui-sans-serif, system-ui, sans-serif;
  --card-radius: {card_radius};
  --control-radius: {control_radius};
  --page-bg: #f8fafc;
}}
.dark {{ --page-bg: #0b1220; }}
h1, h2, h3 {{ font-family: var(--font-display); }}
.rounded-xl, .rounded-2xl {{ border-radius: var(--card-radius); }}
.rounded-lg {{ border-radius: var(--control-radius); }}
@keyframes fade-only {{ 0% {{ opacity: 0; }} 6%, 30% {{ opacity: 1; }} 38%, 100% {{ opacity: 0; }} }}
.fade-only {{ animation-name: fade-only; }}
@keyframes drift-a {{ 0%, 100% {{ transform: translateY(0); }} 50% {{ transform: translateY(-10px); }} }}
@keyframes drift-b {{ 0%, 100% {{ transform: translateY(0); }} 50% {{ transform: translateY(10px); }} }}
.mosaic-a {{ animation: drift-a 9s ease-in-out infinite; }}
.mosaic-b {{ animation: drift-b 11s ease-in-out infinite; }}
@keyframes slide-up {{ from {{ transform: translateY(100%); opacity: 0; }} to {{ transform: translateY(0); opacity: 1; }} }}
.slide-word {{ animation: slide-up 0.5s cubic-bezier(0.2, 0.8, 0.2, 1) both; }}
@keyframes word-fade {{ from {{ opacity: 0; letter-spacing: 0.18em; filter: blur(4px); }} to {{ opacity: 1; letter-spacing: 0; filter: blur(0); }} }}
.fade-word {{ animation: word-fade 0.8s ease-out both; }}
@media (prefers-reduced-motion: reduce) {{ .fade-only, .mosaic-a, .mosaic-b, .slide-word, .fade-word {{ animation: none; }} }}
{LOOK_END}"""
    if LOOK_BEGIN in css:
        css = re.sub(re.escape(LOOK_BEGIN) + r".*?" + re.escape(LOOK_END), look.replace("\\", "\\\\"), css, flags=re.S)
    else:
        css = css.rstrip("\n") + "\n\n" + look + "\n"
    open(css_path, "w", encoding="utf-8", newline="\n").write(css)

    html_path = os.path.join(ROOT, project, "frontend", "index.html")
    html = open(html_path, encoding="utf-8").read()
    html = re.sub(r"\s*<link rel=\"preconnect\"[^>]*>|\s*<link href=\"https://fonts.googleapis.com[^>]*>", "", html)
    families = "&".join(f"family={f.replace(' ', '+')}:wght@400;500;600;700" for f in dict.fromkeys([font, display]))
    tags = ('\n    <link rel="preconnect" href="https://fonts.googleapis.com" />'
            '\n    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />'
            f'\n    <link href="https://fonts.googleapis.com/css2?{families}&display=swap" rel="stylesheet" />')
    html = html.replace('<link rel="icon" type="image/svg+xml" href="/favicon.svg" />', '<link rel="icon" type="image/svg+xml" href="/favicon.svg" />' + tags, 1)
    open(html_path, "w", encoding="utf-8", newline="\n").write(html)
    print(f"{project}: {palette} / {font} / {display} / radius {card_radius} {control_radius}")


if __name__ == "__main__":
    args = sys.argv[1:]
    apply(args[0], args[1], args[2], args[3] if len(args) > 3 else None,
          args[4] if len(args) > 4 else "0.75rem", args[5] if len(args) > 5 else "0.5rem")
