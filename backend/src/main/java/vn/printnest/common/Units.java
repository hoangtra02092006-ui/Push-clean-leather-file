package vn.printnest.common;

/**
 * Quy doi don vi dung chung toan he thong.
 *
 * <p>API trao doi bang milimet (double). Thuat toan nesting tinh toan bang so nguyen
 * don vi 1/100 mm ("centi-milimet", viet tat CMM) de tranh sai so dau phay dong.
 * Xuat PDF dung don vi point cua PostScript (1 inch = 72 pt = 25.4 mm).
 */
public final class Units {

    /** So don vi noi bo tren mot milimet. */
    public static final int CMM_PER_MM = 100;

    private static final double MM_PER_INCH = 25.4d;
    private static final double POINTS_PER_INCH = 72d;

    private Units() {
    }

    /** Milimet -> don vi noi bo, lam tron len de khong bao gio xep chat hon thuc te. */
    public static int mmToCmmCeil(double mm) {
        return (int) Math.ceil(mm * CMM_PER_MM - 1e-9);
    }

    /** Milimet -> don vi noi bo, lam tron xuong (dung cho kich thuoc kho chua). */
    public static int mmToCmmFloor(double mm) {
        return (int) Math.floor(mm * CMM_PER_MM + 1e-9);
    }

    /** Don vi noi bo -> milimet. */
    public static double cmmToMm(int cmm) {
        return cmm / (double) CMM_PER_MM;
    }

    /** Milimet -> point (don vi cua PDF). */
    public static float mmToPt(double mm) {
        return (float) (mm * POINTS_PER_INCH / MM_PER_INCH);
    }

    /** Point -> milimet. */
    public static double ptToMm(double pt) {
        return pt * MM_PER_INCH / POINTS_PER_INCH;
    }

    /** Lam tron ve 2 chu so thap phan de tra ra API cho gon. */
    public static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
