package vn.printnest.nesting.engine;

import vn.printnest.common.ApiException;
import vn.printnest.common.ErrorCode;
import vn.printnest.common.Units;
import vn.printnest.nesting.model.NestResult;
import vn.printnest.nesting.model.NestStats;
import vn.printnest.nesting.model.Placement;
import vn.printnest.nesting.model.Sheet;
import vn.printnest.nesting.model.TypeStats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dieu phoi che do xep long theo hinh that.
 *
 * <p>Lop nay lo phan chuan bi va don dep quanh {@link TrueShapeNester}: dung mat na o tung
 * goc xoay, no them gap, chia nhieu tam, va doi ket qua tu don vi o luoi sang milimet.
 *
 * <p><b>Kich thuoc bao ra ngoai luon la so THAT</b>, tinh bang luong giac tu khung bao va
 * goc xoay, khong phai so o luoi nhan voi canh o. Luoi chi dung de do va cham; neu lay so
 * o de bao kich thuoc thi hinh se bi keo gian theo sai so lam tron - pham dung dieu cam
 * ky nhat cua ung dung nay.
 */
final class TrueShapeRunner {

    /** Canh mot o luoi. Nho hon thi khit hon nhung cham theo binh phuong. */
    private static final double CELL_MM = 1.0;

    /**
     * Cac goc xoay duoc thu.
     *
     * <p>Bon goc vuong la quan trong nhat va duoc xoay CHINH XAC (chi hoan vi chi so o).
     * Cac goc cheo phai lay mau nen mat na hoi phinh, doi lai mo ra kha nang long cac hinh
     * nam cheo - thu ma bon goc vuong khong lam duoc.
     */
    private static final double[] ANGLES = {0, 90, 180, 270, 45, 135, 225, 315};

    /** Chan an toan chong vong lap vo han khi cat nhieu tam. */
    private static final int MAX_SHEETS = 200;

    private TrueShapeRunner() {
    }

    static NestResult run(EngineInput input, List<Piece> pieces, int marginCmm, int gapCmm,
                          int sheetWidthCmm, int usableWidthCmm, Integer maxContentLengthCmm) {

        double usableWidthMm = Units.cmmToMm(usableWidthCmm);
        int sheetCols = (int) Math.floor(usableWidthMm / CELL_MM);
        if (sheetCols <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Kho ngang qua nho so voi le bien.");
        }

        int gapCells = (int) Math.ceil(Units.cmmToMm(gapCmm) / CELL_MM);
        Map<Integer, List<TrueShapeNester.ShapePiece.Oriented>> byType = new LinkedHashMap<>();

        List<TrueShapeNester.ShapePiece> shapes = new ArrayList<>(pieces.size());
        for (Piece piece : pieces) {
            List<TrueShapeNester.ShapePiece.Oriented> orientations = byType.computeIfAbsent(
                    piece.typeIndex(), key -> buildOrientations(piece, gapCells, input));
            if (orientations.isEmpty()) {
                throw new ApiException(ErrorCode.ITEM_WIDER_THAN_SHEET,
                        "Hinh \"" + piece.label() + "\" khong xep duoc vao kho o che do xep long.");
            }
            shapes.add(new TrueShapeNester.ShapePiece(piece, orientations));
        }

        // Hinh to dat truoc: hinh nho con co co hoi chui vao khe con lai, nguoc lai thi
        // hinh to khong bao gio chen duoc vao dam hinh nho da rai kin.
        shapes.sort(Comparator.comparingInt(TrueShapeNester.ShapePiece::footprint).reversed()
                .thenComparingInt(s -> s.piece().id()));

        // Chieu dai bao ra ngoai la 2*le + usedRows*CELL_MM, ma TrueShapeNester chi nhan
        // hinh khi row + rows <= so hang cua tam. Vay so hang toi da chinh la phan chieu
        // dai con lai sau khi tru le bien - khong nhieu hon mot hang nao, vi moi hang thua
        // la mot milimet tran qua gioi han tho da dat.
        Integer maxRowsPerSheet = maxContentLengthCmm == null
                ? null
                : (int) Math.floor(Units.cmmToMm(maxContentLengthCmm) / CELL_MM);

        List<Sheet> sheets = new ArrayList<>();
        List<TrueShapeNester.ShapePiece> remaining = shapes;
        int placedCount = 0;
        double totalLengthMm = 0;

        while (!remaining.isEmpty()) {
            if (sheets.size() >= MAX_SHEETS) {
                throw new ApiException(ErrorCode.NESTING_FAILED,
                        "So tam vuot qua gioi han an toan o che do xep long.");
            }
            // Khong gioi han thi cho du hai hang cho thoai mai; co gioi han thi lay dung
            // so hang cho phep, khong cong them.
            int rows = maxRowsPerSheet != null ? maxRowsPerSheet : totalRowBudget(remaining) + 2;
            TrueShapeNester nester = new TrueShapeNester(sheetCols, rows);
            List<TrueShapeNester.ShapePlacement> spots = nester.pack(remaining);

            if (spots.isEmpty()) {
                throw new ApiException(ErrorCode.NESTING_FAILED,
                        "Khong dat duoc hinh nao vao tam. Hay noi long tham so.");
            }

            double lengthMm = Units.cmmToMm(marginCmm) * 2 + nester.usedRows() * CELL_MM;
            sheets.add(toSheet(sheets.size(), spots, input, marginCmm, sheetWidthCmm, lengthMm));
            totalLengthMm += lengthMm;
            placedCount += spots.size();

            List<TrueShapeNester.ShapePiece> leftovers = new ArrayList<>();
            for (TrueShapeNester.ShapePiece shape : remaining) {
                if (spots.stream().noneMatch(s -> s.piece().id() == shape.piece().id())) {
                    leftovers.add(shape);
                }
            }
            remaining = leftovers;
        }

        if (placedCount != pieces.size()) {
            throw new ApiException(ErrorCode.NESTING_FAILED,
                    "Sai lech so luong: dat duoc " + placedCount + " hinh trong khi yeu cau "
                            + pieces.size() + ".");
        }

        return summarise(sheets, input, totalLengthMm, sheetWidthCmm, placedCount);
    }

