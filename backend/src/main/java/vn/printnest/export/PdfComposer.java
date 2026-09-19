package vn.printnest.export;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;
import vn.printnest.common.ApiException;
import vn.printnest.common.ErrorCode;
import vn.printnest.common.Units;
import vn.printnest.file.FileService;
import vn.printnest.file.FileType;
import vn.printnest.file.StoredFile;
import vn.printnest.nesting.model.Placement;
import vn.printnest.nesting.model.Sheet;

import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Dung file PDF thanh pham tu ket qua dan khuon.
 *
 * <p>Nguyen tac quan trong nhat: <b>khong raster hoa</b>. File nguon PDF duoc nhung lai
 * duoi dang form XObject qua {@link LayerUtility#importPageAsForm}, giu nguyen duong
 * vector va chu - in ra sac net o moi kich thuoc. Neu doi sang anh, ban in se bi ram.
 *
 * <p>He toa do PDF co goc o TRAI-DUOI va don vi la point, trung voi quy uoc cua engine,
 * nen chi can doi milimet sang point roi tinh tien.
 */
@Component
public class PdfComposer {

    /** Do day duong cat, don vi point. */
    private static final float CUT_LINE_WIDTH = 0.25f;

    /** Mau xam cua duong cat. */
    private static final float CUT_LINE_GREY = 0.6f;

    private final FileService fileService;

    public PdfComposer(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Xuat mot tam thanh file PDF.
     *
     * @param sheet        tam can xuat
     * @param drawCutLines co ve duong cat quanh moi hinh khong
     * @return noi dung file PDF
     */
    public byte[] compose(Sheet sheet, boolean drawCutLines) {
        try (PDDocument output = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(
                    Units.mmToPt(sheet.widthMm()),
                    Units.mmToPt(sheet.lengthMm())));
            output.addPage(page);

            LayerUtility layerUtility = new LayerUtility(output);
            // Mot file nguon co the xuat hien hang chuc lan tren cung mot tam; nhung mot
            // lan roi dung lai giup file nho hon nhieu va may in xu ly nhanh hon.
            Map<String, PDFormXObject> formCache = new HashMap<>();
            Map<String, PDImageXObject> imageCache = new HashMap<>();
            Map<String, PDDocument> sourceCache = new HashMap<>();

            try (PDPageContentStream content = new PDPageContentStream(
                    output, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                for (Placement placement : sheet.placements()) {
                    StoredFile source = fileService.require(placement.fileId());
                    if (source.type() == FileType.PDF) {
                        drawPdf(content, layerUtility, formCache, sourceCache, source, placement);
                    } else {
                        drawImage(content, output, imageCache, source, placement);
                    }
                    if (drawCutLines) {
                        drawCutLine(content, placement);
                    }
                }
            } finally {
                for (PDDocument document : sourceCache.values()) {
                    document.close();
                }
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            output.save(bytes);
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Khong dung duoc file PDF cho tam " + (sheet.index() + 1) + ": " + ex.getMessage());
        }
    }

    /**
     * Nhung mot trang PDF nguon vao dung o da dinh.
     *
     * <p>Phep bien doi duoc ghep theo thu tu: dich toi vi tri dat &rarr; xoay 90 do neu
     * can &rarr; keo ve dung kich thuoc &rarr; dich goc cua hop TrimBox ve 0. Buoc cuoi
     * quan trong: nhieu file co goc hop khong nam o (0,0), bo qua se lam hinh lech.
     */
    private void drawPdf(PDPageContentStream content, LayerUtility layerUtility,
                         Map<String, PDFormXObject> formCache, Map<String, PDDocument> sourceCache,
                         StoredFile source, Placement placement) throws IOException {

        PDFormXObject form = formCache.get(source.id());
        PDRectangle box;
        if (form == null) {
            PDDocument document = sourceCache.computeIfAbsent(source.id(), key -> {
                try {
                    return Loader.loadPDF(Path.of(source.storedPath()).toFile());
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            });
            form = layerUtility.importPageAsForm(document, 0);
            formCache.put(source.id(), form);
        }
        box = form.getBBox();

        double targetWidthMm = placement.rotated() ? placement.hMm() : placement.wMm();
        double targetHeightMm = placement.rotated() ? placement.wMm() : placement.hMm();
        float scaleX = box.getWidth() == 0 ? 1f : Units.mmToPt(targetWidthMm) / box.getWidth();
        float scaleY = box.getHeight() == 0 ? 1f : Units.mmToPt(targetHeightMm) / box.getHeight();

        AffineTransform transform = new AffineTransform();
        transform.translate(Units.mmToPt(placement.xMm()), Units.mmToPt(placement.yMm()));
        if (placement.rotated()) {
            // Xoay quanh goc trai-duoi roi day sang phai dung mot chieu rong da xoay,
            // de hinh ro vao dung o chu khong nam ngoai trang.
            transform.translate(Units.mmToPt(placement.wMm()), 0);
            transform.rotate(Math.PI / 2);
        }
        transform.scale(scaleX, scaleY);
        transform.translate(-box.getLowerLeftX(), -box.getLowerLeftY());

        content.saveGraphicsState();
        content.transform(new org.apache.pdfbox.util.Matrix(transform));
        content.drawForm(form);
        content.restoreGraphicsState();
    }

    /** Nhung mot file anh, keo dung kich thuoc vat ly da tinh. */
    private void drawImage(PDPageContentStream content, PDDocument output,
                           Map<String, PDImageXObject> imageCache,
                           StoredFile source, Placement placement) throws IOException {

        PDImageXObject image = imageCache.get(source.id());
        if (image == null) {
            image = PDImageXObject.createFromFile(source.storedPath(), output);
            imageCache.put(source.id(), image);
        }

        double targetWidthMm = placement.rotated() ? placement.hMm() : placement.wMm();
        double targetHeightMm = placement.rotated() ? placement.wMm() : placement.hMm();

        AffineTransform transform = new AffineTransform();
        transform.translate(Units.mmToPt(placement.xMm()), Units.mmToPt(placement.yMm()));
        if (placement.rotated()) {
            transform.translate(Units.mmToPt(placement.wMm()), 0);
            transform.rotate(Math.PI / 2);
        }
        transform.scale(Units.mmToPt(targetWidthMm), Units.mmToPt(targetHeightMm));

        content.saveGraphicsState();
        content.transform(new org.apache.pdfbox.util.Matrix(transform));
        // drawImage voi ma tran don vi: anh duoc keo theo dung phep bien doi o tren.
        content.drawImage(image, 0, 0, 1, 1);
        content.restoreGraphicsState();
    }

    /** Ve khung cat mo quanh mot hinh de tho de cat. */
    private void drawCutLine(PDPageContentStream content, Placement placement) throws IOException {
        content.saveGraphicsState();
        content.setStrokingColor(CUT_LINE_GREY, CUT_LINE_GREY, CUT_LINE_GREY);
        content.setLineWidth(CUT_LINE_WIDTH);
        content.addRect(
                Units.mmToPt(placement.xMm()),
                Units.mmToPt(placement.yMm()),
                Units.mmToPt(placement.wMm()),
                Units.mmToPt(placement.hMm()));
        content.stroke();
        content.restoreGraphicsState();
    }
}
