package vn.printnest.export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dua tu mat na diem anh sang duong cat khep kin.
 *
 * <p>Ba buoc: <b>no ra ngoai</b> mot khoang, <b>do vien</b>, roi <b>bot bot diem</b>.
 *
 * <p><b>Vi sao no ra ngoai bang cach an vao mat na chu khong phai day duong ra.</b> Day
 * mot da giac ra ngoai (polygon offset) nghe thi gon nhung phai tu xu ly goc lom, canh
 * tu cat chinh no, va hai hinh sat nhau thi hai duong dam vao nhau. Lam tren mat na thi
 * ba chuyen do tu het: no ra la phep no anh (dilate), hai hinh sat nhau tu dinh lai thanh
 * mot khoi - dung la hanh vi minh muon, va cai "gop thanh mot duong" ma yeu cau noi toi.
 *
 * <p><b>Chi lay vien NGOAI.</b> Mot mieng decal duoc cat vong quanh chu khong khoet ruot,
 * nen moi khoi hinh cho ra dung mot duong khep kin. Lo thung ben trong bi bo qua - do
 * cung la cach "bo lo nho ben trong" ma yeu cau mo ta.
 */
final class CutContours {

    /** Gia tri trong mat na, dung chung quy uoc voi {@link WhiteChannel}. */
    private static final byte INK = (byte) 255;
    private static final byte NONE = 0;

    private CutContours() {
    }

    /**
     * Mot duong cat khep kin, toa do tinh bang diem anh.
     *
     * <p>Cac dinh nam tren goc diem anh chu khong tam diem anh, nen mot khoi mot diem cho
     * ra o vuong 1x1 dung nghia chu khong phai mot diem khong co dien tich.
     */
    record Path(List<double[]> points) {

        /**
         * Dien tich co dau. Dau cho biet duong nay la vien ngoai hay vien mot lo thung.
         */
        double signedArea() {
            double sum = 0;
            for (int i = 0; i < points.size(); i++) {
                double[] a = points.get(i);
                double[] b = points.get((i + 1) % points.size());
                sum += a[0] * b[1] - b[0] * a[1];
            }
            return sum / 2;
        }
    }

    /**
     * No mat na ra moi phia {@code radius} diem anh.
     *
     * <p>Hai luot mot chieu thay vi quet ca o vuong quanh moi diem: ket qua y het voi nhan
     * hinh vuong, nhung mot tam 300 DPI co gan 80 trieu diem.
     *
     * <p>Diem ngoai tam coi nhu KHONG co muc, nen hinh cham mep khong bi no lan ra ngoai
     * trang - nguoc voi {@link WhiteChannel#erode} von coi ngoai tam la khoang trong de
     * hinh cham mep bi co vao.
     */
    static byte[] dilate(byte[] mask, int width, int height, int radius) {
        if (radius <= 0) {
            return mask;
        }
        byte[] pass = new byte[mask.length];

        for (int y = 0; y < height; y++) {
            int row = y * width;
            int inks = 0;
            for (int x = 0; x <= radius && x < width; x++) {
                if (mask[row + x] != NONE) {
                    inks++;
                }
            }
            for (int x = 0; x < width; x++) {
                pass[row + x] = inks > 0 ? INK : NONE;

                int leaving = x - radius;
                int entering = x + radius + 1;
                if (leaving >= 0 && mask[row + leaving] != NONE) {
                    inks--;
                }
                if (entering < width && mask[row + entering] != NONE) {
                    inks++;
                }
            }
        }

        byte[] out = new byte[mask.length];
        for (int x = 0; x < width; x++) {
            int inks = 0;
            for (int y = 0; y <= radius && y < height; y++) {
                if (pass[y * width + x] != NONE) {
                    inks++;
                }
            }
            for (int y = 0; y < height; y++) {
                out[y * width + x] = inks > 0 ? INK : NONE;

                int leaving = y - radius;
                int entering = y + radius + 1;
                if (leaving >= 0 && pass[leaving * width + x] != NONE) {
                    inks--;
                }
                if (entering < height && pass[entering * width + x] != NONE) {
                    inks++;
                }
            }
        }
        return out;
    }

