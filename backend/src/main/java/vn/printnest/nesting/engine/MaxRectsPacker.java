package vn.printnest.nesting.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Thuat toan MaxRects cho bai toan xep hinh chu nhat vao kho rong co dinh.
 *
 * <p>Nguyen ly: duy tri danh sach cac hinh chu nhat trong CUC DAI (maximal free
 * rectangles) - moi o trong khong the nong ra them ma van rong. Khi dat mot hinh:
 * <ol>
 *   <li>Chon o trong co diem cham tot nhat theo {@link Heuristic} (thu ca huong xoay
 *       neu hinh cho phep).</li>
 *   <li>Cat moi o trong bi hinh vua dat de len thanh toi da 4 o con.</li>
 *   <li>Loai bo nhung o trong nam tron trong o trong khac (prune).</li>
 * </ol>
 *
 * <p>Toan bo tinh toan bang so nguyen 1/100 mm. Thuat toan hoan toan tat dinh: moi so
 * sanh diem deu co tie-break theo (y, x, huong xoay) nen cung dau vao luon cho cung
 * dau ra.
 *
 * <p>Lop nay KHONG tu sap xep dau vao - thu tu dat do {@link NestingEngine} quyet dinh.
 */
public final class MaxRectsPacker {

    /** Hinh chu nhat trong. Dung lop co the sua doi vi danh sach bi cat lien tuc. */
    private static final class FreeRect {
        int x;
        int y;
        int w;
        int h;

        FreeRect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        int right() {
            return x + w;
        }

        int top() {
            return y + h;
        }

        boolean contains(FreeRect o) {
            return o.x >= x && o.y >= y && o.right() <= right() && o.top() <= top();
        }
    }

    /** Vi tri ung vien kem diem cham. */
    private record Candidate(int x, int y, int w, int h, boolean rotated,
                             long primary, long secondary) {
    }

    private final int binWidth;
    private final int binHeight;
    private final Heuristic heuristic;
    private final List<FreeRect> freeRects = new ArrayList<>();

    /**
     * @param binWidth  chieu rong kha dung (da tru le), don vi 1/100 mm
     * @param binHeight chieu dai toi da cho phep, don vi 1/100 mm
     * @param heuristic ham cham diem vi tri
     */
    public MaxRectsPacker(int binWidth, int binHeight, Heuristic heuristic) {
        this.binWidth = binWidth;
        this.binHeight = binHeight;
        this.heuristic = heuristic;
        this.freeRects.add(new FreeRect(0, 0, binWidth, binHeight));
    }

    /**
     * Xep lan luot danh sach hinh theo dung thu tu truyen vao.
     *
     * <p>Hinh nao khong nhet duoc se duoc gom lai chu KHONG lam dung vong lap - nho vay
     * mot hinh to that bai khong chan cac hinh nho phia sau.
     *
     * @param pieces      danh sach can xep, theo thu tu uu tien
     * @param allowRotate cho phep xoay o muc toan cuc; tung hinh van co co rieng
     * @return ket qua xep tren tam nay
     */
    public PackResult pack(List<Piece> pieces, boolean allowRotate) {
        List<PlacedPiece> placed = new ArrayList<>();
        List<Piece> unplaced = new ArrayList<>();

        for (Piece piece : pieces) {
            PlacedPiece result = insert(piece, allowRotate);
            if (result == null) {
                unplaced.add(piece);
            } else {
                placed.add(result);
            }
        }

        // Nhoi hinh nho: thu lai cac hinh that bai theo thu tu dien tich tang dan.
        // Sau lan xep chinh, cac dai bien thua thuong chi con vua hinh nho nhat; day
        // chinh la cho tao ra chenh lech lon so voi xep tay.
        if (!unplaced.isEmpty()) {
            unplaced = backfill(unplaced, placed, allowRotate);
        }

        int usedLength = 0;
        for (PlacedPiece p : placed) {
            usedLength = Math.max(usedLength, p.top());
        }
        return new PackResult(placed, unplaced, usedLength);
    }

