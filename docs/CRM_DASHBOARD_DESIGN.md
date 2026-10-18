# CRM Dashboard — Visual System

*Companion to [CRM_DASHBOARD_PLAN.md](CRM_DASHBOARD_PLAN.md) and [CRM_DASHBOARD_IMPLEMENTATION.md](CRM_DASHBOARD_IMPLEMENTATION.md). Written 2026-08-20.*

**Live design reference (hover the charts, toggle your OS theme):** https://claude.ai/code/artifact/47f79143-681b-4ae1-b257-d5924b4524f0

---

## The organising idea

The dashboard is split into **two visually distinct bands, because it holds two kinds of number.**

| | Flow | Stock |
|---|---|---|
| Means | happened inside the selected window | state of the world right now |
| Period selector | applies | **does not apply** |
| Surface | raised white card | recessed inset ground |
| Carries | delta vs. prior period + sparkline | severity chip + left stripe |
| Never carries | severity chip | a delta |

This is the same distinction as P&L vs. Balance Sheet, and the team already thinks in those terms from the accounting module. Encoding it in the layout makes the classic dashboard bug impossible to render: filtering "overdue follow-ups" to a month produces a number that means nothing and shrinks at the start of every month, which reads as improvement.

**Rule:** a metric moves to the stock band or the flow band. There is no third option, and nothing sits in both.

---

## Colour

Neutrals are biased cool toward the accent rather than pure grey. The accent is the product's existing blue (`#2563eb`) pushed one step into pigment so it reads as drafting ink rather than UI chrome — appropriate for a process-equipment manufacturer, and continuous with what is already on screen.

### Interface tokens

| Token | Light | Dark |
|---|---|---|
| `--ink` | `#0A1020` | `#E6EDF5` |
| `--ink-2` | `#4E5C74` | `#9AA9BF` |
| `--ink-3` | `#8794AB` | `#6B7A90` |
| `--rule` | `#DFE5EE` | `#232D3B` |
| `--rule-soft` | `#EDF1F6` | `#1B2431` |
| `--surface` | `#FFFFFF` | `#141B24` |
| `--ground` | `#F2F5F9` | `#0C1117` |
| `--inset` (stock band) | `#E8EEF6` | `#101821` |
| `--blueprint` (accent) | `#1C4ED8` | `#5B9BEE` |

### Series — categorical, fixed order, never cycled

| Slot | Light | Dark |
|---|---|---|
| Created | `#2a78d6` | `#3987e5` |
| Won | `#0f8f63` | `#199e70` |
| Lost | `#e34948` | `#e66767` |

Validated all-pairs: light passes every gate (worst CVD ΔE 8.4, all slots ≥ 3:1 on surface). Dark sits in the 6–8 CVD band, which is legal **only** with secondary encoding — so **endpoint direct labels on the trend chart are mandatory, not decorative.** Do not remove them to "clean up" the chart.

### Funnel ordinal ramp — one hue, monotone lightness

| Light | Dark |
|---|---|
| `#86b6ef` `#5598e7` `#2a78d6` `#1c5cab` `#104281` | `#184f95` `#256abf` `#3987e5` `#6da7ec` `#b7d3f6` |

**Five steps, and that is load-bearing.** The ramp only clears the adjacent-step and light-end contrast gates at five; six steps fail. That settled a modelling question rather than a cosmetic one: the ten `EnquiryStatus` values collapse into **five real funnel stages** — New → Contacted → Quoted → Negotiation → Won. `LOST`, `CLOSED` and `JUNK` are *outcomes*, not stages, and never belong in a funnel; `FOLLOW_UP` is a mode, not a stage. The colour constraint produced the better data model, so keep both.

### Status — reserved, never reused as a series

`good #0ca30c` · `warning #fab219` · `serious #ec835a` · `critical #d03b3b`

Always paired with an icon **and** a word. Colour never carries meaning alone — `warning` and `serious` are sub-3:1 on the light surface by design, and the icon-plus-label pairing is the mitigation.

---

## Type

| Role | Face |
|---|---|
| Headings, body | **IBM Plex Sans** (400 / 500 / 600) |
| Eyebrows, column heads | **IBM Plex Sans Condensed** 700, uppercase, `.09em` tracking, 10.5px |
| Every figure | **IBM Plex Mono** 500, `tabular-nums` |

