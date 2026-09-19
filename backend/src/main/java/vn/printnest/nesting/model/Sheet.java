package vn.printnest.nesting.model;

import java.util.List;

/**
 * Mot tam in - tuong ung mot file PDF xuat ra.
 *
 * @param index      so thu tu tam, bat dau tu 0
 * @param widthMm    chieu rong tam (bang dung kho cuon)
 * @param lengthMm   chieu dai tam da cat sat (da cong le hai dau)
 * @param fillRate   ty le lap day 0..1 (dien tich hinh / dien tich tam)
 * @param placements danh sach hinh tren tam
 */
public record Sheet(
        int index,
        double widthMm,
        double lengthMm,
        double fillRate,
        List<Placement> placements
) {
}
