package vn.printnest.nesting.engine;

import java.util.List;

/**
 * Ket qua mot lan chay packer tren MOT tam.
 *
 * @param placed     danh sach hinh da dat
 * @param unplaced   danh sach hinh khong nhet duoc (rong khi tam du dai)
 * @param usedLength chieu dai thuc su da dung, don vi 1/100 mm
 */
public record PackResult(List<PlacedPiece> placed, List<Piece> unplaced, int usedLength) {

    public int placedCount() {
        return placed.size();
    }

    public boolean packedEverything() {
        return unplaced.isEmpty();
    }
}
