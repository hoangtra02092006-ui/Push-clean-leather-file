package vn.printnest.nesting.engine;

/**
 * Mot ban in can xep, da duoc "no" them khoang cach gap.
 *
 * <p>Moi truong hop cua mot item (quantity = 5 sinh ra 5 Piece) la mot doi tuong rieng.
 * Kich thuoc {@code w}/{@code h} la kich thuoc DA CONG gap; kich thuoc that de ve
 * len PDF la {@code realW}/{@code realH}.
 *
 * @param id     ma so duy nhat trong mot lan chay (dung de sap xep on dinh)
 * @param fileId ma file nguon
 * @param label  nhan hien thi (ten file)
 * @param w      chieu rong da no gap, don vi 1/100 mm
 * @param h      chieu cao da no gap, don vi 1/100 mm
 * @param realW  chieu rong that, don vi 1/100 mm
 * @param realH  chieu cao that, don vi 1/100 mm
 * @param allowRotate co duoc xoay 90 do hay khong
 * @param categoryIndex thu tu loai hinh, dung de to mau o preview
 * @param typeIndex     thu tu dong trong danh sach yeu cau, dung khi thu cac phuong an
 *                      co dinh huong theo tung loai hinh
 * @param prerotated    hinh da bi hoan doi w/h truoc khi xep (phuong an ep huong)
 * @param shape         hinh dang that; chi dung o che do xep long, null la coi nhu dac
 * @param cavities      cac o trong ben trong khung bao hinh nay, toa do theo goc
 *                      trai-duoi cua khung bao; rong neu hinh dac
 */
public record Piece(
        int id,
        String fileId,
        String label,
        int w,
        int h,
        int realW,
        int realH,
        boolean allowRotate,
        int categoryIndex,
        int typeIndex,
        boolean prerotated,
        java.util.List<Cavity> cavities,
        ShapeMask shape
) {
    /** Ban sao da xoay 90 do, dung cho cac phuong an ep huong toan loai hinh. */
    public Piece rotated90() {
        // Xoay theo chieu cao THAT chu khong phai chieu cao da no gap: hoc lom duoc dinh
        // nghia trong he toa do cua net ve, va PdfComposer cung xoay quanh o that.
        java.util.List<Cavity> turned = cavities.stream()
                .map(c -> c.rotated90(realH))
                .toList();
        return new Piece(id, fileId, label, h, w, realH, realW,
                allowRotate, categoryIndex, typeIndex, !prerotated, turned, shape);
    }

    public long area() {
        return (long) w * h;
    }

    /**
     * Dien tich THUC SU choan cho: dien tich khung bao tru cac o trong ben trong.
     *
     * <p>Dung cho can duoi cua buoc tim nhi phan. Neu van lay ca dien tich khung bao, can
     * duoi se cao hon chieu dai toi uu that, va vong tim nhi phan khong bao gio thu toi
     * phuong an ngan hon - tu tay chan mat cai loi ma hoc lom vua mang lai.
     */
    public long netArea() {
        long used = area();
        for (Cavity cavity : cavities) {
            used -= (long) cavity.w() * cavity.h();
        }
        return Math.max(0, used);
    }

    public int longSide() {
        return Math.max(w, h);
    }

    public int shortSide() {
        return Math.min(w, h);
    }
}
