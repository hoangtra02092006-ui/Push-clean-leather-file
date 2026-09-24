package vn.printnest.nesting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.printnest.nesting.model.Placement;
import vn.printnest.nesting.model.Sheet;
import vn.printnest.nesting.model.TypeStats;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Chieu dai cua tung mau: mau nao co ban in thi phai co chieu dai.
 *
 * <p>Chu xuong chia tien giay theo cot nay. Mot mau co 11 ban in, chiem 83 cm2 giay, ma
 * bang ghi 0 cm la khach do duoc mien phi giay - sai tien that.
 *
 * <p>Truong hop sinh ra so 0: hai che do "nhoi hinh nho vao goc trong" va "xep long theo
 * hinh that" cho phep khung bao CHONG NHAU. Mau mong nhat chui gon vao khung cua mau to,
 * va phep chia cu - chia theo phan giay moi mau chiem cho RIENG - tra ve 0 cho no.
 */
class ThinPieceLengthTest {

    /** Mot tam voi mau mong nam LOT trong khung bao cua mau to. */
    private static Sheet sheetWithNestedThinPiece() {
        return new Sheet(0, 570, 400, 0.5, List.of(
                // Mau to: 30 x 37 cm, khung bao trum het phan giua tam.
                new Placement("f-to", "Nhu Y - 2486 lg.pdf", 0, 0, 0, 300, 370, false),
                // Mau mong: 8 x 0,9 cm, nam gon ben trong khung bao cua mau to.
                new Placement("f-mong", "Huynh Xuan Mai - 9251 lg.pdf", 1, 100, 100, 80, 9, false)));
    }

    @Test
    @DisplayName("Mau chui vao khung mau khac van phai co chieu dai, khong duoc bang 0")
    void nestedTypeStillGetsLength() {
        Sheet sheet = sheetWithNestedThinPiece();
        double totalLengthMm = sheet.lengthMm();

        List<TypeStats> byType = TypeStats.from(List.of(sheet), totalLengthMm);

        TypeStats thin = byType.stream()
                .filter(type -> type.categoryIndex() == 1)
                .findFirst().orElseThrow();

        assertThat(thin.pieces()).as("mau mong co dat duoc ban in").isEqualTo(1);
        assertThat(thin.shapeAreaMm2()).as("va no chiem 720 mm2 giay").isEqualTo(720);
        assertThat(thin.lengthMm())
                .as("nen no phai an mot phan chieu dai cuon, khong the bang 0")
                .isGreaterThan(0);
    }

    /**
     * Bat bien khong duoc pha: cong chieu dai moi mau lai phai ra DUNG tong chieu dai.
     *
     * <p>Lech mot chut la phan giay bo di dang bi tinh thieu hoac tinh trung cho ai do.
     */
    @Test
    @DisplayName("Cong chieu dai moi mau lai van dung bang tong chieu dai")
    void lengthsStillAddUpToTheTotal() {
        Sheet sheet = sheetWithNestedThinPiece();
        double totalLengthMm = sheet.lengthMm();

        double sum = TypeStats.from(List.of(sheet), totalLengthMm)
                .stream()
                .mapToDouble(TypeStats::lengthMm)
                .sum();

        assertThat(sum).isCloseTo(totalLengthMm, within(0.02));
    }
}
