package vn.printnest.nesting.engine;

import org.springframework.stereotype.Component;
import vn.printnest.common.ApiException;
import vn.printnest.common.ErrorCode;
import vn.printnest.common.Units;
import vn.printnest.nesting.model.NestResult;
import vn.printnest.nesting.model.NestStats;
import vn.printnest.nesting.model.Placement;
import vn.printnest.nesting.model.Sheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Bo dieu phoi thuat toan dan khuon.
 *
 * <p>Bai toan la <em>strip packing</em>: chieu rong co dinh
 * {@code W = sheetWidth - 2*margin}, can toi thieu hoa chieu dai {@code L}.
 *
 * <h2>Cac buoc</h2>
 * <ol>
 *   <li><b>No hinh.</b> Moi hinh duoc cong them tron mot {@code gap} vao ca hai chieu
 *       (nua gap moi canh). Nho vay hai hinh ke nhau bat ky luon ho dung {@code gap}.
 *       Khi ghi toa do that, ta tru lai nua gap.</li>
 *   <li><b>Sinh phuong an.</b> Chay tat ca to hop
 *       {4 heuristic MaxRects} x {2 thu tu sap xep} x {2 che do xoay} cong them
 *       {@link ShelfPacker} lam doi chung.</li>
 *   <li><b>Tim nhi phan chieu dai.</b> Voi moi to hop, tim {@code L} nho nhat ma phuong
 *       an do van xep het hinh. Ep chieu dai xuong buoc packer phai nen chat - day la
 *       ly do chinh giup ty le lap day vuot xa mot lan xep khong rang buoc.</li>
 *   <li><b>Chon phuong an tot nhat</b> theo {@code L} nho nhat.</li>
 *   <li><b>Cat nhieu tam</b> khi co {@code maxSheetLength}: do hinh vao tam hien tai toi
 *       khi day, chot tam, mo tam moi. Sau khi biet CHINH XAC nhung hinh nao thuoc mot
 *       tam, tam do duoc xep lai mot lan nua de nen sat.</li>
 * </ol>
 *
 * <p>Toan bo tinh toan dung so nguyen don vi 1/100 mm nen khong co sai so dau phay dong,
 * va khong co yeu to ngau nhien nao: cung dau vao luon cho cung dau ra.
 */
@Component
public class NestingEngine {

    /**
     * Cac thu tu sap xep ban dau duoc thu nghiem.
     *
     * <p>Thu tu dat quyet dinh chat luong khong kem gi heuristic: dat hinh to truoc thi
     * hinh nho con cho nhoi vao khe, nhung "to" theo tieu chi nao thi tuy bo du lieu.
     * Engine chay het va lay ket qua tot nhat nen khong can doan truoc.
     */
    private enum SortOrder {
        /** Dien tich giam dan - tot cho bo hinh kich thuoc lech nhau nhieu. */
        AREA_DESC,
        /** Canh dai giam dan - tot cho bo hinh dai va hep. */
        LONG_SIDE_DESC,
        /** Canh ngan giam dan - tot khi nhieu hinh gan vuong. */
        SHORT_SIDE_DESC,
        /** Chieu cao giam dan - hop voi loi xep theo tang. */
        HEIGHT_DESC,
        /** Chieu rong giam dan - hop khi muon lap day be ngang truoc. */
        WIDTH_DESC,
        /** Chu vi giam dan - tieu chi trung hoa giua dien tich va canh dai. */
        PERIMETER_DESC
    }

    /** So lan lap toi da cua vong tim nhi phan tren mot to hop. */
    private static final int BINARY_SEARCH_STEPS = 40;

    /** Chan an toan chong vong lap vo han khi cat nhieu tam. */
    private static final int MAX_SHEETS = 500;

    /** So vong leo doi o buoc tinh chinh cuc bo. */
    private static final int LOCAL_SEARCH_ROUNDS = 300;

    /** Hat gieo co dinh: bao dam cung dau vao luon cho cung dau ra. */
    private static final long[] LOCAL_SEARCH_SEEDS = {20240917L, 7L, 1337L};

    /** Ty le chieu dai tinh tu dinh duoc coi la "dai thua" khi tinh chinh. */
    private static final double TOP_BAND_RATIO = 0.12;

