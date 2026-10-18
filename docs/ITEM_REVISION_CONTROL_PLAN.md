# Item Revision Control — design plan

Status: **plan only, nothing built.** Written 2026-09-21.

## 1. What is wrong today

`InventoryItem.revision` is a `byte` ([InventoryItem.java:45](../src/main/java/com/nextgenmanager/nextgenmanager/items/model/InventoryItem.java#L45)) that a human types into a number field (`ui/src/components/inventoryitem/AddInventoryItem.jsx:768`) and that **no backend code ever reads**. Grep finds exactly one reference — the declaration. It is decoration.

The consequences:

- `editInventoryItem` overwrites the item in place. Change a drawing number or a dimension and every BOM that already used the old geometry silently now claims to use the new one, including approved and active BOMs and BOMs behind closed work orders. There is no record that the part changed.
- `BomPosition` holds `childInventoryItemId` and a quantity and nothing else ([BomPosition.java](../src/main/java/com/nextgenmanager/nextgenmanager/bom/model/BomPosition.java)). A BOM printed last year cannot be reproduced.
- BOMs *do* have real versioning — `versionGroup` / `versionNumber` / `isActiveVersion`, an A/B/C `revision` string, ECO number, change reason, approver, and a seven-state status machine. Items have none of it. The assembly is controlled and its parts are not, which is backwards: the part is the thing a supplier makes.
- The practical failure mode is already in this database. When people cannot revise a part they invent a new item code for it. 52% of the item master sits in the fabricated `IMP-`/`PUR-` namespaces. Revision control is the fix for the root cause.

## 2. Reading of the proposal, and where I would change it

The proposal was: lock the item; only financial fields stay editable; a locked item can never be edited; an unlocked item cannot be used in a new BOM; the BOM carries the item's revision.

Most of that is right and should be built as stated:

- **Lock/release is the correct gate.** Yes.
- **Financials stay editable on a locked item.** Yes, and this is the key insight — price is not engineering. A steel price change is not a new revision of the part. `ProductFinanceSettings` stays on the item, unversioned, always editable by the finance roles. Same for `ProductInventorySettings` (reorder level, batch-tracked, min/max): those are policy, not geometry.
- **The BOM must carry the revision.** Yes — but as a foreign key to a revision *row*, not as a copied string. A string is a label; it pins nothing and prevents nothing. If revisions are rows, "reproduce this BOM as it was" is a join, not an archaeology exercise.

Two changes I would make:

**(a) "Locked can never be edited" must become "locked can be *revised*."** A permanent freeze is not a workflow — parts genuinely change, and if the only escape is a new item code you have rebuilt the `IMP-`/`PUR-` problem by hand. The action on a released item is **Revise**, which opens Rev B as a draft copy of Rev A. Rev A stays released and immutable forever; existing BOMs keep pointing at it until somebody deliberately moves them.

**(b) The interlock belongs at BOM *release*, not BOM *creation*.** Blocking a draft component from entering a draft BOM breaks how design actually happens — a new part and the assembly that uses it get drawn in the same week. The rule that gives the same safety without the deadlock:

> A BOM may not leave `DRAFT`/`PENDING_APPROVAL` while any component it references points at a revision that is not `RELEASED`.

So a draft BOM can freely reference a draft part; the gate fires at approval, exactly like the QC gate on `completeWorkOrder`. Same guarantee, no deadlock.

## 3. Model

Keep `InventoryItem` as the identity of the part. Every existing FK in the ten modules that reference it — stock, PO lines, SO lines, work orders, GRN, accounting — stays pointed at `inventoryItemId` and none of them need to change. **Stock is held by part, not by revision**, which is correct and is also what makes this change small.

Add one table.

### `ItemRevision` (new)

| field | note |
|---|---|
| `id` | PK |
| `inventoryItem` | FK → `inventoryItem`, many revisions per item |
| `revisionCode` | `A`, `B`, `C`… reuse `BomServiceImpl.toRevision/nextRevision`, unique per item |
| `status` | `DRAFT`, `PENDING_APPROVAL`, `RELEASED`, `SUPERSEDED`, `OBSOLETE` |
| `interchangeable` | `Boolean`, see §5 — the most important field here |
| `ecoNumber`, `changeReason` | mirrors the BOM's ECO fields |
| `releasedBy`, `releasedOn` | |
| `supersededBy` | self-FK, nullable |
| `effectiveFrom`, `effectiveTo` | |
| *engineering attributes* | `dimension`, `size`, `weight`, `basicMaterial`, `processType`, `drawingNumber`, `uom`, `hsnCode` — the fields today on `ProductSpecification` plus the two on the item |

Partial unique index: at most one `DRAFT` revision per item at a time. At most one `RELEASED` revision is *current* — carry `currentRevisionId` on `InventoryItem` for the cheap lookup, the way `Bom` carries `isActiveVersion`.

Drawing attachments attach to the **revision**, not to the item. That is the single most common real question ("which drawing did we send the vendor?") and today it has no answer.

### What stays on `InventoryItem`

`itemCode`, `name`, `itemGroupCode`, `itemType`, `remarks`, `ProductFinanceSettings`, `ProductInventorySettings`, and the whole vendor-price subtree. All editable at any time regardless of lock state. Drop the dead `byte revision` field and the UI number box with it.

`ProductSpecification` becomes the *engineering* shape of the current revision. Two options; I would take the first:

1. **Move** its columns onto `ItemRevision` and keep `ProductSpecification` as a read-through view of the current revision, so the existing readers (search specs, exports, PDF templates, `JOIN_FIELD_MAP`) keep compiling. One source of truth.
2. Leave it and duplicate onto the revision. Cheaper now, guarantees drift later. No.

### `BomPosition` gains one column

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "childItemRevisionId")   // nullable during migration, then required
private ItemRevision childItemRevision;
```

`childInventoryItemId` stays. Both are stored: the item for every existing query and every material issue, the revision for reproducibility and for the release gate. Default on add: the item's current released revision, overridable by an engineer who is deliberately building against a draft.

## 4. State machine and who may do what

```
   Create item ──► Rev A  DRAFT ──► PENDING_APPROVAL ──► RELEASED
                       ▲                  │                  │
                       │ reject           │                  │ Revise
                       └──────────────────┘                  ▼
                                                       Rev B  DRAFT
                                                             │ release
   Rev A  SUPERSEDED  ◄──────────────────────────────────────┘
```

- **DRAFT** — every engineering field editable. Usable in a draft BOM. Cannot be used on a PO, a work order, or a sales order.
- **PENDING_APPROVAL** — read-only. Route through the existing generic engine in [common/approval](../src/main/java/com/nextgenmanager/nextgenmanager/common/approval) — it is already policy-driven, so an installation that wants a one-click release just configures a zero-step policy and pays nothing for the machinery.
- **RELEASED** — engineering fields immutable. This is the "locked" state. Item-level fields (name, prices, stock policy) still editable.
- **SUPERSEDED** — was released, a later revision took over. Still immutable, still referenced by history. Never deleted.
- **OBSOLETE** — do not use on anything new. Existing references stand.

Roles: a new `ROLE_ENGINEERING` may create and submit revisions; release is whatever the approval policy says, defaulting to `ROLE_ADMIN`/`ROLE_SUPER_ADMIN`. Finance fields keep the existing `canAccessFinance()` check, unchanged.

## 5. Interchangeability — the field that earns its keep

When Rev B is created the engineer must answer one question: **is Rev B form-fit-function interchangeable with Rev A?** ERPNext and Zoho have no equivalent; it is the difference between a revision system that helps and one that is a filing cabinet.

- **Interchangeable = true** (a tolerance loosened, a note added, a finish changed). Existing Rev A stock may be consumed against Rev B demand. Open POs need no amendment. BOMs may be rolled forward in bulk.
- **Interchangeable = false** (a mounting hole moved). Rev A stock is *not* valid for Rev B. On release the system must produce an impact list: every BOM referencing Rev A, every open PO, every released-but-incomplete work order, every quantity of Rev A stock on hand. Nothing is auto-changed — the list is a worklist for a human, the same way the Planning Desk is.

That impact report is the deliverable users will actually notice.

## 6. Effectivity — what each document pins, and when

| Document | Pins the revision when | Rationale |
|---|---|---|
| BOM position | at add / at BOM release | reproducibility |
| Work order | at WO release, snapshot from the BOM | a WO in progress must not shift under the shop floor |
| Purchase order line | at PO issue | the vendor was sent a specific drawing |
| Sales order line | at order confirmation | what the customer bought |
| GRN / stock | **never** — stock is by part | qty of a part, not qty of a drawing |

A WO already released against Rev A stays on Rev A even after Rev B releases. This is non-negotiable and is the main reason to store the revision FK on the WO snapshot rather than resolve "current" at read time.

## 7. Migration and backfill

Latest migration on disk is **V170**, so:

- **V171** — create `itemRevision`, add `currentRevisionId` to `inventoryItem`, add `childItemRevisionId` to `bomPosition` (nullable).
- **V172** — backfill. Every active item gets one `ItemRevision` row, `revisionCode = 'A'`, `status = RELEASED`, engineering fields copied from its `ProductSpecification` (or left null where it has none), `releasedOn = creationDate`, `interchangeable = null`. Set `currentRevisionId`. Point every existing `bomPosition` at the child item's Rev A.
- **V173** — make `bomPosition.childItemRevisionId` NOT NULL; drop `inventoryItem.revision`.

Backfill everything as `RELEASED`, not `DRAFT`. Landing the whole master in draft would instantly block every BOM approval in the system on day one.

Per the standing rule in this project, data loads go through the API rather than raw SQL — but a schema backfill of a column that has no API yet is legitimately a Flyway job.

## 8. Build order

1. **Model + migrations V171/V172** — `ItemRevision`, repository, backfill. Nothing visible changes; every existing screen keeps working.
2. **Revision service** — `createDraft`, `submit`, `release`, `revise`, `obsolete`, plus `getCurrentReleased(itemId)`. Enforce immutability in `editInventoryItem`: reject any change to an engineering field when the current revision is `RELEASED`, allow item-level and financial fields through.
3. **The release gate** — `changeBomStatus` refuses to leave `DRAFT`/`PENDING_APPROVAL` while any position points at a non-`RELEASED` revision, listing the offenders. Same shape as the QC gate on `completeWorkOrder`.
4. **BOM pinning** — `childItemRevision` populated on add/edit, shown on the BOM screen, printed on the BOM PDF and the work-order pack.
5. **Impact report** — "where used, at this revision": BOMs, open POs, open WOs, stock on hand. Read-only. This is what makes step 6 safe.
6. **Revise + supersede flow, with the interchangeability question** and the bulk roll-forward for the interchangeable case.
7. **UI** — a Revisions tab on the item detail page (history, current, diff between two revisions), a lock chip in the header, the drawing attachment moved under the revision. Follows the module page style already used elsewhere.

Steps 1–3 alone deliver the whole of what was asked. 4–7 are what make it usable.

## 9. Things this plan deliberately does not do

- **No revision in the item code.** `VLV-1042` stays `VLV-1042` at Rev C. Encoding the revision in the code is how you get a 758-row master with 395 invented codes.
- **No revision on stock.** Batch/serial already exist for traceability where it matters; revision-level stock buckets would multiply every inventory row for no gain on a make-to-order shop floor.
- **No auto-migration of BOMs on release.** Even when interchangeable, moving an approved BOM is a human decision with a new BOM version behind it.
- **No change to the BOM version machinery.** It works. Items are being brought up to it, not the other way round.

## 10. Open questions

1. Should releasing Rev B auto-create a new *BOM* version for every parent assembly that uses the part non-interchangeably, or only flag them? My read: flag only — an auto-created BOM version that nobody asked for is noise, and the Planning Desk precedent in this codebase is to surface a decision, not to make it.
2. Does the customer want approval on item revisions at all on day one, or is release-by-one-click enough? The generic approval engine makes this a config choice, so build the hook and default it to zero steps.
3. Purchased parts: does a vendor-supplied commodity (a bolt, a bearing) need revision control, or only manufactured/drawn parts? Suggest a per-item flag — `revisionControlled`, default true for manufactured, false for purchased — so the purchased items do not drag a workflow they do not need.
