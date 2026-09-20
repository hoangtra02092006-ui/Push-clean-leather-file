package vn.printnest.file;

import vn.printnest.nesting.engine.EngineItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Tim cac O TRONG lon ben trong khung bao cua mot hinh.
 *
 * <p>Ket qua dung de tra lai cho thuat toan dan khuon: o trong nao du rong thi mot hinh
 * nho khac duoc phep chui vao, thay vi de phi ca goc nhu truoc.
 *
 * <p><b>Bao dam an toan.</b> Truoc khi tim, moi o dac duoc <em>no ra</em> mot khoang bang
 * {@code gap}. Nho vay o trong tim duoc da cach net ve that it nhat mot gap, va hinh nao
 * nam lot trong do thi tu dong du khoang ho - khong can kiem tra gi them o tang duoi.
 *
 * <p>Thuat toan: lap lai K lan viec "tim hinh chu nhat trong lon nhat" (bai toan kinh dien
 * hinh chu nhat lon nhat trong bieu do cot, chay theo tung hang), moi lan tim duoc thi
 * danh dau vung do la da dung roi tim tiep. Khong can tim DU moi o trong - chi vai o lon
 * nhat la da lay gan het phan loi.
 */
public final class CavityFinder {

    /** So o trong lay toi da cho moi hinh. */
    private static final int MAX_CAVITIES = 4;

    /** O trong nho hon nguong nay thi bo qua - khong hinh nao chui vua. */
    private static final double MIN_SIDE_MM = 8;

    private CavityFinder() {
    }

    /**
     * Tim cac o trong dung duoc.
     *
     * @param mask  ban do chiem cho cua hinh
     * @param gapMm khoang ho toi thieu phai chua quanh net ve
     * @return danh sach o trong, don vi milimet, toa do theo goc trai-duoi khung bao
     */
    public static List<EngineItem.CavityMm> find(OccupancyMask mask, double gapMm) {
        if (mask == null) {
            return List.of();
        }

        boolean[] blocked = inflate(mask, gapMm);
        int cols = mask.cols();
        int rows = mask.rows();
        double cell = mask.cellMm();

        List<EngineItem.CavityMm> cavities = new ArrayList<>();
        for (int round = 0; round < MAX_CAVITIES; round++) {
            int[] best = largestEmptyRectangle(blocked, cols, rows);
            if (best == null) {
                break;
            }
            int col = best[0];
            int row = best[1];
            int width = best[2];
            int height = best[3];

            double widthMm = width * cell;
            double heightMm = height * cell;
            if (widthMm < MIN_SIDE_MM || heightMm < MIN_SIDE_MM) {
                break;
            }

            cavities.add(new EngineItem.CavityMm(col * cell, row * cell, widthMm, heightMm));

            // Danh dau vung vua lay de vong sau tim o trong KHAC, khong lay trung.
            for (int r = row; r < row + height; r++) {
                for (int c = col; c < col + width; c++) {
                    blocked[r * cols + c] = true;
                }
            }
        }
        return List.copyOf(cavities);
    }

    /**
     * No vung dac ra mot khoang gap ve moi phia.
     *
     * <p>Day chinh la buoc bao dam khoang ho: sau khi no, moi o con lai deu cach net ve
     * that it nhat mot gap.
     */
    private static boolean[] inflate(OccupancyMask mask, double gapMm) {
        int cols = mask.cols();
        int rows = mask.rows();
        // Lam tron LEN: thà no du con hon no thieu.
        int radius = (int) Math.ceil(gapMm / mask.cellMm());

        boolean[] blocked = new boolean[cols * rows];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (!mask.isOccupied(col, row)) {
                    continue;
                }
                int rowFrom = Math.max(0, row - radius);
                int rowTo = Math.min(rows - 1, row + radius);
                int colFrom = Math.max(0, col - radius);
                int colTo = Math.min(cols - 1, col + radius);
                for (int r = rowFrom; r <= rowTo; r++) {
                    for (int c = colFrom; c <= colTo; c++) {
                        blocked[r * cols + c] = true;
                    }
                }
            }
        }
        return blocked;
    }

    /**
     * Hinh chu nhat trong lon nhat trong luoi.
     *
     * <p>Quet tung hang, giu chieu cao cot lien tuc cua phan trong, roi giai bai toan
     * "hinh chu nhat lon nhat trong bieu do cot" bang ngan xep - O(cols x rows).
     *
     * @return mang {@code [col, row, width, height]}, hoac null neu khong con o trong nao
     */
    private static int[] largestEmptyRectangle(boolean[] blocked, int cols, int rows) {
        int[] heights = new int[cols];
        int[] best = null;
        long bestArea = 0;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                heights[col] = blocked[row * cols + col] ? 0 : heights[col] + 1;
            }

            // Ngan xep cac cot tang dan; khi gap cot thap hon thi chot cac hinh chu nhat
            // ket thuc tai day.
            int[] stack = new int[cols + 1];
            int top = 0;
            for (int col = 0; col <= cols; col++) {
                int current = col == cols ? 0 : heights[col];
                while (top > 0 && heights[stack[top - 1]] >= current) {
                    int height = heights[stack[--top]];
                    int left = top == 0 ? 0 : stack[top - 1] + 1;
                    int width = col - left;
                    long area = (long) width * height;
                    if (height > 0 && area > bestArea) {
                        bestArea = area;
                        // heights dem NGUOC tu hang hien tai xuong, nen goc duoi la:
                        best = new int[]{left, row - height + 1, width, height};
                    }
                }
                stack[top++] = col;
            }
        }
        return best;
    }
}
