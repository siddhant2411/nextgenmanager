# CRM Dashboard & Pipeline Analytics — Module Plan

*Planned 2026-08-20. Companion specs: [CRM_DASHBOARD_IMPLEMENTATION.md](CRM_DASHBOARD_IMPLEMENTATION.md) (phase-by-phase build) and [CRM_DASHBOARD_DESIGN.md](CRM_DASHBOARD_DESIGN.md) (visual system). Format follows [ACCOUNTING_MODULE_PLAN.md](ACCOUNTING_MODULE_PLAN.md).*

> **Goal:** Turn the enquiry register from a *list you filter* into a *pipeline you manage*. A sales manager should open one screen and know: what is in the funnel, what it is worth, where it is stuck, who is not working their leads, why we lose, and whether we will hit the number — for **any period**, not just all-time.

---

## Guiding principle

**The enquiry is already the opportunity.** We do not introduce a separate Lead/Deal/Opportunity object. `Enquiry` already carries owner, value, probability, target close date, source, priority, geography, outcome code and a conversation log; `Quotation` and `SalesOrder` already link back to it. Adding a parallel CRM object would fork the truth and double the data entry — the exact failure mode that makes bolt-on CRMs get abandoned in manufacturing SMEs.

What is missing is not *entities*. It is **time**: nothing in the system records *when* an enquiry moved, *when* a quote was sent, or *when* somebody was supposed to call back and didn't. Every serious CRM metric is a function of elapsed time between events. That is the through-line of this plan.

Second principle: **a metric that cannot be trusted must say so.** `EnquirySummaryDTO.codedCount` already does this — it tells the reader how much of the win-rate split is real coding versus inferred from status. Every derived metric added here carries the same honesty: a coverage figure, or `null` where "0" and "no data" would be confused.

---

## Where we stand — the audit

### Already built and genuinely good

| Capability | Where |
|---|---|
| Enquiry as opportunity: owner, `expectedRevenue`, `probability`, `targetCloseDate`, `enquirySource`, `priority` (HOT/WARM/COLD), `leadQuality`, city/state | `marketing/enquiry/model/Enquiry.java` |
| **Outcome taxonomy separate from workflow status** — WON / LOST / NO_ENGAGEMENT / DECLINED_BY_US / DEFERRED / INVALID | `EnquiryCloseOutcome`, `EnquiryCloseReason` (master table, V155) |
| Honest win rate — LOST in the denominator, walk-aways and silence excluded | `EnquirySummaryDTO`, `EnquiryServiceImpl.getEnquirySummary()` |
| Conversation log with real contact date + channel (CALL/EMAIL/MEETING/NOTE) | `EnquiryConversationRecord` (V156) |
| Quote revisions as a chain | `Quotation.revisionNumber` + `parentQuotationId` |
| Enquiry → Quotation → SalesOrder traceability, **including orders taken without a quote** | `SalesOrder.enquiry` (V157) |
| Booked revenue from real order value, not the typed guess | `EnquiryRepository.sumBookedRevenue()` |
| Filter validation that fails loudly instead of silently returning everything | `EnquiryFilter.KNOWN_PARAMS` |
| Debtor ageing per customer | `accounting/reports/AgeingReportService` |
| Per-order realised margin against actual manufacturing cost | `SalesOrderProfitabilityDto` |

### The gap that matters most

`EnquiryServiceImpl.getEnquirySummary()` **takes no arguments**, and every counting query in `EnquiryRepository` (lines ~189–262) is unbounded. There is no period. A dashboard that cannot answer *"this month versus last month"* is a counter, not a dashboard — and every trend, cohort and run-rate metric below is blocked behind this one signature.

Second: **the backend computes 19 metrics; the UI renders 6.** `EnquiryDashboard.jsx` (85 lines) shows Total Leads, New, In Follow-up, Overdue, Expected Revenue, Won Revenue. `winRatePercent`, `bookedRevenue`, `convertedToOrder`, `noEngagementRevenue`, `openNeverContacted`, `declinedByUs`, `deferred`, `invalid`, `codedCount` are computed on every request and thrown away. (`Enquiry.jsx` renders a slightly richer inline set — the two tile definitions have already drifted apart, which is its own reason to consolidate.)

