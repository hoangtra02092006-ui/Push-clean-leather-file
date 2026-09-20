package vn.printnest.nesting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.printnest.nesting.engine.Piece;
import vn.printnest.nesting.engine.ShapeMask;
import vn.printnest.nesting.engine.TrueShapeNester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiem thu engine xep long theo hinh that.
 *
 * <p>Diem phai chung minh: hai hinh co goc khuyet LONG duoc vao nhau, tuc la tong cho
 * chiem it hon tong hai khung bao - dieu ma packer chu nhat khong bao gio lam duoc.
 */
class TrueShapeNesterTest {

    private static final double CELL_MM = 1.0;

    /**
     * Hinh chu L: o vuong 100x100 o, khuyet han goc TREN-PHAI 50x50.
     *
     * <p>Chon hinh nay vi no la ban rut gon cua the that trong don cua xuong: hoa van ben
     * trai, chu ben duoi, goc tren-phai bo trong.
     */
    private static ShapeMask lShape() {
        return ShapeMask.of(100, 100, CELL_MM,
                (col, row) -> !(col >= 50 && row >= 50));
    }

    private static Piece piece(int id) {
        return new Piece(id, "f", "chu-L", 100, 100, 100, 100, true, 0, 0, false, List.of(), null);
    }

    /** Mot ban in kem mat na o cac goc xoay cho truoc, khong no gap. */
    private static TrueShapeNester.ShapePiece shapePiece(int id, ShapeMask mask, double... angles) {
        List<TrueShapeNester.ShapePiece.Oriented> orientations = new java.util.ArrayList<>();
        for (double angle : angles) {
            ShapeMask turned = angle == 0 ? mask : mask.rotated(angle);
            orientations.add(new TrueShapeNester.ShapePiece.Oriented(angle, turned, turned));
        }
        return new TrueShapeNester.ShapePiece(piece(id), orientations);
    }

    @Test
    @DisplayName("Hai hinh chu L long vao nhau, ton it cho hon hai khung bao roi")
    void twoLShapesInterlock() {
        // Kho vua dung mot hinh: buoc chung phai xep chong len nhau theo chieu doc.
        TrueShapeNester nester = new TrueShapeNester(100, 400);

        List<TrueShapeNester.ShapePlacement> placed = nester.pack(List.of(
                shapePiece(0, lShape(), 0, 180),
                shapePiece(1, lShape(), 0, 180)));

        assertThat(placed).as("phai dat duoc ca hai hinh").hasSize(2);

        System.out.printf("[XEP LONG] hai hinh chu L 100x100 chiem %d hang "
                + "(xep chu nhat se ton 200)%n", nester.usedRows());

        assertThat(nester.usedRows())
                .as("long vao nhau thi phai thap hon 200 hang")
                .isLessThan(200);
    }

    @Test
    @DisplayName("Hinh dac thi khong long duoc, van ton dung hai khung bao")
    void solidShapesCannotInterlock() {
        ShapeMask solid = ShapeMask.solid(100, 100, CELL_MM);
        TrueShapeNester nester = new TrueShapeNester(100, 400);

        List<TrueShapeNester.ShapePlacement> placed = nester.pack(List.of(
                shapePiece(0, solid, 0, 180),
                shapePiece(1, solid, 0, 180)));

        assertThat(placed).hasSize(2);
        assertThat(nester.usedRows())
                .as("hinh dac thi khong the long, phai dung 200 hang")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("Khong o nao cua hai hinh dung nhau")
    void placedShapesNeverOverlap() {
        ShapeMask shape = lShape();
        TrueShapeNester nester = new TrueShapeNester(200, 400);

        List<TrueShapeNester.ShapePlacement> placed = nester.pack(List.of(
                shapePiece(0, shape, 0, 90, 180, 270),
                shapePiece(1, shape, 0, 90, 180, 270),
                shapePiece(2, shape, 0, 90, 180, 270),
                shapePiece(3, shape, 0, 90, 180, 270)));

        assertThat(placed).hasSize(4);

        // Dung lai toan bo tam roi kiem tung o: khong o nao bi danh dau hai lan.
        boolean[][] grid = new boolean[400][200];
        for (TrueShapeNester.ShapePlacement spot : placed) {
            ShapeMask mask = spot.angleDeg() == 0 ? shape : shape.rotated(spot.angleDeg());
            for (int r = 0; r < mask.rows(); r++) {
                for (int c = 0; c < mask.cols(); c++) {
                    if (!mask.get(c, r)) {
                        continue;
                    }
                    int row = spot.row() + r;
                    int col = spot.col() + c;
                    assertThat(grid[row][col])
                            .as("o (%d,%d) bi hai hinh cung chiem", col, row)
                            .isFalse();
                    grid[row][col] = true;
                }
            }
        }
    }

    @Test
    @DisplayName("No mat na de chua khoang ho: hai hinh cach nhau du so o yeu cau")
    void inflatedMaskKeepsGap() {
        int gapCells = 3;
        ShapeMask shape = ShapeMask.solid(40, 40, CELL_MM);
        ShapeMask inflated = shape.dilatedExpanding(gapCells);

        TrueShapeNester nester = new TrueShapeNester(100, 200);
        List<TrueShapeNester.ShapePiece.Oriented> orientations =
                List.of(new TrueShapeNester.ShapePiece.Oriented(0, shape, inflated));

        List<TrueShapeNester.ShapePlacement> placed = nester.pack(List.of(
                new TrueShapeNester.ShapePiece(piece(0), orientations),
                new TrueShapeNester.ShapePiece(piece(1), orientations)));

        assertThat(placed).hasSize(2);

        TrueShapeNester.ShapePlacement a = placed.get(0);
        TrueShapeNester.ShapePlacement b = placed.get(1);
        int gapX = Math.max(a.col() - (b.col() + b.cols()), b.col() - (a.col() + a.cols()));
        int gapY = Math.max(a.row() - (b.row() + b.rows()), b.row() - (a.row() + a.rows()));

        assertThat(Math.max(gapX, gapY))
                .as("hai hinh dac phai cach nhau it nhat %d o", gapCells)
                .isGreaterThanOrEqualTo(gapCells);
    }

    @Test
    @DisplayName("Xoay 180 do cho ra dung hinh guong, khong mat o nao")
    void rotationPreservesArea() {
        ShapeMask shape = lShape();
        ShapeMask turned = shape.rotated(180);

        // Xoay co no them mot o nen dien tich tang nhe; khong duoc GIAM.
        assertThat(turned.occupiedCells())
                .as("xoay khong duoc lam mat net ve")
                .isGreaterThanOrEqualTo(shape.occupiedCells());
        assertThat(turned.occupiedCells())
                .as("xoay khong duoc phinh qua da")
                .isLessThan((int) (shape.occupiedCells() * 1.35));
    }
}