    /** Cu bao nhieu vong thi dung mot nuoc di nham vao dai tren cung. */
    private static final int TOP_BAND_MOVE_PERIOD = 8;

    /**
     * Chay toan bo qua trinh dan khuon.
     *
     * @param input tham so va danh sach hinh
     * @return ket qua kem so lieu thong ke
     * @throws ApiException khi co hinh rong hon kho, hoac thuat toan khong hoi tu
     */
    public NestResult nest(EngineInput input) {
        int marginCmm = Units.mmToCmmCeil(input.marginMm());
        int gapCmm = Units.mmToCmmCeil(input.gapMm());
        int sheetWidthCmm = Units.mmToCmmFloor(input.sheetWidthMm());
        // Moi hinh da duoc no them tron mot gap, nhung hinh sat bien KHONG can gap phia
        // ngoai. Bu lai bang cach noi vung kha dung them dung mot gap: hinh sat bien phai
        // se ket thuc dung tai mep le, khong phi mot dai gap doc theo hai canh kho.
        int usableWidth = sheetWidthCmm - 2 * marginCmm + gapCmm;

        if (usableWidth <= gapCmm) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Le bien qua lon so voi kho ngang: khong con vung nao de xep hinh.");
        }

        List<Piece> pieces = buildPieces(input, gapCmm);
        validateFits(pieces, usableWidth, input);

