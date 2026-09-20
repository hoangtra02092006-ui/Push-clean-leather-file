package vn.printnest.nesting.engine;

/**
 * Hinh dang THAT SU cua mot ban in, duoi dang luoi o vuong nhi phan.
 *
 * <p>Khac voi {@link Piece} von chi biet khung bao chu nhat, mat na nay biet tung o nho
 * cho nao co net ve cho nao trong. Nho vay hai hinh co the long vao nhau: goc trong cua
 * hinh nay om lay phan nho ra cua hinh kia, mien la khong o nao dung nhau.
 *
 * <p><b>He toa do:</b> o (0,0) la goc TRAI-DUOI, cot tang sang phai, hang tang len tren -
 * cung chieu voi he toa do PDF nen khong phai lat truc o bat ky dau.
 *
 * <p><b>Luu bang bit:</b> moi hang la mot day bit dong goi trong mang {@code long}. Do va
 * cham giua hai hinh vi the chi la vai phep AND tren 64 o cung luc, thay vi duyet tung o.
 * Day la thu quyet dinh chuyen "xep long theo hinh that" tu bat kha thi thanh chay duoc.
 */
public final class ShapeMask {

    private final int cols;
    private final int rows;
    private final double cellMm;
    private final int wordsPerRow;
    private final long[] bits;

    private ShapeMask(int cols, int rows, double cellMm) {
        this.cols = cols;
        this.rows = rows;
        this.cellMm = cellMm;
        this.wordsPerRow = (cols + 63) >>> 6;
        this.bits = new long[wordsPerRow * rows];
    }

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    public double cellMm() {
        return cellMm;
    }

    public int wordsPerRow() {
        return wordsPerRow;
    }

    /** Mang bit tho, dung truc tiep trong vong lap do va cham. */
    public long[] bits() {
        return bits;
    }

    public boolean get(int col, int row) {
        if (col < 0 || row < 0 || col >= cols || row >= rows) {
            return false;
        }
        return (bits[row * wordsPerRow + (col >>> 6)] & (1L << (col & 63))) != 0;
    }

    public void set(int col, int row) {
        if (col < 0 || row < 0 || col >= cols || row >= rows) {
            return;
        }
        bits[row * wordsPerRow + (col >>> 6)] |= 1L << (col & 63);
    }

    public int occupiedCells() {
        int count = 0;
        for (long word : bits) {
            count += Long.bitCount(word);
        }
        return count;
    }