---

## The metric catalogue

The canonical CRM metric set, scored against what we can compute **today**.

### A. Pipeline health

| # | Metric | Status | Note |
|---|---|---|---|
| A1 | Open pipeline value | PARTIAL | `sumExpectedRevenue()` is all-time incl. closed — needs open-only + period |
| A2 | **Weighted pipeline** (`expectedRevenue × probability`) | DEAD FIELD | `probability` is stored and read by nothing |
| A3 | Open opportunities by stage | HAVE | `countByStatus` per `EnquiryStatus` |
| A4 | Average deal size | DERIVABLE | booked ÷ converted |
| A5 | New pipeline created this period | BLOCKED | needs period filter |
| A6 | **Pipeline coverage ratio** (pipeline ÷ target) | MISSING | no target entity — the number a sales manager actually opens the screen for |
| A7 | Forecast by category (commit / best case / pipeline) | MISSING | `probability` is a raw int with no bucketing |

### B. Conversion

| # | Metric | Status | Note |
|---|---|---|---|
| B1 | Overall win rate | HAVE | outcome-based, already correct |
| B2 | **Stage-to-stage conversion** (where the funnel leaks) | BLOCKED | needs stage history |
| B3 | Enquiry → quote rate | DERIVABLE | `Quotation.enquiry` |
| B4 | Quote → order rate | DERIVABLE | `SalesOrder.quotation` |
| B5 | Quote acceptance rate | HAVE | `QuotationStatus` ACCEPTED/REJECTED |
| B6 | Conversion by source / rep / product / region | PARTIAL | fields exist, no grouped query |

### C. Velocity — *the whole block is blocked on stage history*

| # | Metric | Status | Note |
|---|---|---|---|
| C1 | **Sales cycle length** (enq → won) | CRUDE | `enqDate → closedDate` works; per-stage does not |
| C2 | **Time in stage / stage ageing** | BLOCKED | "14 deals stuck in NEGOTIATION > 30 days" |
| C3 | **Quote turnaround** (enq → quote sent) | CRUDE | `enqDate → qtnDate`; no true `sentDate` |
| C4 | Quote → order elapsed | DERIVABLE | `qtnDate → orderDate` |
| C5 | Sales velocity (`opps × win% × size ÷ cycle`) | BLOCKED | composite of the above |
| C6 | **Lead response time** (enq → first touch) | MISSING | strongest documented predictor of win rate in the field |

### D. Activity & hygiene

| # | Metric | Status | Note |
|---|---|---|---|
| D1 | Overdue follow-ups | HAVE | `countOverdueFollowups()` |
| D2 | Open enquiries never contacted | HAVE | `countOpenNeverContacted()` — excellent, currently unrendered |
| D3 | Activity mix by channel | DERIVABLE | `ConversationType` |
| D4 | Activities per rep per period | BLOCKED | needs period + activity owner |
| D5 | Activities per won deal | BLOCKED | same |
| D6 | Days since last touch (ageing buckets) | PARTIAL | `lastContactedDate` exists; no bucketing |
| D7 | Task list / today's agenda | MISSING | `nextFollowupDate` is one scalar — one open follow-up, no history, no completion record |

### E. Loss analysis

| # | Metric | Status | Note |
|---|---|---|---|
| E1 | Loss reason distribution | HAVE | `EnquiryCloseReason` master |
| E2 | Revenue lost by reason | DERIVABLE | `sumRevenueByOutcome` |
| E3 | **Lost to which competitor** | MISSING | no competitor field — this is the analysis that changes pricing |
| E4 | Price-loss vs delivery-loss split | PARTIAL | depends on close-reason coding discipline |

### F. Rep performance

| # | Metric | Status | Note |
|---|---|---|---|
| F1 | Leaderboard (won value, win rate) | DERIVABLE | `assignedTo` |
| F2 | **Quota attainment** | MISSING | no target entity |
| F3 | Forecast accuracy | MISSING | needs targets + snapshots |
| F4 | Team / territory rollup | MISSING | `AppUser` is flat — no manager, region or team |

### G. Customer