    /** Dung mat na cho tung goc xoay duoc phep, kem ban da no gap. */
    private static List<TrueShapeNester.ShapePiece.Oriented> buildOrientations(
            Piece piece, int gapCells, EngineInput input) {

        ShapeMask base = piece.shape() != null
                ? piece.shape()
                : ShapeMask.solid(Units.cmmToMm(piece.realW()), Units.cmmToMm(piece.realH()), CELL_MM);

        boolean canRotate = input.allowRotateGlobal() && piece.allowRotate();
        List<TrueShapeNester.ShapePiece.Oriented> orientations = new ArrayList<>();

        for (double angle : ANGLES) {
            if (!canRotate && angle != 0) {
                continue;
            }
            ShapeMask mask = base.rotated(angle);
            orientations.add(new TrueShapeNester.ShapePiece.Oriented(
                    angle, mask, mask.dilatedExpanding(gapCells)));
        }
        return orientations;
    }

    /** Can tren so hang can thiet: xep chong tat ca len nhau chac chan du. */
    private static int totalRowBudget(List<TrueShapeNester.ShapePiece> shapes) {
        int total = 0;
        for (TrueShapeNester.ShapePiece shape : shapes) {
            int tallest = 0;
            for (TrueShapeNester.ShapePiece.Oriented oriented : shape.orientations()) {
                tallest = Math.max(tallest, oriented.mask().rows());
            }
            total += tallest;
        }
        return Math.max(1, total);
    }

    /** Doi cac vi tri tren luoi thanh toa do milimet that. */
    private static Sheet toSheet(int index, List<TrueShapeNester.ShapePlacement> spots,
                                 EngineInput input, int marginCmm, int sheetWidthCmm,
                                 double lengthMm) {
        double marginMm = Units.cmmToMm(marginCmm);
        double sheetWidthMm = Units.round2(Units.cmmToMm(sheetWidthCmm));
        List<Placement> placements = new ArrayList<>(spots.size());
        double shapeArea = 0;

        List<TrueShapeNester.ShapePlacement> ordered = new ArrayList<>(spots);
        ordered.sort(Comparator.comparingInt(TrueShapeNester.ShapePlacement::row)
                .thenComparingInt(TrueShapeNester.ShapePlacement::col)
                .thenComparingInt(s -> s.piece().id()));

        for (TrueShapeNester.ShapePlacement spot : ordered) {
            Piece piece = spot.piece();
            double realWMm = Units.cmmToMm(piece.realW());
            double realHMm = Units.cmmToMm(piece.realH());
            // Kich thuoc THAT sau khi xoay, tinh bang luong giac chu khong lay so o luoi.
            double radians = Math.toRadians(spot.angleDeg());
            double cos = Math.abs(Math.cos(radians));
            double sin = Math.abs(Math.sin(radians));
            double widthMm = realWMm * cos + realHMm * sin;
            double heightMm = realWMm * sin + realHMm * cos;

            placements.add(new Placement(
                    piece.fileId(), piece.label(), piece.categoryIndex(),
                    Units.round2(marginMm + spot.col() * CELL_MM),
                    Units.round2(marginMm + spot.row() * CELL_MM),
                    Units.round2(widthMm), Units.round2(heightMm),
                    false, spot.angleDeg(),
                    Units.round2(realWMm), Units.round2(realHMm)));
            shapeArea += widthMm * heightMm;
        }

        double sheetArea = sheetWidthMm * lengthMm;
        return new Sheet(index, sheetWidthMm, Units.round2(lengthMm),
                sheetArea > 0 ? round4(shapeArea / sheetArea) : 0, placements);
    }

    private static NestResult summarise(List<Sheet> sheets, EngineInput input,
                                        double totalLengthMm, int sheetWidthCmm, int placedCount) {
        double shapeArea = 0;
        double individualLength = 2 * input.marginMm();
        for (Sheet sheet : sheets) {
            for (Placement p : sheet.placements()) {
                shapeArea += p.wMm() * p.hMm();
                individualLength += p.hMm() + input.gapMm();
            }
        }
        double sheetWidthMm = Units.round2(Units.cmmToMm(sheetWidthCmm));
        double usedArea = sheetWidthMm * totalLengthMm;

        NestStats stats = new NestStats(
                sheets.size(),
                Units.round2(totalLengthMm),
                Units.round2(shapeArea),
                Units.round2(usedArea),
                usedArea > 0 ? round4(shapeArea / usedArea) : 0,
                individualLength > 0
                        ? round4(Math.max(0, (individualLength - totalLengthMm) / individualLength * 100))
                        : 0,
                placedCount,
                TypeStats.from(sheets, usedArea));

        return new NestResult(sheets, stats);
    }

    private static double round4(double value) {
        return Math.round(value * 10000d) / 10000d;
    }
}
