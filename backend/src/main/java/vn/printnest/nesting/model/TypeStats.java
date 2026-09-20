package vn.printnest.nesting.model;

import vn.printnest.common.Units;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * So lieu cua MOT LOAI HINH tren toan bo lan ghep.
 *
 * <p>Bang tong hop tra loi cau "ca don nay ton bao nhieu met"; bang nay tra loi cau
 * "trong so met do, tung mau chiem bao nhieu". Chu xuong can con so thu hai de chia tien
 * giay cho tung khach, va tho can no de biet mau nao dang an cho nhat.
 *
 * <p>Hai ty le nhin cung mot thu tu hai phia, va hai cai nay KHAC NHAU:
 * <ul>
 *   <li>{@code shareOfShapes} - trong so cua loai nay trong TONG DIEN TICH HINH. Cong
 *       tat ca cac loai lai dung bang 1.</li>
 *   <li>{@code fillRate} - loai nay chiem bao nhieu phan DIEN TICH GIAY da dung. Cong
 *       tat ca cac loai lai dung bang ty le lap day chung cua ca lan ghep, phan con
 *       thieu chinh la giay bo di.</li>
 * </ul>
 *
 * @param fileId         ma file nguon
 * @param label          ten hien thi
 * @param categoryIndex  thu tu loai hinh, khop voi mau trong preview
 * @param pieces         so ban in cua loai nay da dat duoc
 * @param widthMm        chieu rong mot ban, do TRUOC khi xoay
 * @param heightMm       chieu cao mot ban, do truoc khi xoay
 * @param shapeAreaMm2   tong dien tich cac ban cua loai nay
 * @param shareOfShapes  ty le 0..1 tren tong dien tich hinh
 * @param fillRate       ty le 0..1 tren dien tich giay da dung
 */
public record TypeStats(
        String fileId,
        String label,
        int categoryIndex,
        int pieces,
        double widthMm,
        double heightMm,
        double shapeAreaMm2,
        double shareOfShapes,
        double fillRate
) {

    /**
     * Gom cac ban in tren moi tam lai theo loai hinh.
     *
     * <p>Gom theo {@code categoryIndex} chu khong theo ten file: do dung la khoa ma
     * preview dung de to mau, nen bang so lieu va hinh ve luon noi ve cung mot thu.
     *
     * <p>Thu tu tra ve luon theo {@code categoryIndex} tang dan - bat buoc, vi ket qua
     * phai TAT DINH: cung dau vao thi cung dau ra, ke ca thu tu dong trong bang.
     *
     * @param sheets       cac tam da dan khuon
     * @param usedAreaMm2  tong dien tich giay da dung
     */
    public static List<TypeStats> from(List<Sheet> sheets, double usedAreaMm2) {
        Map<Integer, Accumulator> byCategory = new LinkedHashMap<>();
        double totalShapeArea = 0;

        for (Sheet sheet : sheets) {
            for (Placement p : sheet.placements()) {
                Accumulator acc = byCategory.computeIfAbsent(p.categoryIndex(),
                        key -> new Accumulator(p.fileId(), p.label(), key,
                                p.sourceWMm(), p.sourceHMm()));
                double area = p.wMm() * p.hMm();
                acc.pieces++;
                acc.areaMm2 += area;
                totalShapeArea += area;
            }
        }

        List<TypeStats> stats = new ArrayList<>(byCategory.size());
        for (Accumulator acc : byCategory.values()) {
            stats.add(new TypeStats(
                    acc.fileId,
                    acc.label,
                    acc.categoryIndex,
                    acc.pieces,
                    Units.round2(acc.widthMm),
                    Units.round2(acc.heightMm),
                    Units.round2(acc.areaMm2),
                    totalShapeArea > 0 ? round4(acc.areaMm2 / totalShapeArea) : 0,
                    usedAreaMm2 > 0 ? round4(acc.areaMm2 / usedAreaMm2) : 0));
        }
        stats.sort(Comparator.comparingInt(TypeStats::categoryIndex));
        return List.copyOf(stats);
    }

    private static double round4(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    /** Bien dem tam trong luc gom nhom. */
    private static final class Accumulator {
        private final String fileId;
        private final String label;
        private final int categoryIndex;
        private final double widthMm;
        private final double heightMm;
        private int pieces;
        private double areaMm2;

        private Accumulator(String fileId, String label, int categoryIndex,
                            double widthMm, double heightMm) {
            this.fileId = fileId;
            this.label = label;
            this.categoryIndex = categoryIndex;
            this.widthMm = widthMm;
            this.heightMm = heightMm;
        }
    }
}
