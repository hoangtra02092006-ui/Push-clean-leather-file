package vn.printnest.nesting.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Xep hinh theo HINH DANG THAT, cho phep khung bao chong nhau.
 *
 * <p>Khac biet voi {@link MaxRectsPacker}: packer chu nhat coi moi hinh la mot khoi dac
 * nen hai hinh khong bao gio duoc de len nhau. O day moi hinh mang theo mat na silhouette,
 * nen goc trong cua hinh nay om duoc phan nho ra cua hinh kia - dung cach tho xep tay khi
 * cat theo vien.
 *
 * <h2>Cach lam</h2>
 * <ol>
 *   <li>Tam la mot luoi bit rong. Moi hinh da dat duoc in len luoi bang mat na DA NO
 *       them {@code gap}.</li>
 *   <li>Hinh moi duoc do bang mat na CHUA no. Hai ben khong dam bit nao tuc la hai hinh
 *       that cach nhau it nhat mot gap - khong can tinh khoang cach gi them.</li>
 *   <li>Voi moi goc xoay cho phep, quet tu trai sang phai; moi cot tim vi tri THAP NHAT
 *       dat duoc. Chon vi tri cho dinh hinh thap nhat (hoa thi lay trai nhat).</li>
 * </ol>
 *
 * <p>Do va cham lam bang phep AND tren tung tu 64 bit chu khong duyet tung o, nen mot lan
 * thu vi tri cua hinh 25x15 cm chi ton vai tram phep tinh.
 *
 * <p><b>Gioi han da biet:</b> hinh duoc tha tu tren xuong nhu chiu trong luc, nen chui
 * duoc vao hoc ho mieng len tren, nhung khong chui duoc vao hang kin hoac hoc chi mo
 * ngang. Day la danh doi de giu toc do chay duoc.
 */
public final class TrueShapeNester {

    /** Mot ban in kem mat na o tung goc xoay da chuan bi san. */
    public record ShapePiece(Piece piece, List<Oriented> orientations) {

        /**
         * Mat na o mot goc xoay cu the.
         *
         * @param angleDeg      goc xoay nguoc chieu kim dong ho
         * @param mask          mat na that, dung de DO va cham
         * @param inflated      mat na da no them gap, dung de IN len tam
         * @param bottomProfile hang thap nhat co net ve o tung cot; -1 neu cot trong
         */
        public record Oriented(double angleDeg, ShapeMask mask, ShapeMask inflated,
                               int[] bottomProfile) {

            public Oriented(double angleDeg, ShapeMask mask, ShapeMask inflated) {
                this(angleDeg, mask, inflated, mask.bottomProfile());
            }
        }

        /** Dien tich that (so o dac) o goc dau tien - dung de sap xep to truoc nho sau. */
        public int footprint() {
            return orientations.isEmpty() ? 0 : orientations.get(0).mask().occupiedCells();
        }
    }

    /** Ket qua dat mot hinh. */
    public record ShapePlacement(Piece piece, double angleDeg, int col, int row,
                                 int cols, int rows) {
    }

    private final int sheetCols;
    private final int maxRows;
    private final int wordsPerRow;
    private final long[] sheet;

    /** Hang cao nhat da co o dac, dung de biet tam dai bao nhieu. */
    private int usedRows;

    /**
     * Duong chan troi: hang ngay TREN o dac cao nhat cua moi cot.
     *
     * <p>Dat hinh sao cho moi cot deu nam tu day tro len thi chac chan khong dam vao gi.
     * Day la diem xuat phat de tha roi, giup bo qua hang nghin phep do vo ich o phan tam
     * da day ben duoi.
     */
    private final int[] heights;

    public TrueShapeNester(int sheetCols, int maxRows) {
        this.sheetCols = sheetCols;
        this.maxRows = maxRows;
        this.wordsPerRow = (sheetCols + 63) >>> 6;
        this.sheet = new long[wordsPerRow * maxRows];
        this.heights = new int[sheetCols];
    }

    public int usedRows() {
        return usedRows;
    }

    /**
     * Dat lan luot cac hinh theo thu tu truyen vao.
     *
     * @return danh sach da dat; hinh nao khong con cho se khong co mat trong ket qua
     */
    public List<ShapePlacement> pack(List<ShapePiece> pieces) {
        List<ShapePlacement> placed = new ArrayList<>(pieces.size());
        for (ShapePiece piece : pieces) {
            ShapePlacement spot = findSpot(piece);
            if (spot != null) {
                mark(spot, piece);
                placed.add(spot);
            }
        }
        return placed;
    }