    /**
     * Do vien ngoai cua moi khoi trong mat na.
     *
     * <p><b>Cach lam.</b> Moi canh diem anh co mot ben co muc va mot ben khong la mot doan
     * vien. Gan huong cho tung doan sao cho phan CO MUC luon nam ben phai huong di, roi
     * noi cac doan lai theo dinh chung - moi chuoi khep kin la mot duong.
     *
     * <p>Lam theo canh chu khong theo tam diem anh vi no khong co truong hop nhap nhang:
     * moi dinh chi co mot doan di ra, tru diem hai khoi cham nhau theo duong cheo. Do
     * cung la ly do phai no mat na TRUOC khi do - sau khi no ra vai chuc diem thi hai khoi
     * cham cheo da dinh han vao nhau.
     */
    static List<Path> trace(byte[] mask, int width, int height) {
        // Dinh duoc danh so theo hang: vx * (height + 1) + vy.
        Map<Long, List<Long>> edges = new HashMap<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask[y * width + x] == NONE) {
                    continue;
                }
                // Tren: di sang phai. Phai: di xuong. Duoi: di sang trai. Trai: di len.
                if (y == 0 || mask[(y - 1) * width + x] == NONE) {
                    addEdge(edges, height, x, y, x + 1, y);
                }
                if (x == width - 1 || mask[y * width + x + 1] == NONE) {
                    addEdge(edges, height, x + 1, y, x + 1, y + 1);
                }
                if (y == height - 1 || mask[(y + 1) * width + x] == NONE) {
                    addEdge(edges, height, x + 1, y + 1, x, y + 1);
                }
                if (x == 0 || mask[y * width + x - 1] == NONE) {
                    addEdge(edges, height, x, y + 1, x, y);
                }
            }
        }

        List<Path> paths = new ArrayList<>();
        List<Long> starts = new ArrayList<>(edges.keySet());
        starts.sort(null);

        for (Long start : starts) {
            while (true) {
                List<Long> from = edges.get(start);
                if (from == null || from.isEmpty()) {
                    break;
                }
                List<double[]> points = new ArrayList<>();
                long at = start;
                while (true) {
                    List<Long> next = edges.get(at);
                    if (next == null || next.isEmpty()) {
                        break;
                    }
                    long to = next.remove(next.size() - 1);
                    points.add(new double[]{at / (height + 1L), at % (height + 1L)});
                    at = to;
                    if (at == start) {
                        break;
                    }
                }
                if (points.size() >= 4) {
                    paths.add(new Path(collapseCollinear(points)));
                }
            }
        }
        return paths;
    }

    /**
     * Gop cac dinh nam thang hang lien nhau, khong sai mot ly nao.
     *
     * <p>Do vien theo canh diem anh cho ra duong toan doan dai mot diem, nen mot canh ngang
     * dai 500 diem thanh 500 dinh trong khi hai dinh la du. Buoc nay chay mot luot O(n) va
     * KHONG lam sai lech hinh dang - khac han buoc {@link #simplify} vua bot dinh vua chap
     * nhan sai so.
     *
     * <p>No con la cai chan cho truong hop xau cua Douglas-Peucker: thuat toan do co the
     * len toi O(n2), nen dua vao no mot duong vai tram nghin dinh la treo may. Gop truoc
     * thi duong thuc te chi con vai nghin dinh.
     */
    private static List<double[]> collapseCollinear(List<double[]> points) {
        int size = points.size();
        if (size < 3) {
            return points;
        }
        List<double[]> out = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            double[] previous = points.get((i - 1 + size) % size);
            double[] current = points.get(i);
            double[] next = points.get((i + 1) % size);

            // Giu lai neu huong di doi tai day; bo neu di thang qua.
            double cross = (current[0] - previous[0]) * (next[1] - current[1])
                    - (current[1] - previous[1]) * (next[0] - current[0]);
            if (cross != 0) {
                out.add(current);
            }
        }
        return out.isEmpty() ? points : out;
    }

    private static void addEdge(Map<Long, List<Long>> edges, int height,
                                int x1, int y1, int x2, int y2) {
        long from = x1 * (height + 1L) + y1;
        long to = x2 * (height + 1L) + y2;
        edges.computeIfAbsent(from, key -> new ArrayList<>(1)).add(to);
    }

    /**
     * Bot bot dinh ma giu nguyen hinh dang, sai lech khong qua {@code tolerance} diem anh.
     *
     * <p>Do vien theo canh diem anh cho ra duong toan goc vuong: mot duong tron duong kinh
     * 5 cm o 300 DPI thanh gan hai chuc nghin dinh. May cat khong can den the, ma file PDF
     * thi phinh ra vo ich.
     *
     * <p>Thuat toan Douglas-Peucker: giu hai dau, tim dinh lech xa duong noi hai dau nhat;
     * xa hon nguong thi giu lai va chia doi bai toan, khong thi bo het khuc giua.
     */
    static List<double[]> simplify(List<double[]> points, double tolerance) {
        if (points.size() < 4 || tolerance <= 0) {
            return points;
        }
        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        simplifySegment(points, 0, points.size() - 1, tolerance, keep);

        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (keep[i]) {
                out.add(points.get(i));
            }
        }
        return out;
    }

    /**
     * Chia doi bang vong lap chu khong de quy.
     *
     * <p>Mot duong vien o 300 DPI co the sau hang chuc nghin muc - de quy la tran ngan xep,
     * ma loi do chi xuat hien voi file phuc tap cua khach chu khong bao gio xuat hien luc
     * thu bang hinh don gian.
     */
    private static void simplifySegment(List<double[]> points, int first, int last,
                                        double tolerance, boolean[] keep) {
        java.util.Deque<int[]> todo = new java.util.ArrayDeque<>();
        todo.push(new int[]{first, last});

        while (!todo.isEmpty()) {
            int[] range = todo.pop();
            int from = range[0];
            int to = range[1];
            if (to <= from + 1) {
                continue;
            }

            double[] a = points.get(from);
            double[] b = points.get(to);
            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            double length = Math.hypot(dx, dy);

            double worst = -1;
            int worstAt = -1;
            for (int i = from + 1; i < to; i++) {
                double[] p = points.get(i);
                double distance = length == 0
                        ? Math.hypot(p[0] - a[0], p[1] - a[1])
                        : Math.abs(dx * (a[1] - p[1]) - (a[0] - p[0]) * dy) / length;
                if (distance > worst) {
                    worst = distance;
                    worstAt = i;
                }
            }

            if (worst > tolerance) {
                keep[worstAt] = true;
                todo.push(new int[]{from, worstAt});
                todo.push(new int[]{worstAt, to});
            }
        }
    }
}
