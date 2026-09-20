package vn.printnest.file;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;

import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tim VUNG CO NET VE THAT SU cua mot trang PDF, bo qua khoang trang bao quanh.
 *
 * <p>Vi sao cần lớp này: rất nhiều file in được xuất ra trên khổ giấy lớn (A3, A4) trong
 * khi hình thật chỉ nằm gọn một góc. Lấy trọn khổ trang thì phần trắng bao quanh cũng bị
 * tính là hình, và thuật toán dàn khuôn phải chừa chỗ cho cả khoảng trắng đó — tốn giấy vô ích.
 *
 * <p><b>Đọc vector chứ không quét ảnh.</b> Lớp này duyệt content stream và gom hộp bao của
 * mọi lệnh vẽ (đường, hình, ảnh, chữ, tô chuyển sắc). Cách này đúng hơn hẳn so với render
 * ra ảnh rồi dò pixel không trắng, vì:
 * <ul>
 *   <li>Một hình <b>tô màu trắng</b> vẫn là nét vẽ thật và phải được giữ. Dò pixel sẽ tưởng
 *       là khoảng trống rồi cắt mất — đúng cái lỗi "không giữ nguyên hình".</li>
 *   <li>Không phụ thuộc độ phân giải, không có sai số làm tròn pixel.</li>
 * </ul>
 *
 * <p><b>Nhiều hình nhỏ trong một file:</b> mọi lệnh vẽ đều được gộp vào CÙNG một hộp bao
 * (phép hợp). Kết quả là một hộp duy nhất ôm trọn tất cả, nên vị trí tương đối giữa các
 * hình con được giữ nguyên y hệt — không hình nào bị tách rời hay xê dịch.
 *
 * <p>Mọi ước lượng đều <b>nới ra</b> chứ không thu vào: thà lấy dư vài phần mười milimet
 * còn hơn cắt mất nét của bản in.
 */
public final class PdfContentBoxFinder extends PDFGraphicsStreamEngine {

    /** Chan tren so luong hinh ghi lai, de file cuc phuc tap khong an het bo nho. */
    private static final int MAX_SHAPES = 50_000;

    /** Đường đang được dựng bởi các lệnh moveTo/lineTo/curveTo. */
    private final GeneralPath currentPath = new GeneralPath();

    /** Hộp bao gom được, trong hệ toạ độ trang PDF (point, gốc trái-dưới). */
    private Rectangle2D bounds;

    /** Quy tắc tô đang chờ áp dụng cho lệnh cắt (clip) ở cuối đường. */
    private Integer pendingClipWindingRule;

    /**
     * Hộp bao của TỪNG lệnh vẽ, để sau này biết chỗ nào trống chỗ nào đặc.
     *
     * <p>Chỉ có hộp bao chung thì không đủ: một hình chữ L và một hình vuông đặc có hộp
     * bao y hệt nhau. Danh sách này cho phép dựng lại bản đồ chỗ trống bên trong hộp bao.
     *
     * <p>Dùng hộp bao của từng lệnh vẽ chứ không phải đường nét chính xác, tức là <b>lấy
     * dư</b>: một đường chéo sẽ bị tính cả ô vuông bao quanh nó. Sai về phía coi là đặc
     * thì chỉ mất cơ hội nhồi thêm; sai về phía coi là trống thì hai hình đè lên nhau —
     * hỏng bản in. Luôn chọn hướng sai an toàn.
     */
    private final List<Rectangle2D> shapes = new ArrayList<>();

    public PdfContentBoxFinder(PDPage page) {
        super(page);
    }

    /**
     * Duyệt trang và trả về hộp bao của phần có nét vẽ.
     *
     * @return hộp bao trong hệ toạ độ trang (point), hoặc {@code null} nếu trang trắng trơn
     */
    public Rectangle2D find() throws IOException {
        processPage(getPage());
        return bounds;
    }

    /** Gộp một hộp vào kết quả, sau khi cắt theo vùng clip đang hiệu lực. */
    private void include(Rectangle2D box) {
        if (box == null || box.getWidth() < 0 || box.getHeight() < 0) {
            return;
        }
        Rectangle2D effective = box;

        // Nét vẽ bị clip che khuất thì không được tính — nếu không, một đường kẻ dài bị
        // cắt còn một ô nhỏ vẫn kéo hộp bao ra tận mép trang.
        Area clip = getGraphicsState().getCurrentClippingPath();
        if (clip != null) {
            Rectangle2D clipBox = clip.getBounds2D();
            if (clipBox.isEmpty()) {
                return;
            }
            effective = effective.createIntersection(clipBox);
            if (effective.getWidth() <= 0 || effective.getHeight() <= 0) {
                return;
            }
        }

        bounds = bounds == null ? effective : bounds.createUnion(effective);
        if (shapes.size() < MAX_SHAPES) {
            shapes.add(effective);
        }
    }

    /** Hop bao cua tung lenh ve, theo thu tu gap duoc. */
    public List<Rectangle2D> shapes() {
        return shapes;
    }

    /** Hop bao chung, sau khi da goi { #find()}. */
    public Rectangle2D bounds() {
        return bounds;
    }

    // ------------------------------------------------------------------
    // Dựng đường
    // ------------------------------------------------------------------

    @Override
    public void moveTo(float x, float y) {
        currentPath.moveTo(x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        currentPath.lineTo(x, y);
    }

    @Override
    public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        currentPath.curveTo(x1, y1, x2, y2, x3, y3);
    }

