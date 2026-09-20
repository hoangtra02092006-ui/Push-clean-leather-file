package vn.printnest.nesting.model;

/**
 * Cach thuat toan duoc phep sap xep hinh.
 *
 * <p>Hai che do nay khac nhau o cho: hinh co duoc phep nam LONG vao phan trong ben trong
 * khung bao cua hinh khac hay khong.
 */
public enum NestingMode {

    /**
     * Chi xoay 0 hoac 90 do, moi hinh chiem tron khung bao chu nhat cua no.
     *
     * <p>Day la che do mac dinh va la che do da duoc do ky: bo nghiem thu 145 ban in cho
     * 478,2 cm, ty le lap day 92,45%. Bo tri ra luoi ngay ngan nen tho cat nhanh.
     */
    ORTHOGONAL,

    /**
     * Cho hinh nho chui vao phan trong ben trong khung bao cua hinh lon.
     *
     * <p>Tiet kiem hon voi cac hinh co khung bao "rong" - hinh chu L, hinh tron, hinh co
     * goc bi khuyet. Doi lai bo tri khong con thanh luoi nen tho cat vat va hon.
     */
    FREE,

    /**
     * Xep long theo HINH DANG THAT: khung bao duoc phep chong nhau, chi net ve la khong
     * duoc cham nhau. Hinh duoc xoay o nhieu goc, ke ca goc cheo.
     *
     * <p>Tiet kiem nhieu nhat, nhung CHI dung duoc khi tho cat theo vien hinh. Neu cat
     * theo hinh chu nhat thi phan trang quanh hinh la mot phan san pham, va viec nhet
     * hinh khac vao do se lam hong ban in.
     */
    TRUE_SHAPE
}