The mono is not a new choice — `AgeingReportPage.jsx` already sets numerals in IBM Plex Mono. The superfamily **extends the system already in the codebase** rather than replacing it, and gives the panel an engineering-documentation character that Inter does not.

Every numeric column runs `font-variant-numeric: tabular-nums` so digits align down a column. This is non-negotiable in a table of money.

---

## Component specs

### Flow tile
Raised white card. Eyebrow (condensed caps) top-left, 80×24 sparkline top-right. Value at 30px/500, `-.03em` tracking, unit in a smaller lighter span. Footer row: delta pill + prior-period value in words.

Delta pill: mono 11.5px on a tinted ground — green up, red down, neutral flat. **The pill states direction against the prior window and nothing else** — no arbitrary "target" colouring.

### Stock tile
Recessed on the inset band. 3px severity stripe on the left edge, value at 25px, a plain-language note under it, and a severity chip with icon + word. **No delta, ever.** An "as of <date>" line sits once on the band header, not repeated per tile.

The left accent stripe survives **only here**, where it encodes severity. Flow tiles drop it — on those it was decoration, and a rail on every rounded card is the single most generic move in enterprise UI.

### Funnel
Horizontal bars, stage name right-aligned in a fixed 108px gutter, count in mono inside the bar end. Between every pair of stages sits a **drop-off annotation** — the percentage plus a plain-language reason ("15 never picked up"). The worst leak is coloured `critical`; the others `serious`.

The drop-off line is the whole point of the chart. A funnel without leakage annotation is just five bars.

### Trend
Multi-line, 2px strokes, recessive dashed gridlines, emphasised 4.5px endpoints with a 2px surface ring. Direct end labels for all three series (see the CVD note above). Legend always present. Full-width transparent hit rectangles per bucket drive a crosshair tooltip — hit targets span the plot height, not just the mark.

### Ranked bars (close reasons)
One hue per *sentiment* — won green, lost red, everything else recessive `--ink-3`. Identity comes from the row label, so colour does not have to carry six categories. This is why the six-outcome stacked bar was rejected: it fails CVD separation and reads worse.

### Tables
Condensed-caps column heads, hairline row rules, mono right-aligned numerics, full-row hover. Wide tables live inside their own `overflow-x: auto` container — the page body never scrolls sideways. **"Unassigned" is always a visible row, never filtered out** — it is usually the largest and worst-performing bucket in the register, and hiding it is how it stays that way.

---

## The honesty affordance

Every derived metric ships with its coverage, rendered — not buried in a tooltip:

- Weighted pipeline shows **"34% coverage"** as a warning chip, because most open enquiries have no probability.
- The close-reason split states **"215 of 329 coded (65%)"**.
- Funnel leakage says plainly that it is inferred from current stage counts and needs Phase 2 to be real.

This is a deliberate carry-over of the `codedCount` discipline already in `EnquirySummaryDTO` — the strongest thing about the existing backend and the first thing a redesign tends to lose, because a clean tile looks better than an honest one. It is a hard requirement of this design, not a nicety.

---

## Theming

Three states, not two: an explicit choice stamps `data-theme`, and the default "system" setting stamps nothing. Define the full light palette on bare `:root`; redefine **tokens only** under `@media (prefers-color-scheme: dark)` guarded as `:root:not([data-theme="light"])`, and again under `:root[data-theme="dark"]`. Never declare a colour whose only definition sits inside a media or `[data-theme]` block.

Dark series steps are **selected for the dark surface**, not flipped from light.

---

## Build target

MUI 6 + Recharts 3.8 — both already dependencies. **No new packages.**

- Tokens land in a shared `ui/src/components/crm/crmTokens.js`, consumed through `sx`.
- This replaces the ad-hoc `T` object currently inlined at `Enquiry.jsx:39` and the parallel constants in `AgeingReportPage.jsx` / `PurchaseAnalytics.jsx`. Those three have already drifted (`#2563eb` vs `#1565c0` for the same role); consolidating them is part of Phase 1, not a later cleanup.
- Charts use Recharts with the palette above passed explicitly. Do **not** rely on Recharts' default colour cycle — it cycles, and cycling breaks the "colour follows the entity, never its rank" rule the moment a filter changes the series count.