    @Override
    public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
        currentPath.moveTo(p0.getX(), p0.getY());
        currentPath.lineTo(p1.getX(), p1.getY());
        currentPath.lineTo(p2.getX(), p2.getY());
        currentPath.lineTo(p3.getX(), p3.getY());
        currentPath.closePath();
    }

    @Override
    public Point2D getCurrentPoint() {
        return currentPath.getCurrentPoint();
    }

    @Override
    public void closePath() {
        currentPath.closePath();
    }

    // ------------------------------------------------------------------
    // Vẽ
    // ------------------------------------------------------------------

    @Override
    public void fillPath(int windingRule) {
        includeCurrentPath(0);
        resetPath();
    }

    @Override
    public void strokePath() {
        // Nét vẽ nở ra mỗi bên nửa độ dày, nên hộp bao phải nới thêm chừng ấy.
        includeCurrentPath(strokeHalfWidth());
        resetPath();
    }

    @Override
    public void fillAndStrokePath(int windingRule) {
        includeCurrentPath(strokeHalfWidth());
        resetPath();
    }

    @Override
    public void endPath() {
        resetPath();
    }

    private void includeCurrentPath(double outset) {
        if (currentPath.getCurrentPoint() == null && currentPath.getPathIterator(null).isDone()) {
            return;
        }
        Rectangle2D box = currentPath.getBounds2D();
        if (outset > 0) {
            box = new Rectangle2D.Double(box.getX() - outset, box.getY() - outset,
                    box.getWidth() + 2 * outset, box.getHeight() + 2 * outset);
        }
        include(box);
    }

    /** Nửa độ dày nét, đã quy đổi theo phép biến đổi hiện hành. */
    private double strokeHalfWidth() {
        double lineWidth = getGraphicsState().getLineWidth();
        Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
        // Lấy hệ số phóng lớn hơn trong hai trục để nới về phía an toàn.
        double scale = Math.max(
                Math.hypot(ctm.getScaleX(), ctm.getShearY()),
                Math.hypot(ctm.getShearX(), ctm.getScaleY()));
        return Math.max(lineWidth * scale, 0) / 2d;
    }

    private void resetPath() {
        if (pendingClipWindingRule != null) {
            currentPath.setWindingRule(pendingClipWindingRule);
            getGraphicsState().intersectClippingPath(new Area(currentPath));
            pendingClipWindingRule = null;
        }
        currentPath.reset();
    }

    @Override
    public void clip(int windingRule) {
        // Theo đặc tả PDF, lệnh W chỉ ghi nhận ý định; vùng cắt có hiệu lực sau khi
        // đường kết thúc (n, f, S...). Vì vậy chỉ ghi nhớ ở đây.
        pendingClipWindingRule = windingRule == Path2D.WIND_EVEN_ODD
                ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO;
    }

    @Override
    public void drawImage(PDImage pdImage) {
        // Ảnh luôn được vẽ vào ô vuông đơn vị rồi biến đổi bằng CTM.
        Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
        GeneralPath unitSquare = new GeneralPath(new Rectangle2D.Double(0, 0, 1, 1));
        include(unitSquare.createTransformedShape(ctm.createAffineTransform()).getBounds2D());
    }

    @Override
    public void shadingFill(COSName shadingName) {
        // Tô chuyển sắc lấp đầy vùng cắt hiện hành; không có vùng cắt thì bỏ qua để
        // tránh kéo hộp bao ra cả trang một cách vô cớ.
        Area clip = getGraphicsState().getCurrentClippingPath();
        if (clip != null) {
            include(clip.getBounds2D());
        }
    }

    // ------------------------------------------------------------------
    // Chữ
    // ------------------------------------------------------------------

    @Override
    protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code,
                             Vector displacement) throws IOException {
        Rectangle2D glyphBox = glyphBoxInTextSpace(font, displacement);
        AffineTransform transform = textRenderingMatrix.createAffineTransform();
        include(new GeneralPath(glyphBox).createTransformedShape(transform).getBounds2D());

        super.showGlyph(textRenderingMatrix, font, code, displacement);
    }

    /**
     * Hộp bao một ký tự trong không gian chữ.
     *
     * <p>Dùng hộp bao chung của cả phông thay vì đo từng ký tự: giá trị này LỚN HƠN hoặc
     * bằng ký tự thật, nên sai về phía lấy dư — không bao giờ cắt cụt chữ. Đo chính xác
     * từng glyph đòi hỏi dựng đường nét của ký tự, đắt hơn nhiều mà không đổi được gì
     * ngoài vài phần mười milimet.
     */
    private Rectangle2D glyphBoxInTextSpace(PDFont font, Vector displacement) {
        float advance = displacement.getX();
        try {
            BoundingBox fontBox = font.getBoundingBox();
            if (fontBox != null && fontBox.getHeight() > 0) {
                // Phông đơn giản dùng không gian glyph 1/1000 đơn vị chữ.
                float scale = 1 / 1000f;
                float lowerY = fontBox.getLowerLeftY() * scale;
                float upperY = fontBox.getUpperRightY() * scale;
                float width = Math.max(advance, fontBox.getWidth() * scale);
                return new Rectangle2D.Float(0, lowerY, width, upperY - lowerY);
            }
        } catch (IOException | RuntimeException ignored) {
            // Phông hỏng hoặc thiếu metric: rơi xuống ước lượng thô bên dưới.
        }
        // Ước lượng dự phòng: cao một đơn vị chữ, hơi thò xuống dưới đường cơ sở.
        return new Rectangle2D.Float(0, -0.25f, Math.max(advance, 0.5f), 1.25f);
    }
}
