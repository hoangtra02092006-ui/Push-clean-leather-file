package vn.printnest.file;

import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Ban do "cho nao co net ve" cua mot hinh, chia thanh luoi o vuong.
 *
 * <p>Dung de tra loi cau hoi: trong khung bao cua hinh nay, cho nao con trong du rong de
 * nhet mot hinh khac vao? Chi biet hop bao thi khong tra loi duoc - mot hinh chu L va mot
 * hinh vuong dac co hop bao y het nhau.
 *
 * <p>Toa do o (0,0) la goc TRAI-DUOI cua khung bao hinh, tang dan sang phai va len tren -
 * cung chieu voi he toa do PDF, de khong phai lat truc o bat ky dau.
 *
 * <p>Bit duoc luu theo hang trong mot mang {@code long}: mot phep AND tren 64 o cung luc,
 * nho vay viec do chong lan nhanh hon han so voi duyet tung o.
 *
 * @param cols   so o theo chieu ngang
 * @param rows   so o theo chieu doc
 * @param cellMm canh mot o, tinh bang milimet
 * @param bits   bit danh dau o co net ve, row-major
 */
public record OccupancyMask(int cols, int rows, double cellMm, long[] bits) {

    /** Canh o mac dinh. Nho hon thi chinh xac hon nhung ton bo nho theo binh phuong. */
    public static final double DEFAULT_CELL_MM = 0.5;

    /** Chan tren so o, de mot file kho A0 khong lam phinh bo nho. */
    private static final int MAX_CELLS = 4_000_000;

    public boolean isOccupied(int col, int row) {
        if (col < 0 || row < 0 || col >= cols || row >= rows) {
            return true;
        }
        int index = row * cols + col;
        return (bits[index >>> 6] & (1L << (index & 63))) != 0;
    }

    private void markOccupied(int col, int row) {
        int index = row * cols + col;
        bits[index >>> 6] |= 1L << (index & 63);
    }

    /**
     * Dung ban do tu danh sach hop bao cua tung lenh ve.
     *
     * <p>Moi o CHAM vao bat ky hinh nao deu bi danh dau la dac. Lam tron ra ngoai nhu vay
     * la co y: coi nham o trong thanh dac thi chi mat co hoi nhoi them hinh, con coi nham
     * o dac thanh trong thi hai ban in de len nhau - hong han ca to.
     *
     * @param shapes     hop bao tung lenh ve, don vi point, he toa do trang
     * @param contentBox khung bao net ve, don vi point, he toa do trang
     * @param cellMm     canh mot o
     * @return ban do chiem cho, hoac null neu khung bao khong hop le
     */
    public static OccupancyMask build(List<Rectangle2D> shapes, ContentBox contentBox, double cellMm) {
        double widthMm = pointsToMm(contentBox.widthPt());
        double heightMm = pointsToMm(contentBox.heightPt());
        if (widthMm <= 0 || heightMm <= 0) {
            return null;
        }

        double cell = cellMm;
        int cols = (int) Math.ceil(widthMm / cell);
        int rows = (int) Math.ceil(heightMm / cell);
        // Hinh qua to thi noi rong o ra cho vua bo nho, doi lai do chinh xac thap hon.
        while ((long) cols * rows > MAX_CELLS) {
            cell *= 2;
            cols = (int) Math.ceil(widthMm / cell);
            rows = (int) Math.ceil(heightMm / cell);
        }
        if (cols <= 0 || rows <= 0) {
            return null;
        }

        OccupancyMask mask = new OccupancyMask(cols, rows, cell,
                new long[(cols * rows + 63) >>> 6]);

        for (Rectangle2D shape : shapes) {
            // Doi ve he toa do cua khung bao, roi sang chi so o.
            double x0 = pointsToMm(shape.getMinX() - contentBox.xPt());
            double y0 = pointsToMm(shape.getMinY() - contentBox.yPt());
            double x1 = pointsToMm(shape.getMaxX() - contentBox.xPt());
            double y1 = pointsToMm(shape.getMaxY() - contentBox.yPt());

            int colFrom = Math.max(0, (int) Math.floor(x0 / cell));
            int colTo = Math.min(cols - 1, (int) Math.ceil(x1 / cell));
            int rowFrom = Math.max(0, (int) Math.floor(y0 / cell));
            int rowTo = Math.min(rows - 1, (int) Math.ceil(y1 / cell));

            for (int row = rowFrom; row <= rowTo; row++) {
                for (int col = colFrom; col <= colTo; col++) {
                    mask.markOccupied(col, row);
                }
            }
        }
        return mask;
    }

    private static double pointsToMm(double points) {
        return points * 25.4d / 72d;
    }

    /** So o dac, dung de biet hinh dac toi muc nao. */
    public int occupiedCells() {
        int count = 0;
        for (long word : bits) {
            count += Long.bitCount(word);
        }
        return count;
    }

    /** Ty le phan khung bao thuc su co net ve, 0..1. */
    public double density() {
        int total = cols * rows;
        return total == 0 ? 1 : occupiedCells() / (double) total;
    }
}