    /** Tim vi tri tot nhat cho mot hinh: dinh thap nhat, hoa thi trai nhat. */
    private ShapePlacement findSpot(ShapePiece piece) {
        ShapePlacement best = null;
        int bestTop = Integer.MAX_VALUE;

        for (ShapePiece.Oriented oriented : piece.orientations()) {
            ShapeMask mask = oriented.mask();
            if (mask.cols() > sheetCols) {
                continue;
            }
            int lastCol = sheetCols - mask.cols();

            for (int col = 0; col <= lastCol; col++) {
                int row = lowestFreeRow(mask, oriented.bottomProfile(), col);
                if (row < 0) {
                    continue;
                }
                int top = row + mask.rows();
                if (top < bestTop) {
                    bestTop = top;
                    best = new ShapePlacement(piece.piece(), oriented.angleDeg(), col, row,
                            mask.cols(), mask.rows());
                }
            }
        }
        return best;
    }

    /**
     * Hang thap nhat ma hinh dat duoc tai cot nay - mo phong tha roi tu tren xuong.
     *
     * <p>Quet tu hang 0 len tren se dung nhung cham khong chiu noi: tam dai hang nghin
     * hang, nhan voi so cot va so goc xoay la hang ty phep do. Thay vao do:
     * <ol>
     *   <li>Dung <b>duong chan troi</b> (hang cao nhat da co net ve o moi cot) de nhay
     *       thang toi mot vi tri CHAC CHAN trong - khong can do mot lan nao.</li>
     *   <li>Tu do <b>tha roi</b>: ha dan xuong chung nao con trong. Nho buoc nay hinh moi
     *       chui duoc vao cac hoc lom ma duong chan troi da vuot qua.</li>
     * </ol>
     *
     * @param bottomProfile hang thap nhat co net ve o tung cot cua mat na
     * @return hang thap nhat, hoac -1 neu cot nay khong dat duoc
     */
    private int lowestFreeRow(ShapeMask mask, int[] bottomProfile, int col) {
        // Vi tri chac chan trong: moi cot cua hinh deu nam tren duong chan troi.
        int row = 0;
        for (int c = 0; c < bottomProfile.length; c++) {
            if (bottomProfile[c] < 0) {
                continue;
            }
            row = Math.max(row, heights[col + c] - bottomProfile[c]);
        }
        if (row + mask.rows() > maxRows) {
            return -1;
        }

        // Tha roi: ha xuong chung nao van con trong.
        while (row > 0 && !collides(mask, col, row - 1)) {
            row--;
        }
        return row;
    }

    /**
     * Hinh dat tai (col,row) co dam vao phan da in tren tam khong.
     *
     * <p>Diem cot loi cua ca lop: dich bit cua tung hang mat na sang {@code col} roi AND
     * voi hang tuong ung cua tam. Mot hinh 25x15 cm o luoi 1 mm chi ton chung 300 phep
     * tinh cho mot lan thu - nho vay moi quet duoc hang chuc nghin vi tri.
     */
    private boolean collides(ShapeMask mask, int col, int row) {
        long[] maskBits = mask.bits();
        int maskWords = mask.wordsPerRow();
        int wordShift = col >>> 6;
        int bitShift = col & 63;

        for (int r = 0; r < mask.rows(); r++) {
            int maskBase = r * maskWords;
            int sheetBase = (row + r) * wordsPerRow;

            for (int w = 0; w < maskWords; w++) {
                long value = maskBits[maskBase + w];
                if (value == 0) {
                    continue;
                }
                int target = sheetBase + wordShift + w;
                if ((sheet[target] & (value << bitShift)) != 0) {
                    return true;
                }
                if (bitShift != 0) {
                    long carry = value >>> (64 - bitShift);
                    if (carry != 0 && (sheet[target + 1] & carry) != 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** In hinh da dat len tam, dung ban DA NO de chua san khoang ho cho hinh sau. */
    private void mark(ShapePlacement spot, ShapePiece piece) {
        ShapeMask inflated = null;
        for (ShapePiece.Oriented oriented : piece.orientations()) {
            if (oriented.angleDeg() == spot.angleDeg()) {
                inflated = oriented.inflated();
                break;
            }
        }
        if (inflated == null) {
            return;
        }

        // Ban da no lon hon ban goc deu moi phia; lui goc lai cho khop tam hinh.
        int pad = (inflated.cols() - spot.cols()) / 2;
        int baseCol = spot.col() - pad;
        int baseRow = spot.row() - pad;

        for (int r = 0; r < inflated.rows(); r++) {
            int sheetRow = baseRow + r;
            if (sheetRow < 0 || sheetRow >= maxRows) {
                continue;
            }
            for (int c = 0; c < inflated.cols(); c++) {
                if (!inflated.get(c, r)) {
                    continue;
                }
                int sheetCol = baseCol + c;
                if (sheetCol < 0 || sheetCol >= sheetCols) {
                    continue;
                }
                sheet[sheetRow * wordsPerRow + (sheetCol >>> 6)] |= 1L << (sheetCol & 63);
                if (sheetRow + 1 > heights[sheetCol]) {
                    heights[sheetCol] = sheetRow + 1;
                }
            }
        }
        usedRows = Math.max(usedRows, spot.row() + spot.rows());
    }
}
