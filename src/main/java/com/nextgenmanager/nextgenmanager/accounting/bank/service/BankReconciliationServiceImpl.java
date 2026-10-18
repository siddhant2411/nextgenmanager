package com.nextgenmanager.nextgenmanager.accounting.bank.service;

import com.nextgenmanager.nextgenmanager.accounting.bank.dto.*;
import com.nextgenmanager.nextgenmanager.accounting.bank.model.BankStatementLine;
import com.nextgenmanager.nextgenmanager.accounting.bank.model.BankStatementMapping;
import com.nextgenmanager.nextgenmanager.accounting.bank.repository.BankStatementLineRepository;
import com.nextgenmanager.nextgenmanager.accounting.bank.repository.BankStatementMappingRepository;
import com.nextgenmanager.nextgenmanager.accounting.coa.model.LedgerAccount;
import com.nextgenmanager.nextgenmanager.accounting.coa.repository.LedgerAccountRepository;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.Voucher;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherLine;
import com.nextgenmanager.nextgenmanager.accounting.voucher.model.VoucherStatus;
import com.nextgenmanager.nextgenmanager.accounting.voucher.repository.VoucherLineRepository;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Bank reconciliation: imported statement lines matched 1:1 to the book entries on the bank ledger.
 * Only vouchers that still stand are offered — a reversed voucher and its reversal net to nothing.
 * A match whose book entry is later reversed is "stale": it counts as unmatched and can be matched again.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BankReconciliationServiceImpl implements BankReconciliationService {

    /** Auto-match only pairs a statement line with a book entry dated within this many days of it. */
    static final int MATCH_WINDOW_DAYS = 7;
    private static final int PREVIEW_ROWS = 25;
    private static final int RECENT_MATCH_DAYS = 90;

    private final LedgerAccountRepository ledgerRepo;
    private final BankStatementMappingRepository mappingRepo;
    private final BankStatementLineRepository lineRepo;
    private final VoucherLineRepository voucherLineRepo;

    // ─── Accounts & mapping ──────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<BankAccountDto> accounts() {
        List<BankAccountDto> out = new ArrayList<>();
        for (LedgerAccount la : ledgerRepo.findReconcilable()) {
            Optional<BankStatementMapping> m = mappingRepo.findByLedgerAccountId(la.getId());
            List<BankStatementLine> lines = lineRepo.findLive(la.getId());
            int unmatched = (int) lines.stream().filter(l -> !isMatched(l)).count();
            BankStatementLine last = lines.isEmpty() ? null : lines.get(lines.size() - 1);
            BigDecimal lastBalance = lastBalance(lines);
            out.add(new BankAccountDto(la.getId(), la.getCode(), la.getName(), m.isPresent(),
                    m.map(BankStatementMapping::getReconcileFrom).orElse(null),
                    lines.size(), unmatched, last != null ? last.getTxnDate() : null, lastBalance));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public BankStatementMappingDto getMapping(Long ledgerId) {
        requireReconcilable(ledgerId);
        return mappingRepo.findByLedgerAccountId(ledgerId).map(this::toDto).orElseGet(() -> {
            BankStatementMappingDto blank = new BankStatementMappingDto();
            blank.setLedgerId(ledgerId);
            return blank;
        });
    }

    @Override
    public BankStatementMappingDto saveMapping(Long ledgerId, BankStatementMappingDto dto) {
        LedgerAccount la = requireReconcilable(ledgerId);
        if (blank(dto.getWithdrawalColumn()) && blank(dto.getDepositColumn()) && blank(dto.getAmountColumn())) {
            throw new IllegalArgumentException("Map the withdrawal and deposit columns, or one signed amount column.");
        }
        BankStatementMapping m = mappingRepo.findByLedgerAccountId(ledgerId).orElseGet(() -> {
            BankStatementMapping fresh = new BankStatementMapping();
            fresh.setLedgerAccount(la);
            return fresh;
        });
        m.setHeaderRow(Math.max(1, dto.getHeaderRow()));
        m.setDateColumn(dto.getDateColumn().trim());
        m.setDateFormat(trimToNull(dto.getDateFormat()));
        m.setNarrationColumn(trimToNull(dto.getNarrationColumn()));
        m.setReferenceColumn(trimToNull(dto.getReferenceColumn()));
        m.setWithdrawalColumn(trimToNull(dto.getWithdrawalColumn()));
        m.setDepositColumn(trimToNull(dto.getDepositColumn()));
        m.setAmountColumn(trimToNull(dto.getAmountColumn()));
        m.setBalanceColumn(trimToNull(dto.getBalanceColumn()));
        m.setReconcileFrom(dto.getReconcileFrom());
        return toDto(mappingRepo.save(m));
    }

    // ─── Import ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public StatementPreviewDto preview(MultipartFile file, int headerRow) {
        List<List<String>> rows = StatementFileParser.readRows(bytes(file), file.getOriginalFilename());
        int hr = Math.max(1, Math.min(headerRow, Math.max(rows.size(), 1)));
        List<String> headers = rows.isEmpty() ? List.of() : StatementFileParser.headers(rows, hr);
        return new StatementPreviewDto(file.getOriginalFilename(), rows.size(), hr, headers,
                rows.subList(0, Math.min(rows.size(), PREVIEW_ROWS)));
    }

    @Override
    public StatementImportResultDto importStatement(Long ledgerId, MultipartFile file, String username) {
        LedgerAccount la = requireReconcilable(ledgerId);
        BankStatementMapping mapping = mappingRepo.findByLedgerAccountId(ledgerId)
                .orElseThrow(() -> new IllegalStateException("Map this account's statement columns before importing."));
        List<List<String>> rows = StatementFileParser.readRows(bytes(file), file.getOriginalFilename());
        StatementFileParser.ParseResult parsed = StatementFileParser.parse(rows, mapping);

        // Re-uploading an overlapping statement must not double the rows. Identical rows are counted,
        // not just looked up, so two genuine ₹500 charges on the same day both survive.
        Map<String, Integer> existing = new HashMap<>();
        for (BankStatementLine l : lineRepo.findLive(ledgerId)) {
            existing.merge(key(l.getTxnDate(), l.getWithdrawal(), l.getDeposit(), l.getReference(), l.getNarration()), 1, Integer::sum);
        }
        String batch = UUID.randomUUID().toString();
        int imported = 0;
        int duplicates = 0;
        for (StatementFileParser.ParsedRow r : parsed.rows()) {
            String k = key(r.date(), r.withdrawal(), r.deposit(), r.reference(), r.narration());
            Integer seen = existing.get(k);
            if (seen != null && seen > 0) {
                existing.put(k, seen - 1);
                duplicates++;
                continue;
            }
            BankStatementLine l = new BankStatementLine();
            l.setLedgerAccount(la);
            l.setTxnDate(r.date());
            l.setNarration(truncate(r.narration(), 500));
            l.setReference(truncate(r.reference(), 120));
            l.setWithdrawal(r.withdrawal());
            l.setDeposit(r.deposit());
            l.setBalance(r.balance());
            l.setImportBatch(batch);
            l.setSourceFileName(truncate(file.getOriginalFilename(), 255));
            lineRepo.save(l);
            imported++;
        }
        int matched = imported > 0 ? autoMatch(ledgerId, username) : 0;
        List<String> skipped = parsed.skipped().size() > 50 ? parsed.skipped().subList(0, 50) : parsed.skipped();
        return new StatementImportResultDto(batch, imported, duplicates, matched, skipped);
    }

    private static String key(LocalDate d, BigDecimal w, BigDecimal dep, String ref, String narr) {
        return d + "|" + money(w).toPlainString() + "|" + money(dep).toPlainString() + "|"
                + norm(ref) + "|" + norm(narr);
    }

    // ─── Workspace & matching ────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ReconciliationWorkspaceDto workspace(Long ledgerId, LocalDate asOf) {
        LedgerAccount la = requireReconcilable(ledgerId);
        LocalDate from = reconcileFrom(ledgerId);
        List<BankStatementLine> stmt = lineRepo.findLiveUpTo(ledgerId, asOf);
        Map<Long, BankStatementLine> byVoucherLine = matchesByVoucherLine(lineRepo.findLive(ledgerId));

        List<StatementLineDto> unmatchedStmt = new ArrayList<>();
        List<StatementLineDto> recent = new ArrayList<>();
        for (BankStatementLine s : stmt) {
            if (!isMatched(s)) unmatchedStmt.add(toDto(s));
            else if (!s.getTxnDate().isBefore(asOf.minusDays(RECENT_MATCH_DAYS))) recent.add(toDto(s));
        }
        Collections.reverse(recent);

        List<BookLineDto> unmatchedBook = new ArrayList<>();
        for (VoucherLine vl : bookLines(ledgerId, asOf, from)) {
            if (!byVoucherLine.containsKey(vl.getId())) unmatchedBook.add(toDto(vl, null));
        }
        return new ReconciliationWorkspaceDto(la.getId(), la.getCode(), la.getName(), asOf, from, unmatchedStmt, unmatchedBook, recent);
    }

    @Override
    public int autoMatch(Long ledgerId, String username) {
        requireReconcilable(ledgerId);
        LocalDate from = reconcileFrom(ledgerId);
        List<BankStatementLine> all = lineRepo.findLive(ledgerId);
        Map<Long, BankStatementLine> taken = matchesByVoucherLine(all);

        List<StatementCandidate> stmts = new ArrayList<>();
        Map<Long, BankStatementLine> stmtById = new HashMap<>();
        for (BankStatementLine s : all) {
            if (isMatched(s)) continue;
            stmts.add(new StatementCandidate(s.getId(), s.getTxnDate(), money(s.signedAmount()), s.getReference()));
            stmtById.put(s.getId(), s);
        }
        if (stmts.isEmpty()) return 0;
        LocalDate horizon = stmts.stream().map(StatementCandidate::date).max(LocalDate::compareTo).get().plusDays(MATCH_WINDOW_DAYS);

        List<BookCandidate> books = new ArrayList<>();
        Map<Long, VoucherLine> bookById = new HashMap<>();
        for (VoucherLine vl : bookLines(ledgerId, horizon, from)) {
            if (taken.containsKey(vl.getId())) continue;
            Voucher v = vl.getVoucher();
            books.add(new BookCandidate(vl.getId(), v.getDate(), money(vl.getDrAmount().subtract(vl.getCrAmount())),
                    String.join(" ", nn(v.getVoucherNumber()), nn(v.getNarration()), nn(vl.getNarration()))));
            bookById.put(vl.getId(), vl);
        }

        List<long[]> pairs = pair(stmts, books);
        Date now = new Date();
        for (long[] p : pairs) {
            BankStatementLine s = stmtById.get(p[0]);
            s.setVoucherLine(bookById.get(p[1]));
            s.setMatchedBy(username + " (auto)");
            s.setMatchedAt(now);
            lineRepo.save(s);
        }
        return pairs.size();
    }

    record StatementCandidate(Long id, LocalDate date, BigDecimal signed, String reference) {}

    record BookCandidate(Long id, LocalDate date, BigDecimal signed, String text) {}

    /**
     * Pairs each statement line (oldest first) with a book entry of the same signed amount dated within
     * {@link #MATCH_WINDOW_DAYS}. A statement reference that appears in the entry's number or narration
     * narrows the field; among what is left, only a single candidate — or a single nearest date — is
     * taken. Anything ambiguous is left for a person.
     */
    static List<long[]> pair(List<StatementCandidate> stmts, List<BookCandidate> books) {
        List<StatementCandidate> ordered = new ArrayList<>(stmts);
        ordered.sort(Comparator.comparing(StatementCandidate::date).thenComparing(StatementCandidate::id));
        Set<Long> used = new HashSet<>();
        List<long[]> out = new ArrayList<>();
        for (StatementCandidate s : ordered) {
            List<BookCandidate> fits = books.stream()
                    .filter(b -> !used.contains(b.id()))
                    .filter(b -> b.signed().compareTo(s.signed()) == 0)
                    .filter(b -> Math.abs(ChronoUnit.DAYS.between(b.date(), s.date())) <= MATCH_WINDOW_DAYS)
                    .toList();
            if (fits.isEmpty()) continue;
            String ref = norm(s.reference());
            if (ref.length() >= 4) {
                List<BookCandidate> byRef = fits.stream().filter(b -> norm(b.text()).contains(ref)).toList();
                if (!byRef.isEmpty()) fits = byRef;
            }
            BookCandidate chosen = null;
            if (fits.size() == 1) {
                chosen = fits.get(0);
            } else {
                long best = fits.stream().mapToLong(b -> Math.abs(ChronoUnit.DAYS.between(b.date(), s.date()))).min().getAsLong();
                List<BookCandidate> nearest = fits.stream()
                        .filter(b -> Math.abs(ChronoUnit.DAYS.between(b.date(), s.date())) == best).toList();
                if (nearest.size() == 1) chosen = nearest.get(0);
            }
            if (chosen != null) {
                used.add(chosen.id());
                out.add(new long[]{s.id(), chosen.id()});
            }
        }
        return out;
    }

    @Override
    public void match(Long statementLineId, Long voucherLineId, String username) {
        BankStatementLine s = lineRepo.findByIdAndDeletedDateIsNull(statementLineId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement line not found: " + statementLineId));
        if (isMatched(s)) throw new IllegalStateException("This statement line is already matched. Unmatch it first.");
        VoucherLine vl = voucherLineRepo.findById(voucherLineId)
                .orElseThrow(() -> new ResourceNotFoundException("Book entry not found: " + voucherLineId));
        if (!vl.getLedgerAccount().getId().equals(s.getLedgerAccount().getId())) {
            throw new IllegalArgumentException("That book entry is on a different ledger.");
        }
        if (!isStanding(vl)) throw new IllegalStateException("That book entry has been reversed and cannot be matched.");
        if (lineRepo.existsByVoucherLineIdAndDeletedDateIsNull(voucherLineId)) {
            throw new IllegalStateException("That book entry is already matched to another statement line.");
        }
        BigDecimal book = money(vl.getDrAmount().subtract(vl.getCrAmount()));
        if (book.compareTo(money(s.signedAmount())) != 0) {
            throw new IllegalArgumentException("Amounts differ: the statement shows " + describe(s.signedAmount())
                    + " but the book entry is " + describe(book) + ".");
        }
        s.setVoucherLine(vl);
        s.setMatchedBy(username);
        s.setMatchedAt(new Date());
        lineRepo.save(s);
    }

    @Override
    public void unmatch(Long statementLineId) {
        BankStatementLine s = lineRepo.findByIdAndDeletedDateIsNull(statementLineId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement line not found: " + statementLineId));
        s.setVoucherLine(null);
        s.setMatchedBy(null);
        s.setMatchedAt(null);
        lineRepo.save(s);
    }

    @Override
    public void deleteStatementLine(Long statementLineId) {
        BankStatementLine s = lineRepo.findByIdAndDeletedDateIsNull(statementLineId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement line not found: " + statementLineId));
        if (isMatched(s)) throw new IllegalStateException("Unmatch the line before removing it.");
        s.setVoucherLine(null);
        s.setDeletedDate(new Date());
        lineRepo.save(s);
    }

    // ─── Reconciliation statement ────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public BankReconciliationStatementDto statement(Long ledgerId, LocalDate asOf) {
        LedgerAccount la = requireReconcilable(ledgerId);
        LocalDate from = reconcileFrom(ledgerId);

        BigDecimal book = BigDecimal.ZERO;
        for (Object[] r : voucherLineRepo.aggregateByAccount(asOf)) {
            if (ledgerId.equals(r[0])) book = nz((BigDecimal) r[1]).subtract(nz((BigDecimal) r[2]));
        }
        if (la.getOpeningBalance() != null && la.getOpeningBalance().signum() != 0) {
            book = book.add("DR".equals(la.getOpeningBalanceDrCr()) ? la.getOpeningBalance() : la.getOpeningBalance().negate());
        }
        book = money(book);

        // A book entry is outstanding at asOf unless the bank had cleared it by then.
        Map<Long, BankStatementLine> byVoucherLine = matchesByVoucherLine(lineRepo.findLive(ledgerId));
        List<BookLineDto> notCredited = new ArrayList<>();
        List<BookLineDto> notDebited = new ArrayList<>();
        for (VoucherLine vl : bookLines(ledgerId, asOf, from)) {
            BankStatementLine s = byVoucherLine.get(vl.getId());
            if (s != null && !s.getTxnDate().isAfter(asOf)) continue;
            BookLineDto dto = toDto(vl, s);
            if (vl.getDrAmount().signum() > 0) notCredited.add(dto); else notDebited.add(dto);
        }

        // A bank line is missing from the books at asOf unless it is matched to an entry dated by then.
        List<StatementLineDto> bankCredits = new ArrayList<>();
        List<StatementLineDto> bankDebits = new ArrayList<>();
        List<BankStatementLine> stmt = lineRepo.findLiveUpTo(ledgerId, asOf);
        for (BankStatementLine s : stmt) {
            if (isMatched(s) && !s.getVoucherLine().getVoucher().getDate().isAfter(asOf)) continue;
            if (s.getDeposit().signum() > 0) bankCredits.add(toDto(s)); else bankDebits.add(toDto(s));
        }

        BigDecimal notCreditedTotal = sumBook(notCredited, true);
        BigDecimal notDebitedTotal = sumBook(notDebited, false);
        BigDecimal creditsTotal = money(bankCredits.stream().map(StatementLineDto::deposit).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal debitsTotal = money(bankDebits.stream().map(StatementLineDto::withdrawal).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal computed = book.subtract(notCreditedTotal).add(notDebitedTotal).add(creditsTotal).subtract(debitsTotal);

        BankStatementLine lastWithBalance = null;
        for (BankStatementLine s : stmt) if (s.getBalance() != null) lastWithBalance = s;
        BigDecimal stmtBalance = lastWithBalance != null ? money(lastWithBalance.getBalance()) : null;
        BigDecimal diff = stmtBalance != null ? stmtBalance.subtract(computed) : null;
        boolean ties = diff != null && diff.abs().compareTo(new BigDecimal("0.01")) < 0;

        return new BankReconciliationStatementDto(la.getId(), la.getCode(), la.getName(), asOf, from, book,
                notCredited, notCreditedTotal, notDebited, notDebitedTotal,
                bankCredits, creditsTotal, bankDebits, debitsTotal,
                computed, stmtBalance, lastWithBalance != null ? lastWithBalance.getTxnDate() : null, diff, ties);
    }

    private static BigDecimal sumBook(List<BookLineDto> lines, boolean dr) {
        return money(lines.stream().map(l -> dr ? l.dr() : l.cr()).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private LedgerAccount requireReconcilable(Long ledgerId) {
        LedgerAccount la = ledgerRepo.findByIdAndDeletedDateIsNull(ledgerId)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger account not found: " + ledgerId));
        if (!la.isBankAccount() && !la.isReconcilable()) {
            throw new IllegalArgumentException(la.getName() + " is not a bank account. Mark it as a bank account in the chart of accounts first.");
        }
        return la;
    }

    private LocalDate reconcileFrom(Long ledgerId) {
        return mappingRepo.findByLedgerAccountId(ledgerId).map(BankStatementMapping::getReconcileFrom).orElse(null);
    }

    /** Standing book entries on the ledger up to asOf, from the reconcile-from date on. */
    private List<VoucherLine> bookLines(Long ledgerId, LocalDate asOf, LocalDate from) {
        return voucherLineRepo.standingLinesUpTo(ledgerId, asOf).stream()
                .filter(vl -> from == null || !vl.getVoucher().getDate().isBefore(from))
                .toList();
    }

    /** Live statement lines keyed by the book entry they clear (stale matches left out). */
    private static Map<Long, BankStatementLine> matchesByVoucherLine(List<BankStatementLine> lines) {
        Map<Long, BankStatementLine> map = new HashMap<>();
        for (BankStatementLine s : lines) if (isMatched(s)) map.put(s.getVoucherLine().getId(), s);
        return map;
    }

    static boolean isMatched(BankStatementLine s) {
        return s.getVoucherLine() != null && isStanding(s.getVoucherLine());
    }

    static boolean isStanding(VoucherLine vl) {
        Voucher v = vl.getVoucher();
        return vl.getDeletedDate() == null && v.getDeletedDate() == null
                && v.getStatus() == VoucherStatus.POSTED && v.getReversalOf() == null;
    }

    private static BigDecimal lastBalance(List<BankStatementLine> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) if (lines.get(i).getBalance() != null) return lines.get(i).getBalance();
        return null;
    }

    private BankStatementMappingDto toDto(BankStatementMapping m) {
        BankStatementMappingDto d = new BankStatementMappingDto();
        d.setLedgerId(m.getLedgerAccount().getId());
        d.setSaved(true);
        d.setHeaderRow(m.getHeaderRow());
        d.setDateColumn(m.getDateColumn());
        d.setDateFormat(m.getDateFormat());
        d.setNarrationColumn(m.getNarrationColumn());
        d.setReferenceColumn(m.getReferenceColumn());
        d.setWithdrawalColumn(m.getWithdrawalColumn());
        d.setDepositColumn(m.getDepositColumn());
        d.setAmountColumn(m.getAmountColumn());
        d.setBalanceColumn(m.getBalanceColumn());
        d.setReconcileFrom(m.getReconcileFrom());
        return d;
    }

    private static StatementLineDto toDto(BankStatementLine s) {
        VoucherLine vl = s.getVoucherLine();
        boolean stale = vl != null && !isStanding(vl);
        Voucher v = vl != null && !stale ? vl.getVoucher() : null;
        return new StatementLineDto(s.getId(), s.getTxnDate(), s.getNarration(), s.getReference(),
                s.getWithdrawal(), s.getDeposit(), s.getBalance(),
                v != null ? vl.getId() : null, v != null ? v.getVoucherNumber() : null, v != null ? v.getDate() : null, stale);
    }

    private static BookLineDto toDto(VoucherLine vl, BankStatementLine matchedTo) {
        Voucher v = vl.getVoucher();
        String narration = vl.getNarration() != null && !vl.getNarration().isBlank() && !vl.getNarration().equals(v.getNarration())
                ? (v.getNarration() != null ? v.getNarration() + " — " : "") + vl.getNarration()
                : v.getNarration();
        return new BookLineDto(vl.getId(), v.getId(), v.getVoucherNumber(), v.getVoucherType().name(), v.getDate(),
                narration, vl.getDrAmount(), vl.getCrAmount(),
                matchedTo != null ? matchedTo.getId() : null, matchedTo != null ? matchedTo.getTxnDate() : null);
    }

    private static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a statement file to upload.");
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file.");
        }
    }

    private static String describe(BigDecimal signed) {
        return (signed.signum() >= 0 ? "a deposit of ₹" : "a withdrawal of ₹") + money(signed.abs()).toPlainString();
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String nn(String s) {
        return s == null ? "" : s;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        return blank(s) ? null : s.trim();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal money(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }
}
