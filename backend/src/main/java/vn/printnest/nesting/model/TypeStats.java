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
     * <p>Chieu dai cua moi mau = tong chieu dai nhan ty le dien tich mau do chiem:
     *
     * <pre>
     *   chieu dai mau i = dien tich cac ban cua mau i / tong dien tich moi ban
     *                     x tong chieu dai
     * </pre>
     *
     * Cong cac dong lai ra DUNG tong chieu dai, khong du mot sai so lam tron nao.
     *
     * <p><b>Chia theo dien tich RIENG cua tung ban, khong theo phan giay no chiem cho
     * rieng.</b> Hai cach cho cung ket qua o che do khung chu nhat - khung bao khong
     * chong nhau nen hai so bang nhau. Nhung o hai che do xep long, khung bao DUOC phep
     * chong nhau, va cach cu tra ve 0 cho mau nao chui gon vao khung mau khac. Do that
     * tren mot don cua xuong: mau 8 x 0,9 cm, 11 ban, 83 cm2 giay, bang ghi 0 cm - tuc
     * la khach do duoc mien phi giay. Xuong chia tien giay theo cot nay nen do la sai
     * tien that.
     *
     * <p>Noi cach khac: cai loi do xep long tiet kiem duoc gio chia DEU cho moi mau theo
     * dien tich, thay vi tang tron cho mau chui vao trong.
     *
     * @param sheets         cac tam da dan khuon
     * @param totalLengthMm  tong chieu dai cuon cua ca lan ghep
     */
    public static List<TypeStats> from(List<Sheet> sheets, double totalLengthMm) {
        Map<Integer, Accumulator> byCategory = new LinkedHashMap<>();
        double totalShapeMm2 = 0;

        for (Sheet sheet : sheets) {
            for (Placement p : sheet.placements()) {
                Accumulator acc = byCategory.computeIfAbsent(p.categoryIndex(),
                        key -> new Accumulator(p.fileId(), p.label(), key,
                                p.sourceWMm(), p.sourceHMm()));
                acc.pieces++;
                acc.areaMm2 += p.wMm() * p.hMm();
                totalShapeMm2 += p.wMm() * p.hMm();
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
                    totalShapeMm2 > 0
                            ? Units.round2(totalLengthMm * acc.areaMm2 / totalShapeMm2)
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
