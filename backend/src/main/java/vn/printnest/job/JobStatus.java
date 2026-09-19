package vn.printnest.job;

/** Vong doi cua mot job ghep file. */
public enum JobStatus {
    /** Da nhan yeu cau, dang xep hang. */
    PENDING,
    /** Dang tinh toan bo tri. */
    RUNNING,
    /** Da xong, co ket qua de tai ve. */
    DONE,
    /** That bai, xem truong error de biet ly do. */
    FAILED
}
