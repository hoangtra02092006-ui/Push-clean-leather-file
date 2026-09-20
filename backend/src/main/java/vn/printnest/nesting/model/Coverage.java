package vn.printnest.nesting.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dien tich giay THAT SU bi cac hinh phu len, moi cho chi dem MOT lan.
 *
 * <p>Truoc day ty le lap day lay bang TONG dien tich cac khung bao chia cho dien tich
 * tam. O che do xep luoi thi dung, vi khung bao khong bao gio chong nhau. Nhung o che do
 * nhet hinh nho vao cho trong, khung bao duoc phep LONG VAO NHAU - do chinh la muc dich
 * cua che do do. Phan giay bi hai khung bao cung trum len bi dem hai lan, nen ty le lap
 * day vuot qua 100% va "giay bo di" thanh so am. Do la con so vo nghia voi nguoi dung.
 *
 * <p>Lop nay dem dung mot lan bang cach lay HOP cua cac hinh chu nhat. Voi che do xep
 * luoi, khong co cho nao chong nhau nen hop bang dung tong - moi con so cu giu nguyen
 * khong xe dich mot chut nao.
 *
 * <p>Cach lam: nen toa do. Cat tam thanh cac dai ngang tai moi canh tren/duoi cua hinh,
 * va cac cot doc tai moi canh trai/phai. Trong moi o luoi thu duoc, hoac khong hinh nao
 * phu, hoac co - khong bao gio phu mot phan. Nho vay phep tinh la CHINH XAC chu khong
 * phai xap xi, khong phu thuoc do min cua luoi nao ca.
 */
public final class Coverage {

    private final double totalAreaMm2;
    private final Map<Integer, Double> areaByCategory;

    private Coverage(double totalAreaMm2, Map<Integer, Double> areaByCategory) {
        this.totalAreaMm2 = totalAreaMm2;
        this.areaByCategory = Map.copyOf(areaByCategory);
    }

    /** Tong dien tich giay bi phu, don vi mm2. */
    public double totalAreaMm2() {
        return totalAreaMm2;
    }

    /**
     * Dien tich giay phan bo cho tung loai hinh, cong lai dung bang {@link #totalAreaMm2()}.
     *
     * <p>Cho nao co hai khung bao cung trum thi tinh cho hinh dung TRUOC trong danh sach
     * (danh sach da sap xep tat dinh theo y, roi x, roi id). Noi cach khac day la "phan
     * giay ma mau nay chiem cho RIENG": mot hinh nho chui gon vao goc trong cua hinh lon
     * khong ton them gia y nao, nen phan do khong duoc tinh cho no.
     */
    public double areaOf(int categoryIndex) {
        return areaByCategory.getOrDefault(categoryIndex, 0d);
    }

    /** Cac loai hinh co mat, theo thu tu gap dau tien. */
    public Map<Integer, Double> areaByCategory() {
        return areaByCategory;
    }

    /** Gop ket qua cua nhieu tam lai. */
    public static Coverage merge(List<Coverage> parts) {
        double total = 0;
        Map<Integer, Double> merged = new LinkedHashMap<>();
        for (Coverage part : parts) {
            total += part.totalAreaMm2;
            part.areaByCategory.forEach((key, value) -> merged.merge(key, value, Double::sum));
        }
        return new Coverage(total, merged);
    }

    /**
     * Tinh dien tich bi phu cua mot tam.
     *
     * @param placements cac hinh tren tam, theo thu tu tat dinh
     */
    public static Coverage of(List<Placement> placements) {
        if (placements.isEmpty()) {
            return new Coverage(0, Map.of());
        }

        double[] xs = edges(placements, true);
        double[] ys = edges(placements, false);

        Map<Integer, Double> byCategory = new LinkedHashMap<>();
        double total = 0;

        int[] owner = new int[xs.length - 1];

        for (int band = 0; band < ys.length - 1; band++) {
            double bandBottom = ys[band];
            double bandTop = ys[band + 1];
            double bandHeight = bandTop - bandBottom;
            if (bandHeight <= 0) {
                continue;
            }
            Arrays.fill(owner, -1);
            boolean anyOwner = false;

            for (Placement p : placements) {
                // Dai nam tron trong hinh hay khong. Khong the nam mot phan: moi canh cua
                // moi hinh deu la mot duong cat, nen dai luon nam han trong hoac han ngoai.
                if (p.yMm() > bandBottom || p.yMm() + p.hMm() < bandTop) {
                    continue;
                }
                int from = indexOf(xs, p.xMm());
                int to = indexOf(xs, p.xMm() + p.wMm());
                for (int slot = from; slot < to; slot++) {
                    if (owner[slot] < 0) {
                        owner[slot] = p.categoryIndex();
                        anyOwner = true;
                    }
                }
            }

            if (!anyOwner) {
                continue;
            }
            for (int slot = 0; slot < owner.length; slot++) {
                if (owner[slot] < 0) {
                    continue;
                }
                double cell = (xs[slot + 1] - xs[slot]) * bandHeight;
                total += cell;
                byCategory.merge(owner[slot], cell, Double::sum);
            }
        }

        return new Coverage(total, byCategory);
    }

    /** Cac duong cat theo mot truc: moi canh cua moi hinh, da loai trung va sap tang dan. */
    private static double[] edges(List<Placement> placements, boolean horizontal) {
        List<Double> values = new ArrayList<>(placements.size() * 2);
        for (Placement p : placements) {
            if (horizontal) {
                values.add(p.xMm());
                values.add(p.xMm() + p.wMm());
            } else {
                values.add(p.yMm());
                values.add(p.yMm() + p.hMm());
            }
        }
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();

        int size = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (i == 0 || sorted[i] != sorted[size - 1]) {
                sorted[size++] = sorted[i];
            }
        }
        return Arrays.copyOf(sorted, size);
    }

    private static int indexOf(double[] edges, double value) {
        int found = Arrays.binarySearch(edges, value);
        return found >= 0 ? found : -(found + 1);
    }
}
