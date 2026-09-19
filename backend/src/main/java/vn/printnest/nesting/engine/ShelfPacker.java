package vn.printnest.nesting.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Thuat toan xep theo tang (shelf / next-fit-decreasing-height) dung lam DOI CHUNG.
 *
 * <p>Xep tu trai sang phai thanh tung tang; het cho ngang thi mo tang moi ngay phia
 * tren tang cu. Ket qua thuong kem MaxRects nhung on dinh va cuc nhanh, nen dung de:
 * <ul>
 *   <li>lam can tren (upper bound) cho buoc tim nhi phan chieu dai trong
 *       {@link NestingEngine};</li>
 *   <li>lam luoi an toan: neu MaxRects vi ly do nao do cho ket qua toi hon, engine van
 *       chon duoc phuong an tot hon trong hai.</li>
 * </ul>
 *
 * <p>Khi hinh duoc phep xoay, moi hinh duoc dat nam ngang (canh dai theo phuong X) de
 * tang so hinh tren moi tang.
 */
public final class ShelfPacker {

    private final int binWidth;
    private final int binHeight;

    public ShelfPacker(int binWidth, int binHeight) {
        this.binWidth = binWidth;
        this.binHeight = binHeight;
    }

    /**
     * Xep danh sach hinh theo dung thu tu truyen vao.
     *
     * @param pieces      danh sach can xep
     * @param allowRotate cho phep xoay o muc toan cuc
     * @return ket qua xep tren tam nay
     */
    public PackResult pack(List<Piece> pieces, boolean allowRotate) {
        List<PlacedPiece> placed = new ArrayList<>();
        List<Piece> unplaced = new ArrayList<>();

        int shelfY = 0;
        int shelfHeight = 0;
        int cursorX = 0;

        for (Piece piece : pieces) {
            boolean canRotate = allowRotate && piece.allowRotate();

            int w = piece.w();
            int h = piece.h();
            boolean rotated = false;
            // Nam ngang de tang so hinh tren mot tang.
            if (canRotate && piece.h() > piece.w()) {
                w = piece.h();
                h = piece.w();
                rotated = true;
            }
            // Neu van khong vua be ngang, thu lai huong con lai.
            if (w > binWidth && canRotate) {
                int tmpW = w;
                w = h;
                h = tmpW;
                rotated = !rotated;
            }
            if (w > binWidth) {
                unplaced.add(piece);
                continue;
            }

            if (cursorX + w > binWidth) {
                shelfY += shelfHeight;
                shelfHeight = 0;
                cursorX = 0;
            }
            if (shelfY + h > binHeight) {
                unplaced.add(piece);
                continue;
            }

            placed.add(new PlacedPiece(piece, cursorX, shelfY, w, h, rotated));
            cursorX += w;
            shelfHeight = Math.max(shelfHeight, h);
        }

        int usedLength = 0;
        for (PlacedPiece p : placed) {
            usedLength = Math.max(usedLength, p.top());
        }
        return new PackResult(placed, unplaced, usedLength);
    }
}