| # | Metric | Status | Note |
|---|---|---|---|
| G1 | Repeat vs new revenue | DERIVABLE | `SalesOrder` by customer + date |
| G2 | Revenue concentration (top-10 %) | DERIVABLE | same |
| G3 | Customer lifetime value | DERIVABLE | same |
| G4 | Dormant / at-risk accounts | MISSING | `Contact` has no `lastOrderDate` |
| G5 | Outstanding + credit exposure on the contact record | PARTIAL | `AgeingReportService` exists, never surfaced in CRM |

### H. Source & campaign

| # | Metric | Status | Note |
|---|---|---|---|
| H1 | Leads by source | FRAGILE | `enquirySource` is **free text** — "IndiaMart" / "Indiamart" / "India Mart" split the report silently |
| H2 | Conversion by source | FRAGILE | same |
| H3 | CAC / source ROI | MISSING | no spend captured anywhere |
| H4 | Campaign attribution | MISSING | no campaign entity |

### I. Communication

| # | Metric | Status | Note |
|---|---|---|---|
| I1 | Templates | HAVE | `MessageTemplate` (WHATSAPP / EMAIL) |
| I2 | Actually sending from the app | MISSING | no `JavaMailSender`, no WhatsApp integration, no sent log |
| I3 | Open / reply rates | MISSING | requires I2 |

**Tally: 12 have · 11 partial or derivable-but-unbuilt · 20 missing.** The first two groups are worth a strong dashboard on their own — that is Phase 1.

---

## Traps found in the code (must be handled)

1. **`Quotation.validTill` is a `String`.** `private String validTill;` — it cannot be compared, sorted or filtered in SQL. "Quotes expiring this week" is one of the highest-value tiles in a quote-driven business and it is currently impossible. Migration must parse whatever formats are already in the column; **assume they are inconsistent and quarantine unparseable values rather than dropping them.**

2. **Status and outcome drift apart, by design.** `EnquirySummaryDTO` already documents this: `won` = outcome WON **plus** uncoded `CONVERTED`. Every new grouped query must apply the same coalescing rule or two tiles on the same screen will disagree. **Extract the rule into one place** (`EnquiryOutcomeResolver`) in Phase 1 rather than copying the `+ countUncodedByStatus(...)` idiom into 20 more queries.

3. **`countOverdueFollowups()` filters on `status NOT IN (CONVERTED, LOST, CLOSED)`** but ignores `JUNK`. A junk enquiry with a stale `nextFollowupDate` inflates the overdue count forever. Fix while adding the period parameter.

4. **`EnquiryConversationRecord.conversationDate` is nullable** with documented fallback to `creationDate`. Any velocity metric reading it must apply the same `COALESCE`, or imported history (whose `creationDate` is the import run, not reality) will produce nonsense response times.

5. **`enquiryConversationRecords` is a `CascadeType.ALL` `@OneToMany`.** Do not compute activity counts by loading enquiries — it will N+1 across the whole register. Aggregate with projection queries against `enquiryConversationRecord` directly.

6. **`Contact.id` is a primitive `int`**, unlike every other entity's `Long`. Grouped customer queries must not silently autobox-compare against `Long` ids.

---

## Data model additions (consolidated across all phases)

| Phase | Migration | Change |
|---|---|---|
| 1 | — | none (period filtering is query-level only) |
| 2 | `V158` | `enquiryStatusHistory` table |
| 3 | `V159` | `quotation.validTill` → `DATE` (+ `validTillRaw` quarantine column) |
| 3 | `V160` | `quotation.sentDate`, `quotation.respondedDate` |
| 3 | `V161` | `enquiry.competitorName`, `enquiry.lostOnPrice` |
| 4 | `V162` | `crmActivity` table |
| 5 | `V163` | `salesTarget` table |
| 5 | `V164` | `enquiry.forecastCategory` |
| 6 | `V165` | `enquirySource` master + FK from `enquiry` |

Next free migration is **V158** (current head: `V157__sales_order_enquiry_link.sql`).

---

## Phase roadmap

### Phase 1 — Period-aware summary + surface what exists *(no schema change)*
Make every metric answer "for which period?", add prior-period deltas, wake up the dead `probability` field, and render the 19 metrics already computed. Add grouped breakdowns (stage, source, owner, channel, geography) that need only existing columns.