    /** Lap lai viec nhoi cac hinh con lai (nho truoc) cho toi khi khong con tien trien. */
    private List<Piece> backfill(List<Piece> remaining, List<PlacedPiece> placed, boolean allowRotate) {
        List<Piece> pending = new ArrayList<>(remaining);
        pending.sort((a, b) -> {
            int cmp = Long.compare(a.area(), b.area());
            return cmp != 0 ? cmp : Integer.compare(a.id(), b.id());
        });

        boolean progressed = true;
        while (progressed && !pending.isEmpty()) {
            progressed = false;
            List<Piece> stillPending = new ArrayList<>();
            for (Piece piece : pending) {
                PlacedPiece result = insert(piece, allowRotate);
                if (result == null) {
                    stillPending.add(piece);
                } else {
                    placed.add(result);
                    progressed = true;
                }
            }
            pending = stillPending;
        }
        return pending;
    }

    /** Dat mot hinh vao vi tri tot nhat; tra ve null neu khong con cho. */
    private PlacedPiece insert(Piece piece, boolean allowRotate) {
        boolean canRotate = allowRotate && piece.allowRotate();
        Candidate best = null;

        for (FreeRect free : freeRects) {
            Candidate c = score(free, piece.w(), piece.h(), false);
            if (better(c, best)) {
                best = c;
            }
            if (canRotate && piece.w() != piece.h()) {
                Candidate r = score(free, piece.h(), piece.w(), true);
                if (better(r, best)) {
                    best = r;
                }
            }
        }

        if (best == null) {
            return null;
        }

        PlacedPiece placed = new PlacedPiece(piece, best.x(), best.y(), best.w(), best.h(), best.rotated());
        applyPlacement(placed);
        return placed;
    }

    /** Cham diem viec dat hinh {@code w x h} vao goc trai-duoi cua o trong. */
    private Candidate score(FreeRect free, int w, int h, boolean rotated) {
        if (w > free.w || h > free.h) {
            return null;
        }
        int leftoverH = free.w - w;
        int leftoverV = free.h - h;
        long primary;
        long secondary;

        switch (heuristic) {
            case BSSF -> {
                primary = Math.min(leftoverH, leftoverV);
                secondary = Math.max(leftoverH, leftoverV);
            }
            case BLSF -> {
                primary = Math.max(leftoverH, leftoverV);
                secondary = Math.min(leftoverH, leftoverV);
            }
            case BAF -> {
                primary = (long) free.w * free.h - (long) w * h;
                secondary = Math.min(leftoverH, leftoverV);
            }
            case BL -> {
                primary = free.y + h;
                secondary = free.x;
            }
            default -> throw new IllegalStateException("Heuristic chua duoc ho tro: " + heuristic);
        }
        return new Candidate(free.x, free.y, w, h, rotated, primary, secondary);
    }

    /**
     * So sanh hai ung vien. Tie-break theo (y, x, huong xoay) de ket qua tat dinh va
     * nghieng ve phia duoi - dieu can thiet khi muc tieu la toi thieu chieu dai.
     */
    private static boolean better(Candidate c, Candidate best) {
        if (c == null) {
            return false;
        }
        if (best == null) {
            return true;
        }
        if (c.primary() != best.primary()) {
            return c.primary() < best.primary();
        }
        if (c.secondary() != best.secondary()) {
            return c.secondary() < best.secondary();
        }
        if (c.y() != best.y()) {
            return c.y() < best.y();
        }
        if (c.x() != best.x()) {
            return c.x() < best.x();
        }
        return !c.rotated() && best.rotated();
    }

    /** Cat cac o trong bi hinh vua dat de len, roi don danh sach. */
    private void applyPlacement(PlacedPiece placed) {
        List<FreeRect> generated = new ArrayList<>();
        for (int i = freeRects.size() - 1; i >= 0; i--) {
            FreeRect free = freeRects.get(i);
            if (splitFreeRect(free, placed, generated)) {
                freeRects.remove(i);
            }
        }
        addCavities(placed, generated);
        mergeGenerated(generated);
    }