        Integer maxSheetLengthCmm = null;
        if (input.maxSheetLengthMm() != null) {
            int total = Units.mmToCmmFloor(input.maxSheetLengthMm());
            // Cung ly do voi chieu rong: hinh tren cung khong can gap phia ngoai.
            maxSheetLengthCmm = total - 2 * marginCmm + gapCmm;
            int tallest = tallestPiece(pieces, input.allowRotateGlobal());
            if (maxSheetLengthCmm < tallest) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "Chieu dai toi da moi file (" + Units.round2(input.maxSheetLengthMm() / 10d)
                                + " cm) nho hon hinh cao nhat (" + Units.round2(Units.cmmToMm(tallest) / 10d)
                                + " cm). Hay tang gioi han chieu dai.");
            }
        }

        List<PackResult> packedSheets = maxSheetLengthCmm == null
                ? List.of(packSingleSheet(pieces, usableWidth, input.allowRotateGlobal()))
                : packMultipleSheets(pieces, usableWidth, maxSheetLengthCmm, input.allowRotateGlobal());

        return toResult(packedSheets, input, pieces.size(), marginCmm, gapCmm, sheetWidthCmm);
    }

    // ------------------------------------------------------------------
    // Chuan bi du lieu
    // ------------------------------------------------------------------

    /** Nhan ban moi item thanh {@code quantity} mieng va no them gap. */
    private List<Piece> buildPieces(EngineInput input, int gapCmm) {
        List<Piece> pieces = new ArrayList<>();
        int id = 0;
        int typeIndex = 0;
        for (EngineItem item : input.items()) {
            int realW = Units.mmToCmmCeil(item.widthMm());
            int realH = Units.mmToCmmCeil(item.heightMm());
            if (realW <= 0 || realH <= 0) {
                throw new ApiException(ErrorCode.SIZE_UNREADABLE,
                        "Hinh \"" + item.label() + "\" co kich thuoc khong hop le.");
            }
            boolean allowRotate = input.allowRotateGlobal() && item.allowRotate();
            for (int i = 0; i < item.quantity(); i++) {
                pieces.add(new Piece(id++, item.fileId(), item.label(),
                        realW + gapCmm, realH + gapCmm, realW, realH,
                        allowRotate, item.categoryIndex(), typeIndex, false));
            }
            typeIndex++;
        }
        return pieces;
    }

    /** Chan som truong hop hinh khong the nao vua kho, ke ca sau khi xoay. */
    private void validateFits(List<Piece> pieces, int usableWidth, EngineInput input) {
        for (Piece piece : pieces) {
            boolean fitsAsIs = piece.w() <= usableWidth;
            boolean fitsRotated = piece.allowRotate() && piece.h() <= usableWidth;
            if (!fitsAsIs && !fitsRotated) {
                double wCm = Units.cmmToMm(piece.realW()) / 10d;
                double hCm = Units.cmmToMm(piece.realH()) / 10d;
                double sheetCm = input.sheetWidthMm() / 10d;
                String rotateNote = piece.allowRotate() ? " (da tinh ca truong hop xoay)" : " va khong duoc phep xoay";
                throw new ApiException(ErrorCode.ITEM_WIDER_THAN_SHEET,
                        "Hinh \"" + piece.label() + "\" " + Units.round2(wCm) + "x" + Units.round2(hCm)
                                + " cm rong hon kho " + Units.round2(sheetCm) + " cm" + rotateNote + ".",
                        Map.of("fileId", piece.fileId(),
                                "label", piece.label(),
                                "widthMm", Units.cmmToMm(piece.realW()),
                                "heightMm", Units.cmmToMm(piece.realH())));
            }
        }
    }

    /** Chieu cao nho nhat ma mot hinh buoc phai chiem (co xoay thi lay canh ngan hon). */
    private int tallestPiece(List<Piece> pieces, boolean allowRotateGlobal) {
        int tallest = 0;
        for (Piece piece : pieces) {
            boolean canRotate = allowRotateGlobal && piece.allowRotate();
            int minHeight = canRotate ? Math.min(piece.w(), piece.h()) : piece.h();
            tallest = Math.max(tallest, minHeight);
        }
        return tallest;
    }

    // ------------------------------------------------------------------
    // Xep mot tam (khong gioi han chieu dai)
    // ------------------------------------------------------------------

    /**
     * Tim bo tri ngan nhat cho toan bo hinh tren mot tam duy nhat.
     *
     * <p>Chien luoc: voi moi to hop thuat toan, tim nhi phan chieu dai nho nhat con xep
     * duoc het. Can duoi la dien tich ly thuyet, can tren lay tu lan xep tu do.
     */
    private PackResult packSingleSheet(List<Piece> pieces, int usableWidth, boolean allowRotate) {
        int lowerBound = areaLowerBound(pieces, usableWidth);
        int upperBound = freeUpperBound(pieces, usableWidth, allowRotate);

        PackResult best = null;
        // Giu phuong an tot nhat cua TUNG heuristic roi tinh chinh ca bon. Chi tinh chinh
        // phuong an dan dau de mac ket o mot cuc tri dia phuong duy nhat; xuat phat tu bon
        // diem khac nhau cho co hoi thoat ra.
        Map<Heuristic, PackResult> bestPerHeuristic = new EnumMap<>(Heuristic.class);
        Map<Heuristic, Plan> planPerHeuristic = new EnumMap<>(Heuristic.class);

        for (Variant variant : buildVariants(pieces, usableWidth, allowRotate)) {
            for (SortOrder order : SortOrder.values()) {
                List<Piece> sorted = sortPieces(variant.pieces(), order);
                for (Heuristic heuristic : Heuristic.values()) {
                    PackResult candidate = minimizeLength(sorted, usableWidth, lowerBound, upperBound,
                            variant.allowRotate(), heuristic);
                    if (isBetter(candidate, bestPerHeuristic.get(heuristic))) {
                        bestPerHeuristic.put(heuristic, candidate);
                        planPerHeuristic.put(heuristic, new Plan(sorted, heuristic, variant.allowRotate()));
                    }
                    best = betterOf(best, candidate);
                }
                PackResult shelf = minimizeLengthShelf(sorted, usableWidth, lowerBound, upperBound,
                        variant.allowRotate());
                best = betterOf(best, shelf);
            }
        }

        if (best == null || !best.packedEverything()) {
            throw new ApiException(ErrorCode.NESTING_FAILED,
                    "Thuat toan khong xep duoc het so hinh yeu cau. Hay thu noi long khoang cach hoac cho phep xoay.");
        }

        for (Heuristic heuristic : Heuristic.values()) {
            Plan plan = planPerHeuristic.get(heuristic);
            PackResult start = bestPerHeuristic.get(heuristic);
            if (plan == null || start == null) {
                continue;
            }
            // Nhieu hat gieo khac nhau cho ket qua khac nhau ro ret: leo doi tu mot diem
            // xuat phat de ket o cung mot cuc tri. Chay vai hat co dinh roi lay cai tot
            // nhat van tat dinh, ma phu duoc rong hon nhieu so voi keo dai mot mach chay.
            for (long seed : LOCAL_SEARCH_SEEDS) {
                best = betterOf(best, refine(start, plan, usableWidth, lowerBound, seed));
            }
        }
        return best;
    }

    /** Phuong an da sinh ra mot ket qua: thu tu hinh, heuristic va che do xoay. */
    private record Plan(List<Piece> order, Heuristic heuristic, boolean allowRotate) {
    }

    /**
     * Tinh chinh cuc bo quanh phuong an tot nhat.
     *
     * <p>Cac heuristic tham lam bi ket o cuc tri dia phuong: doi mot chut thu tu dat la
     * ket qua co the ngan di. Buoc nay xao tron nhe thu tu cua phuong an tot nhat roi xep
     * lai, giu lai neu ngan hon - ky thuat leo doi co dien.
     *
     * <p>Bo sinh so ngau nhien duoc gieo hat CO DINH (hat co dinh) nen ham
     * van tat dinh: cung dau vao cho cung day phep hoan vi va cung ket qua.
     */
    private PackResult refine(PackResult best, Plan plan, int usableWidth, int lowerBound, long seed) {
        Random random = new Random(seed);
        List<Piece> current = new ArrayList<>(plan.order());
        PackResult currentBest = best;
        int size = current.size();
        if (size < 2) {
            return currentBest;
        }

        for (int round = 0; round < LOCAL_SEARCH_ROUNDS; round++) {
            // Cu moi vai vong lai nham vao dai tren cung - phan thua nhat cua bo tri;
            // cac vong con lai doi cho ngau nhien de khong lap mai mot nuoc di.
            List<Piece> candidateOrder = round % TOP_BAND_MOVE_PERIOD == 0
                    ? promoteTopBand(current, currentBest, random)
                    : new ArrayList<>(current);

            int swaps = 1 + random.nextInt(Math.max(1, size / 12));
            for (int s = 0; s < swaps; s++) {
                int i = random.nextInt(size);
                int j = random.nextInt(size);
                Piece tmp = candidateOrder.get(i);
                candidateOrder.set(i, candidateOrder.get(j));
                candidateOrder.set(j, tmp);
            }

            PackResult attempt = minimizeLength(candidateOrder, usableWidth, lowerBound,
                    currentBest.usedLength(), plan.allowRotate(), plan.heuristic());
            if (isBetter(attempt, currentBest)) {
                currentBest = attempt;
                current = candidateOrder;
            }
        }
        return currentBest;
    }

    /**
     * Dua cac hinh dang nam o dai TREN CUNG cua bo tri len dau thu tu dat.
     *
     * <p>Dai tren cung la phan thua nhat - thuong chi vai hinh le lam ca tam dai them.
     * Cho chung dat truoc de chung duoc chen vao cac khe con trong o phia duoi, thay vi
     * bi day len tao ra mot hang gan nhu rong.
     */
    private static List<Piece> promoteTopBand(List<Piece> order, PackResult result, Random random) {
        int threshold = (int) (result.usedLength() * (1d - TOP_BAND_RATIO));
        Set<Integer> topIds = new HashSet<>();
        for (PlacedPiece placed : result.placed()) {
            if (placed.top() > threshold) {
                topIds.add(placed.piece().id());
            }
        }
        if (topIds.isEmpty() || topIds.size() == order.size()) {
            return new ArrayList<>(order);
        }

        // Nhich tung hinh len mot doan NGAU NHIEN chu khong day han len dau danh sach:
        // day han len dau se dao lon trat tu to-truoc-nho-sau von la diem manh cua
        // phuong an xuat phat, va thuc te lam ket qua toi di.
        List<Piece> reordered = new ArrayList<>(order);
        for (int i = 1; i < reordered.size(); i++) {
            if (!topIds.contains(reordered.get(i).id())) {
                continue;
            }
            int target = random.nextInt(i + 1);
            Piece moved = reordered.remove(i);
            reordered.add(target, moved);
        }
        return reordered;
    }

    /** Ung vien co tot hon ket qua dang giu khong (phai xep het va ngan hon). */
    private static boolean isBetter(PackResult candidate, PackResult current) {
        if (candidate == null || !candidate.packedEverything()) {
            return false;
        }
        return current == null || candidate.usedLength() < current.usedLength();
    }

    /** Tim nhi phan chieu dai nho nhat cho mot to hop MaxRects cu the. */
    private PackResult minimizeLength(List<Piece> sorted, int usableWidth, int lowerBound, int upperBound,
                                      boolean allowRotate, Heuristic heuristic) {
        PackResult best = null;
        int lo = lowerBound;
        int hi = upperBound;

        for (int step = 0; step < BINARY_SEARCH_STEPS && lo <= hi; step++) {
            int mid = lo + (hi - lo) / 2;
            PackResult attempt = new MaxRectsPacker(usableWidth, mid, heuristic).pack(sorted, allowRotate);
            if (attempt.packedEverything()) {
                best = betterOf(best, attempt);
                hi = Math.min(mid, attempt.usedLength()) - 1;
            } else {
                lo = mid + 1;
            }
            if (lo > hi) {
                break;
            }
        }
        return best;
    }

    /** Tim nhi phan chieu dai nho nhat cho packer doi chung. */
    private PackResult minimizeLengthShelf(List<Piece> sorted, int usableWidth, int lowerBound, int upperBound,
                                           boolean allowRotate) {
        PackResult best = null;
        int lo = lowerBound;
        int hi = upperBound;

        for (int step = 0; step < BINARY_SEARCH_STEPS && lo <= hi; step++) {
            int mid = lo + (hi - lo) / 2;
            PackResult attempt = new ShelfPacker(usableWidth, mid).pack(sorted, allowRotate);
            if (attempt.packedEverything()) {
                best = betterOf(best, attempt);
                hi = Math.min(mid, attempt.usedLength()) - 1;
            } else {
                lo = mid + 1;
            }
            if (lo > hi) {
                break;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Cat thanh nhieu tam
    // ------------------------------------------------------------------

    /**
     * Do hinh vao tung tam co chieu dai toi da co dinh.
     *
     * <p>Moi vong: chay tat ca to hop voi chieu cao kho = {@code maxLength}, chon phuong
     * an dat duoc NHIEU hinh nhat (hoa thi lay chieu dai ngan hon). Sau khi biet chinh
     * xac tap hinh cua tam, xep lai rieng tap do de nen sat - nho vay tam cuoi cung
     * khong bi keo dai vo ich.
     */
    private List<PackResult> packMultipleSheets(List<Piece> pieces, int usableWidth, int maxLength,
                                                boolean allowRotate) {
        List<PackResult> sheets = new ArrayList<>();
        List<Piece> remaining = new ArrayList<>(pieces);

        while (!remaining.isEmpty()) {
            if (sheets.size() >= MAX_SHEETS) {
                throw new ApiException(ErrorCode.NESTING_FAILED,
                        "So tam vuot qua gioi han an toan (" + MAX_SHEETS + "). Hay tang chieu dai toi da moi file.");
            }

            PackResult best = null;
            for (Variant variant : buildVariants(remaining, usableWidth, allowRotate)) {
                for (SortOrder order : SortOrder.values()) {
                    List<Piece> sorted = sortPieces(variant.pieces(), order);
                    for (Heuristic heuristic : Heuristic.values()) {
                        PackResult attempt = new MaxRectsPacker(usableWidth, maxLength, heuristic)
                                .pack(sorted, variant.allowRotate());
                        best = betterFilled(best, attempt);
                    }
                    PackResult shelf = new ShelfPacker(usableWidth, maxLength).pack(sorted, variant.allowRotate());
                    best = betterFilled(best, shelf);
                }
            }

            if (best == null || best.placedCount() == 0) {
                throw new ApiException(ErrorCode.NESTING_FAILED,
                        "Khong dat duoc hinh nao vao tam moi. Hay kiem tra lai gioi han chieu dai va khoang cach.");
            }

            // Da biet tap hinh cua tam nay: xep lai de nen sat nhat co the.
            List<Piece> onSheet = best.placed().stream().map(PlacedPiece::piece).toList();
            PackResult compacted = packSingleSheet(onSheet, usableWidth, allowRotate);
            sheets.add(compacted.usedLength() <= best.usedLength() ? compacted : best);

            remaining = new ArrayList<>(best.unplaced());
        }
        return sheets;
    }

    // ------------------------------------------------------------------
    // Tien ich chon phuong an
    // ------------------------------------------------------------------

    /**
     * Mot phuong an chuan bi dau vao: danh sach hinh (co the da bi ep huong) kem che do
     * xoay ma packer duoc phep dung.
     */
    private record Variant(List<Piece> pieces, boolean allowRotate) {
    }

    /** So loai hinh toi da con du re de duyet het cac phuong an ep huong (2^n). */
    private static final int MAX_TYPES_FOR_ORIENTATION_SEARCH = 8;

    /**
     * Sinh cac phuong an chuan bi dau vao.
     *
     * <p>Ngoai phuong an "tu do" (packer tu quyet dinh xoay tung hinh), engine con duyet
     * cac phuong an EP HUONG: moi LOAI hinh bi chot mot huong duy nhat truoc khi xep.
     *
     * <p>Day la cai tien co suc nang nhat tren du lieu that cua xuong. Packer tham lam
     * quyet dinh xoay theo tung hinh mot nen de pha vo cau truc luoi: mot hinh xoay lech
     * lam hong ca hang. Khi chot huong cho ca loai, cac ban giong nhau tu xep thanh cot
     * deu tam tap, dai bien thua gom lai thanh mot dai lien tuc du rong de nhoi loai
     * hinh khac vao - dung cach tho lanh nghe xep tay.
     *
     * <p>So phuong an la 2^(so loai xoay duoc); vuot qua {@link #MAX_TYPES_FOR_ORIENTATION_SEARCH}
     * loai thi chi giu phuong an tu do de thoi gian chay khong bung no.
     */
    private static List<Variant> buildVariants(List<Piece> pieces, int usableWidth, boolean allowRotate) {
        List<Variant> variants = new ArrayList<>();
        variants.add(new Variant(pieces, allowRotate));
        if (allowRotate) {
            // Van giu lai phuong an khong xoay chut nao de lam moc so sanh.
            variants.add(new Variant(pieces, false));
        }
        if (!allowRotate) {
            return variants;
        }

        List<Integer> rotatableTypes = pieces.stream()
                .filter(Piece::allowRotate)
                .map(Piece::typeIndex)
                .distinct()
                .sorted()
                .toList();
        if (rotatableTypes.isEmpty() || rotatableTypes.size() > MAX_TYPES_FOR_ORIENTATION_SEARCH) {
            return variants;
        }

        int combinations = 1 << rotatableTypes.size();
        for (int mask = 0; mask < combinations; mask++) {
            List<Piece> variantPieces = new ArrayList<>(pieces.size());
            boolean feasible = true;
            for (Piece piece : pieces) {
                int bit = rotatableTypes.indexOf(piece.typeIndex());
                boolean flip = bit >= 0 && (mask & (1 << bit)) != 0;
                Piece prepared = flip ? piece.rotated90() : piece;
                if (prepared.w() > usableWidth) {
                    // Huong nay lam hinh rong hon kho: bo ca phuong an.
                    feasible = false;
                    break;
                }
                variantPieces.add(prepared);
            }
            if (feasible) {
                variants.add(new Variant(variantPieces, false));
            }
        }
        return variants;
    }

    /** Uu tien phuong an xep het hinh voi chieu dai nho nhat. */
    private static PackResult betterOf(PackResult current, PackResult candidate) {
        if (candidate == null || !candidate.packedEverything()) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        return candidate.usedLength() < current.usedLength() ? candidate : current;
    }

    /** Uu tien phuong an dat duoc nhieu hinh nhat; hoa thi lay chieu dai ngan hon. */
    private static PackResult betterFilled(PackResult current, PackResult candidate) {
        if (candidate == null || candidate.placedCount() == 0) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        if (candidate.placedCount() != current.placedCount()) {
            return candidate.placedCount() > current.placedCount() ? candidate : current;
        }
        return candidate.usedLength() < current.usedLength() ? candidate : current;
    }

    /** Sap xep tat dinh: tie-break cuoi cung luon la id nen khong phu thuoc thu tu dau vao. */
    private static List<Piece> sortPieces(List<Piece> pieces, SortOrder order) {
        Comparator<Piece> comparator = switch (order) {
            case AREA_DESC -> Comparator.comparingLong(Piece::area).reversed()
                    .thenComparing(Comparator.comparingInt(Piece::longSide).reversed());
            case LONG_SIDE_DESC -> Comparator.comparingInt(Piece::longSide).reversed()
                    .thenComparing(Comparator.comparingInt(Piece::shortSide).reversed());
            case SHORT_SIDE_DESC -> Comparator.comparingInt(Piece::shortSide).reversed()
                    .thenComparing(Comparator.comparingInt(Piece::longSide).reversed());
            case HEIGHT_DESC -> Comparator.comparingInt(Piece::h).reversed()
                    .thenComparing(Comparator.comparingInt(Piece::w).reversed());
            case WIDTH_DESC -> Comparator.comparingInt(Piece::w).reversed()
                    .thenComparing(Comparator.comparingInt(Piece::h).reversed());
            case PERIMETER_DESC -> Comparator.comparingInt((Piece p) -> p.w() + p.h()).reversed()
                    .thenComparing(Comparator.comparingLong(Piece::area).reversed());
        };
        return pieces.stream()
                .sorted(comparator.thenComparingInt(Piece::id))
                .toList();
    }

    /** Can duoi ly thuyet: tong dien tich chia chieu rong kha dung. */
    private static int areaLowerBound(List<Piece> pieces, int usableWidth) {
        long totalArea = 0;
        int tallestSingle = 0;
        for (Piece piece : pieces) {
            totalArea += piece.area();
            tallestSingle = Math.max(tallestSingle, piece.shortSide());
        }
        int byArea = (int) Math.ceil(totalArea / (double) usableWidth);
        return Math.max(1, Math.max(byArea, tallestSingle));
    }

    /** Can tren: mot lan xep khong rang buoc chieu dai, cong bien an toan. */
    private static int freeUpperBound(List<Piece> pieces, int usableWidth, boolean allowRotate) {
        long naive = 0;
        for (Piece piece : pieces) {
            naive += piece.longSide();
        }
        int ceiling = (int) Math.min(Integer.MAX_VALUE / 2L, naive + 1);
        PackResult shelf = new ShelfPacker(usableWidth, ceiling).pack(
                sortPieces(pieces, SortOrder.AREA_DESC), allowRotate);
        int bound = shelf.packedEverything() ? shelf.usedLength() : ceiling;
        return Math.max(1, bound);
    }

    // ------------------------------------------------------------------
    // Dung ket qua tra ra API
    // ------------------------------------------------------------------

    /** Chuyen ket qua noi bo (don vi 1/100 mm, da no gap) sang DTO milimet kich thuoc that. */
    private NestResult toResult(List<PackResult> packed, EngineInput input, int totalPieces,
                                int marginCmm, int gapCmm, int sheetWidthCmm) {
        List<Sheet> sheets = new ArrayList<>();
        double totalLengthMm = 0;
        int placedCount = 0;

        for (int i = 0; i < packed.size(); i++) {
            PackResult result = packed.get(i);
            // usedLength do bang canh tren cua o DA NO gap; hinh that ket thuc som hon
            // dung mot gap, nen tam duoc cat sat vao do.
            int sheetLengthCmm = Math.max(0, result.usedLength() - gapCmm) + 2 * marginCmm;
            double sheetLengthMm = Units.round2(Units.cmmToMm(sheetLengthCmm));
            double sheetWidthMm = Units.round2(Units.cmmToMm(sheetWidthCmm));

            List<Placement> placements = new ArrayList<>();
            double shapeAreaOnSheet = 0;
            // Thu tu on dinh theo (y, x) de preview va PDF ve giong nhau moi lan chay.
            List<PlacedPiece> ordered = result.placed().stream()
                    .sorted(Comparator.comparingInt(PlacedPiece::y)
                            .thenComparingInt(PlacedPiece::x)
                            .thenComparingInt(p -> p.piece().id()))
                    .toList();

            for (PlacedPiece p : ordered) {
                // O da no gap rong hon hinh that dung mot gap. Neo hinh that vao goc
                // trai-duoi cua o: moi hinh deu dich di mot luong NHU NHAU nen khoang
                // cach giua hai hinh bat ky van dung bang gap, dong thoi hinh sat bien
                // nam khit mep le thay vi lui vao nua gap.
                int realX = marginCmm + p.x();
                int realY = marginCmm + p.y();
                int realW = p.rotated() ? p.piece().realH() : p.piece().realW();
                int realH = p.rotated() ? p.piece().realW() : p.piece().realH();
                // Hinh co the da bi ep huong TRUOC khi xep, roi packer xoay them lan nua.
                // Co bao ra ngoai phai la tong hop cua ca hai lan.
                boolean rotated = p.piece().prerotated() ^ p.rotated();

                placements.add(new Placement(
                        p.piece().fileId(),
                        p.piece().label(),
                        p.piece().categoryIndex(),
                        Units.round2(Units.cmmToMm(realX)),
                        Units.round2(Units.cmmToMm(realY)),
                        Units.round2(Units.cmmToMm(realW)),
                        Units.round2(Units.cmmToMm(realH)),
                        rotated));
                shapeAreaOnSheet += Units.cmmToMm(realW) * Units.cmmToMm(realH);
            }

            double sheetArea = sheetWidthMm * sheetLengthMm;
            double fillRate = sheetArea > 0 ? shapeAreaOnSheet / sheetArea : 0;
            sheets.add(new Sheet(i, sheetWidthMm, sheetLengthMm, round4(fillRate), placements));

            totalLengthMm += sheetLengthMm;
            placedCount += placements.size();
        }

        if (placedCount != totalPieces) {
            throw new ApiException(ErrorCode.NESTING_FAILED,
                    "Sai lech so luong: dat duoc " + placedCount + " hinh trong khi yeu cau " + totalPieces + ".");
        }

        double totalShapeArea = 0;
        for (Sheet sheet : sheets) {
            for (Placement p : sheet.placements()) {
                totalShapeArea += p.wMm() * p.hMm();
            }
        }
        double sheetWidthMm = Units.round2(Units.cmmToMm(sheetWidthCmm));
        double usedArea = sheetWidthMm * totalLengthMm;
        double fillRate = usedArea > 0 ? totalShapeArea / usedArea : 0;

        NestStats stats = new NestStats(
                sheets.size(),
                Units.round2(totalLengthMm),
                Units.round2(totalShapeArea),
                Units.round2(usedArea),
                round4(fillRate),
                round4(savedVsIndividualPct(sheets, totalLengthMm, input)),
                placedCount);

        return new NestResult(sheets, stats);
    }

    /**
     * Phan tram tiet kiem so voi phuong an in roi.
     *
     * <p>Moc so sanh "in roi" la cach xuong dang lam truoc day: moi ban in chiem tron
     * chieu ngang kho, cac ban xep chong len nhau theo chieu dai, cach nhau dung gap.
     */
    private double savedVsIndividualPct(List<Sheet> sheets, double totalLengthMm, EngineInput input) {
        double individualLength = 2 * input.marginMm();
        for (Sheet sheet : sheets) {
            for (Placement p : sheet.placements()) {
                // Khi in roi, hinh khong duoc xoay de tiet kiem nen lay dung chieu cao goc.
                double height = p.rotated() ? p.wMm() : p.hMm();
                individualLength += height + input.gapMm();
            }
        }
        if (individualLength <= 0) {
            return 0;
        }
        return Math.max(0, (individualLength - totalLengthMm) / individualLength * 100d);
    }

    private static double round4(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    /** Gom cac item trung fileId de gan chi so mau on dinh cho preview. */
    public static Map<String, Integer> categoryIndexes(List<String> fileIds) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (String fileId : fileIds) {
            indexes.computeIfAbsent(fileId, k -> indexes.size());
        }
        return indexes;
    }
}