**Exit:** `GET /api/enquiry/summary?from=&to=` returns period-bounded figures plus a `previous` block; a delta renders on every tile; weighted pipeline is live; funnel / source / owner / channel breakdowns render; `EnquiryDashboard.jsx` is the single source of tiles and the inline duplicates in `Enquiry.jsx` are removed.

### Phase 2 — Stage history & velocity
`EnquiryStatusHistory` written by an event listener on every status transition, backfilled for existing rows. Unlocks cycle time, time-in-stage, funnel leakage, stuck-deal alerts.

**Exit:** every status change writes a history row; backfill seeds NEW at `enqDate` and the terminal stage at `closedDate` (flagged `source=BACKFILL` so metrics can exclude it); average cycle length, per-stage dwell time, stage-conversion funnel and a "stuck > N days" list all render.

### Phase 3 — Quote intelligence
Fix `validTill`, add real send/response timestamps, add competitor capture.

**Exit:** `validTill` is a `DATE`; "expiring in 7 days" tile works; quote turnaround (enq → sent) and response time (sent → decision) render; revision-count distribution renders; win/loss by competitor renders.

### Phase 4 — Activity & task engine
`CrmActivity` with type, subject, due date, owner, completion and outcome. `nextFollowupDate` becomes a derived read of the earliest open activity rather than a hand-maintained scalar.

**Exit:** activities can be scheduled, completed and reported; lead response time computes; per-rep activity counts and activities-per-won-deal render; a "Today" agenda widget exists.

### Phase 5 — Targets, forecast & customer 360
`SalesTarget` per period per owner. Forecast categories. Customer panel joining order history + `AgeingReportService`.

**Exit:** quota attainment and pipeline coverage ratio render; forecast rolls up by category; opening a contact shows lifetime value, last order date, dormancy and outstanding.

### Phase 6 — Source master, campaigns & the manufacturing moat
`EnquirySource` master (same pattern as `EnquiryCloseReason`) with a de-duplicating migration of the free text. Then the metrics no standalone CRM can compute.

**Exit:** source is a FK with a mapped legacy column; win rate by product family renders off `EnquiredProducts → InventoryItem`; **quoted margin vs realised margin** renders off `SalesOrderProfitabilityDto`; promised-vs-actual delivery renders off `SalesOrder.deliveryDate` vs `DeliveryNote.deliveryDate`.

---

## The moat — metrics only an ERP can compute

Worth stating plainly, because it decides what to build if time runs short. Zoho CRM, Freshsales and HubSpot all do pipeline, activities and forecasting better than we will this year. They **structurally cannot** do these:

1. **Quoted margin vs realised margin.** `SalesOrderProfitabilityDto` gives gross margin against *actual manufacturing cost* — consumed material at real valuation, real operation cost, real overhead. Quote at 22%, deliver at 14%: that gap is invisible to every standalone CRM and it is the most valuable number in an engineer-to-order business.
2. **Win rate by product family, costed.** `EnquiredProducts → InventoryItem → BOM`. A CRM does not know what a product costs, so it cannot tell you that you win 60% on strainers and 12% on valves because the valve BOM is uncompetitive.
3. **Promised vs actual delivery.** `SalesOrder.deliveryDate` vs `DeliveryNote.deliveryDate` — a CRM metric that predicts repeat orders, computable only where sales and production share a database.
4. **Quote turnaround against costing readiness.** In ETO, quote prep *is* the bottleneck, and it waits on BOM/costing data only we hold.

These land in Phase 6 by dependency order, not by importance. If Phase 6 were the only phase to ship beyond Phase 1, it would still be the phase that differentiates the product.

---

## Out of scope (explicitly, for now)

Email/WhatsApp *sending* and delivery tracking (I2/I3 — needs an integration decision and per-tenant credentials); campaign spend and CAC (H3/H4); lead scoring models; territory management beyond a flat owner field; NPS/CSAT capture; mobile app; duplicate-lead detection and merge; web-to-lead capture forms.

Each is a legitimate follow-on. None blocks a dashboard a manager would use daily.