    /**
     * Tra lai cho thuat toan cac o TRONG nam ben trong khung bao cua hinh vua dat.
     *
     * <p>Khung bao cua mot hinh khong phai luc nao cung dac. Mot hinh chu L co khung bao
     * vuong nhung bo trong han mot goc; truoc day ca goc do bi coi la da dung, khong ai
     * duoc dat vao. Gio moi o trong duoc dua nguoc vao danh sach o kha dung, nen mot hinh
     * nho vua van co the chui vao - dung nhu tho xep tay van lam.
     *
     * <p>An toan la do khau trich xuat bao dam: moi diem cua o trong da cach net ve cua
     * hinh chu it nhat mot gap. Hinh nao duoc packer dat LOT trong o do thi tu dong du
     * khoang ho, khong can kiem tra gi them o day.
     */
    private void addCavities(PlacedPiece placed, List<FreeRect> generated) {
        List<Cavity> cavities = placed.piece().cavities();
        if (cavities.isEmpty()) {
            return;
        }
        for (Cavity cavity : cavities) {
            // Hinh co the da bi packer xoay 90 do; hoc lom phai xoay theo y het.
            Cavity oriented = placed.rotated()
                    ? cavity.rotated90(placed.piece().realH())
                    : cavity;
            generated.add(new FreeRect(
                    placed.x() + oriented.x(),
                    placed.y() + oriented.y(),
                    oriented.w(),
                    oriented.h()));
        }
    }

    /**
     * Cat mot o trong theo hinh vua dat.
     *
     * @return true neu o trong bi giao va da bi thay the boi cac manh trong {@code out}
     */
    private boolean splitFreeRect(FreeRect free, PlacedPiece used, List<FreeRect> out) {
        if (used.x() >= free.right() || used.right() <= free.x
                || used.y() >= free.top() || used.top() <= free.y) {
            return false;
        }

        // Manh phia duoi hinh vua dat.
        if (used.y() > free.y && used.y() < free.top()) {
            out.add(new FreeRect(free.x, free.y, free.w, used.y() - free.y));
        }
        // Manh phia tren.
        if (used.top() < free.top() && used.top() > free.y) {
            out.add(new FreeRect(free.x, used.top(), free.w, free.top() - used.top()));
        }
        // Manh ben trai.
        if (used.x() > free.x && used.x() < free.right()) {
            out.add(new FreeRect(free.x, free.y, used.x() - free.x, free.h));
        }
        // Manh ben phai.
        if (used.right() < free.right() && used.right() > free.x) {
            out.add(new FreeRect(used.right(), free.y, free.right() - used.right(), free.h));
        }
        return true;
    }

    /**
     * Gop cac manh moi sinh vao danh sach, van giu bat bien "moi o trong deu cuc dai".
     *
     * <p>Danh sach cu da thoa bat bien va cac phan tu con lai khong he thay doi, nen chi
     * can doi chieu: manh moi voi nhau, roi manh moi voi manh cu. Cach nay thay cho mot
     * lan quet O(n^2) tren toan bo danh sach sau moi lan dat hinh - khac biet quyet dinh
     * khi engine chay hang tram phuong an lien tiep.
     */
    private void mergeGenerated(List<FreeRect> generated) {
        // Manh moi voi nhau.
        for (int i = 0; i < generated.size(); i++) {
            for (int j = i + 1; j < generated.size(); j++) {
                if (generated.get(j).contains(generated.get(i))) {
                    generated.remove(i);
                    i--;
                    break;
                }
                if (generated.get(i).contains(generated.get(j))) {
                    generated.remove(j);
                    j--;
                }
            }
        }

        // Manh moi voi manh cu.
        for (int i = 0; i < generated.size(); i++) {
            FreeRect fresh = generated.get(i);
            boolean redundant = false;
            for (int j = freeRects.size() - 1; j >= 0; j--) {
                FreeRect old = freeRects.get(j);
                if (old.contains(fresh)) {
                    redundant = true;
                    break;
                }
                if (fresh.contains(old)) {
                    freeRects.remove(j);
                }
            }
            if (redundant) {
                generated.remove(i);
                i--;
            }
        }

        freeRects.addAll(generated);
    }

    public int binWidth() {
        return binWidth;
    }

    public int binHeight() {
        return binHeight;
    }
}
