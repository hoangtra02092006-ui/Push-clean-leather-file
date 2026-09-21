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
 * <p>Con so quan trong nhat la {@code lengthMm}: mau nay an bao nhieu met cuon. Do la
 * thu chu xuong dung de chia tien giay, vi giay tinh tien theo met dai chu khong theo
 * met vuong.
 *
 * <p>Cong {@code lengthMm} cua tat ca cac loai lai dung bang TONG CHIEU DAI cua ca lan
 * ghep. Nghia la phan giay bo di da duoc chia deu vao dau tung mau theo ty le - dung
 * vay moi cong bang, vi khong mau nao mot minh gay ra cho trong.
 *
 * @param fileId         ma file nguon
 * @param label          ten hien thi
 * @param categoryIndex  thu tu loai hinh, khop voi mau trong preview
 * @param pieces         so ban in cua loai nay da dat duoc
 * @param widthMm        chieu rong mot ban, do TRUOC khi xoay
 * @param heightMm       chieu cao mot ban, do truoc khi xoay
 * @param shapeAreaMm2   tong dien tich cac ban cua loai nay
 * @param lengthMm       chieu dai cuon ma mau nay an, da gom ca phan giay bo di chia deu
 */
public record TypeStats(
        String fileId,
        String label,
        int categoryIndex,
        int pieces,
        double widthMm,
        double heightMm,
        double shapeAreaMm2,
        double lengthMm
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
     * <p>Chieu dai cua moi mau = tong chieu dai nhan ty le giay mau do chiem cho RIENG.
     * Viet cach khac, dung cong thuc quen thuoc hon:
     *
     * <pre>
     *   chieu dai mau i = (dien tich giay mau i chiem / dien tich giay da dung)
     *                     / ty le lap day chung
     *                     x tong chieu dai
     * </pre>
     *
     * Hai cach cho cung mot ket qua vi dien tich giay da dung bi khu di; ban rut gon
     * o duoi con co loi la cong cac dong lai ra DUNG tong chieu dai, khong du mot sai so
     * lam tron nao.
     *
     * @param sheets         cac tam da dan khuon
     * @param coverage       dien tich giay tung loai chiem cho RIENG, da dem moi cho mot lan
     * @param totalLengthMm  tong chieu dai cuon cua ca lan ghep
     */
    public static List<TypeStats> from(List<Sheet> sheets, Coverage coverage, double totalLengthMm) {
        Map<Integer, Accumulator> byCategory = new LinkedHashMap<>();
        double coveredMm2 = coverage.totalAreaMm2();

        for (Sheet sheet : sheets) {
            for (Placement p : sheet.placements()) {
                Accumulator acc = byCategory.computeIfAbsent(p.categoryIndex(),
                        key -> new Accumulator(p.fileId(), p.label(), key,
                                p.sourceWMm(), p.sourceHMm()));
                acc.pieces++;
                acc.areaMm2 += p.wMm() * p.hMm();
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
                    // Chia theo dien tich giay CHIEM CHO RIENG chu khong theo tong khung
                    // bao: hai khung bao long vao nhau thi phan chung chi duoc tinh mot
                    // lan, neu khong cong cac dong lai se vuot qua tong chieu dai.
                    coveredMm2 > 0
                            ? Units.round2(totalLengthMm * coverage.areaOf(acc.categoryIndex) / coveredMm2)
                            : 0));
        }
        stats.sort(Comparator.comparingInt(TypeStats::categoryIndex));
        return List.copyOf(stats);
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