    /** Mat na dac hoan toan - dung cho anh, hoac file khong doc duoc hinh dang. */
    public static ShapeMask solid(double widthMm, double heightMm, double cellMm) {
        int cols = Math.max(1, (int) Math.ceil(widthMm / cellMm));
        int rows = Math.max(1, (int) Math.ceil(heightMm / cellMm));
        ShapeMask mask = new ShapeMask(cols, rows, cellMm);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                mask.set(col, row);
            }
        }
        return mask;
    }

    /**
     * Dung mat na tu danh sach o dac cho san.
     *
     * @param occupied ham tra loi o (col,row) co dac khong, theo luoi goc
     */
    public static ShapeMask of(int cols, int rows, double cellMm, CellTest occupied) {
        ShapeMask mask = new ShapeMask(cols, rows, cellMm);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (occupied.isOccupied(col, row)) {
                    mask.set(col, row);
                }
            }
        }
        return mask;
    }

    /** Ham kiem tra mot o co dac khong. */
    @FunctionalInterface
    public interface CellTest {
        boolean isOccupied(int col, int row);
    }

    /** Luoi trong hoan toan, cung kich thuoc. */
    public static ShapeMask empty(int cols, int rows, double cellMm) {
        return new ShapeMask(cols, rows, cellMm);
    }

    /**
     * Xoay mat na mot goc bat ky.
     *
     * <p>Kich thuoc luoi moi la hop bao cua HINH CHU NHAT goc sau khi xoay - khong phai
     * hop bao cua rieng phan co net ve. Lam vay de khop chinh xac voi cach
     * {@code PdfComposer} ve ra file: no cung xoay ca khung roi can goc trai-duoi cua
     * khung da xoay.
     *
     * <p>Lay mau nguoc (tu o dich tim ve o nguon) roi <b>no them mot o</b>. No them la co
     * y: phep lay mau co the bo sot o nam vat ngang bien, va bo sot o dac nghia la hai
     * hinh cham nhau - hong ban in. Lay du mot o thi chi mat chut cho.
     *
     * @param degrees goc xoay nguoc chieu kim dong ho
     */
    public ShapeMask rotated(double degrees) {
        double normalized = ((degrees % 360) + 360) % 360;
        // Goc boi so cua 90 do la phep hoan vi chi so thuan tuy: khong lay mau, khong sai
        // so, nen KHONG duoc no them o nao. No thua o day lam hai hinh khit nhau bi coi la
        // cham nhau, va dung cac goc khuyet - thu quan trong nhat cua xep long - mat tac dung.
        if (normalized % 90 == 0) {
            return rotatedRightAngle((int) normalized);
        }

        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        // Hop bao cua khung chu nhat goc sau khi xoay.
        double width = cols;
        double height = rows;
        double[] xs = {0, width * cos, -height * sin, width * cos - height * sin};
        double[] ys = {0, width * sin, height * cos, width * sin + height * cos};
        double minX = Math.min(Math.min(xs[0], xs[1]), Math.min(xs[2], xs[3]));
        double maxX = Math.max(Math.max(xs[0], xs[1]), Math.max(xs[2], xs[3]));
        double minY = Math.min(Math.min(ys[0], ys[1]), Math.min(ys[2], ys[3]));
        double maxY = Math.max(Math.max(ys[0], ys[1]), Math.max(ys[2], ys[3]));

        int newCols = Math.max(1, (int) Math.ceil(maxX - minX));
        int newRows = Math.max(1, (int) Math.ceil(maxY - minY));
        ShapeMask turned = new ShapeMask(newCols, newRows, cellMm);

        for (int row = 0; row < newRows; row++) {
            for (int col = 0; col < newCols; col++) {
                // Tam o dich, doi ve he toa do da xoay roi quay nguoc lai he goc.
                double tx = col + 0.5 + minX;
                double ty = row + 0.5 + minY;
                double sx = tx * cos + ty * sin;
                double sy = -tx * sin + ty * cos;
                int sourceCol = (int) Math.floor(sx);
                int sourceRow = (int) Math.floor(sy);
                if (get(sourceCol, sourceRow)) {
                    turned.set(col, row);
                }
            }
        }
        return turned.dilated(1);
    }

    /**
     * Xoay chinh xac mot goc vuong (0, 90, 180, 270 do).
     *
     * <p>Chi la phep hoan vi chi so o, khong lay mau nen khong mat mat gi. Nho vay hai
     * hinh khit nhau tung o van khit sau khi xoay.
     */
    private ShapeMask rotatedRightAngle(int degrees) {
        if (degrees == 0) {
            return this;
        }
        boolean swap = degrees == 90 || degrees == 270;
        ShapeMask turned = new ShapeMask(swap ? rows : cols, swap ? cols : rows, cellMm);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (!get(col, row)) {
                    continue;
                }
                switch (degrees) {
                    // Nguoc chieu kim dong ho: (c,r) -> (rows-1-r, c)
                    case 90 -> turned.set(rows - 1 - row, col);
                    case 180 -> turned.set(cols - 1 - col, rows - 1 - row);
                    default -> turned.set(row, cols - 1 - col);
                }
            }
        }
        return turned;
    }

    /**
     * No vung dac ra {@code radius} o ve moi phia, giu nguyen kich thuoc luoi.
     *
     * <p>Dung de chua khoang ho: khi danh dau mot hinh da dat len tam, ta danh dau ban DA
     * NO; hinh dat sau duoc do bang ban CHUA no. Hai ban khong dam nhau tuc la hai hinh
     * that cach nhau it nhat bang ban kinh no.
     */
    public ShapeMask dilated(int radius) {
        if (radius <= 0) {
            return this;
        }
        ShapeMask grown = new ShapeMask(cols, rows, cellMm);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (!get(col, row)) {
                    continue;
                }
                int rowFrom = Math.max(0, row - radius);
                int rowTo = Math.min(rows - 1, row + radius);
                int colFrom = Math.max(0, col - radius);
                int colTo = Math.min(cols - 1, col + radius);
                for (int r = rowFrom; r <= rowTo; r++) {
                    for (int c = colFrom; c <= colTo; c++) {
                        grown.set(c, r);
                    }
                }
            }
        }
        return grown;
    }

    /**
     * No ra nhung CHO PHEP luoi lon them, de phan no khong bi cat cut o bien.
     *
     * @param radius so o no ra moi phia
     */
    public ShapeMask dilatedExpanding(int radius) {
        if (radius <= 0) {
            return this;
        }
        ShapeMask grown = new ShapeMask(cols + 2 * radius, rows + 2 * radius, cellMm);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (!get(col, row)) {
                    continue;
                }
                for (int r = -radius; r <= radius; r++) {
                    for (int c = -radius; c <= radius; c++) {
                        grown.set(col + radius + c, row + radius + r);
                    }
                }
            }
        }
        return grown;
    }

    /** Hang thap nhat co o dac o moi cot; -1 neu cot do trong hoan toan. */
    public int[] bottomProfile() {
        int[] profile = new int[cols];
        for (int col = 0; col < cols; col++) {
            profile[col] = -1;
            for (int row = 0; row < rows; row++) {
                if (get(col, row)) {
                    profile[col] = row;
                    break;
                }
            }
        }
        return profile;
    }
}
