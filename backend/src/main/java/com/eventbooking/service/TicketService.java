package com.eventbooking.service;

import com.eventbooking.dto.kafka.BookingConfirmedEvent;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/**
 * Generates a one-page PDF ticket/receipt per booking, with a QR code
 * encoding the booking id (a real system would encode a signed verification
 * token instead, so a venue scanner could check authenticity offline - noted
 * here as a scope simplification, not implemented since there's no scanner
 * app in this project). Runs entirely on the Kafka consumer thread, decoupled
 * from the payment request itself - PDF generation is cheap but non-trivial
 * work that has no business blocking the user's checkout response.
 *
 * Laid out in two sections like a real ticket: a receipt/details section on
 * top, and a tear-off "admit one" stub at the bottom (dashed perforation
 * line between them, QR code in the stub) - matching the ticket-stub theme
 * used across the frontend (see tailwind.config.js: the `stub`/`perforation`
 * design tokens this PDF's colors and dashed divider are deliberately kept
 * in sync with).
 */
@Service
@Slf4j
public class TicketService {

    @Value("${app.tickets.output-dir}")
    private String outputDir;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy \u00b7 hh:mm a");

    // Same hex values as frontend/tailwind.config.js - keep these two in sync if the brand palette ever changes.
    private static final Color INK = new Color(0x12, 0x12, 0x1A);
    private static final Color INK_LINE = new Color(0x2B, 0x2C, 0x3A);
    private static final Color GOLD = new Color(0xE8, 0xA3, 0x3D);
    private static final Color PAPER = new Color(0xF1, 0xEF, 0xEA);
    private static final Color PAPER_MUTED = new Color(0x8B, 0x8A, 0x99);
    private static final Color WHITE = Color.WHITE;

    public Path generateTicketPdf(BookingConfirmedEvent event) throws IOException, WriterException {
        Path dir = Path.of(outputDir);
        Files.createDirectories(dir);
        Path outputPath = dir.resolve("ticket-" + event.getBookingId() + ".pdf");

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float pageW = page.getMediaBox().getWidth();
            float pageH = page.getMediaBox().getHeight();
            float margin = 44;

            BufferedImage qrImage = generateQrCode("BOOKING:" + event.getBookingId(), 400);
            PDImageXObject qrPdImage = imageToPdImage(document, qrImage);
            PDImageXObject logoImage = loadLogo(document);

            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream c = new PDPageContentStream(document, page)) {

                // ---- Header band ----
                float headerH = 90;
                fillRect(c, 0, pageH - headerH, pageW, headerH, INK);
                if (logoImage != null) {
                    float logoSize = 44;
                    c.drawImage(logoImage, margin, pageH - headerH / 2 - logoSize / 2, logoSize, logoSize);
                    text(c, bold, 22, margin + logoSize + 14, pageH - headerH / 2 - 8, GOLD, "StubLine");
                } else {
                    text(c, bold, 22, margin, pageH - headerH / 2 - 8, GOLD, "StubLine");
                }
                textRightAligned(c, regular, 10, pageW - margin, pageH - headerH / 2 - 3, PAPER_MUTED,
                        "E-TICKET / RECEIPT   \u00b7   BOOKING #" + event.getBookingId());

                float y = pageH - headerH - 46;

                // ---- Event title ----
                y = text(c, bold, 19, margin, y, INK, truncate(event.getEventTitle(), 46));
                y -= 6;
                y = text(c, regular, 12, margin, y, PAPER_MUTED, event.getEventDate().format(DATE_FORMAT));
                y -= 22;

                // ---- Details card ----
                float cardTop = y;
                float cardPad = 20;
                float rowGap = 24;
                int rows = 4;
                float cardH = cardPad * 2 + rowGap * rows - 6;
                fillRect(c, margin, cardTop - cardH, pageW - 2 * margin, cardH, PAPER);
                strokeRect(c, margin, cardTop - cardH, pageW - 2 * margin, cardH, new Color(0xE4, 0xE1, 0xD8), 0.75f);

                float rowY = cardTop - cardPad - 10;
                rowY = detailRow(c, regular, bold, margin + cardPad, pageW - margin - cardPad, rowY, "Booked by", event.getUserName(), rowGap);
                rowY = detailRow(c, regular, bold, margin + cardPad, pageW - margin - cardPad, rowY, "Seats", String.join(", ", event.getSeatLabels()), rowGap);
                rowY = detailRow(c, regular, bold, margin + cardPad, pageW - margin - cardPad, rowY, "Total paid", "Rs. " + formatAmount(event.getTotalAmount()), rowGap);
                detailRow(c, regular, bold, margin + cardPad, pageW - margin - cardPad, rowY, "Booking reference", "#" + event.getBookingId(), rowGap);

                y = cardTop - cardH - 34;

                // ---- Perforation (tear line) ----
                float perfY = y;
                dashedLine(c, margin, perfY, pageW - margin, perfY, INK_LINE, 1.2f, new float[]{3, 4});

                // ---- Stub section ----
                float stubTop = perfY - 26;
                text(c, bold, 10, margin, stubTop, PAPER_MUTED, "ADMIT ONE  \u00b7  KEEP THIS STUB");

                float qrSize = 130;
                float qrX = pageW - margin - qrSize;
                float qrY = stubTop - qrSize - 6;
                c.drawImage(qrPdImage, qrX, qrY, qrSize, qrSize);
                strokeRect(c, qrX - 1, qrY - 1, qrSize + 2, qrSize + 2, new Color(0xE4, 0xE1, 0xD8), 0.75f);

                float stubTextY = stubTop - 30;
                stubTextY = text(c, bold, 15, margin, stubTextY, INK, truncate(event.getEventTitle(), 32));
                stubTextY -= 4;
                stubTextY = text(c, regular, 11, margin, stubTextY, PAPER_MUTED, event.getEventDate().format(DATE_FORMAT));
                stubTextY -= 14;
                stubTextY = text(c, bold, 12, margin, stubTextY, INK, "Seat: " + String.join(", ", event.getSeatLabels()));
                text(c, regular, 9, margin, stubTextY - 16, PAPER_MUTED, "Scan the QR code at the venue entrance");

                // ---- Footer ----
                textCentered(c, regular, 9, pageW / 2, margin - 14, PAPER_MUTED,
                        "This is a computer-generated ticket and does not require a signature.  \u00b7  StubLine");
            }

            document.save(outputPath.toFile());
        }

        log.info("Generated ticket PDF for bookingId={} at {}", event.getBookingId(), outputPath);
        return outputPath;
    }

    // ---------- drawing helpers ----------

    private float detailRow(PDPageContentStream c, PDType1Font regular, PDType1Font bold,
                             float xLeft, float xRight, float y, String label, String value, float rowGap) throws IOException {
        text(c, regular, 10.5f, xLeft, y, PAPER_MUTED, label.toUpperCase());
        textRightAligned(c, bold, 12, xRight, y, INK, truncate(value == null ? "" : value, 48));
        return y - rowGap;
    }

    private void fillRect(PDPageContentStream c, float x, float y, float w, float h, Color color) throws IOException {
        c.setNonStrokingColor(color);
        c.addRect(x, y, w, h);
        c.fill();
    }

    private void strokeRect(PDPageContentStream c, float x, float y, float w, float h, Color color, float width) throws IOException {
        c.setStrokingColor(color);
        c.setLineWidth(width);
        c.addRect(x, y, w, h);
        c.stroke();
    }

    private void dashedLine(PDPageContentStream c, float x1, float y1, float x2, float y2, Color color, float width, float[] pattern) throws IOException {
        c.setStrokingColor(color);
        c.setLineWidth(width);
        c.setLineDashPattern(pattern, 0);
        c.moveTo(x1, y1);
        c.lineTo(x2, y2);
        c.stroke();
        c.setLineDashPattern(new float[]{}, 0);
    }

    private float text(PDPageContentStream c, PDType1Font font, float size, float x, float y, Color color, String value) throws IOException {
        c.setNonStrokingColor(color);
        c.beginText();
        c.setFont(font, size);
        c.newLineAtOffset(x, y);
        c.showText(sanitize(value));
        c.endText();
        return y - size - 6;
    }

    private void textRightAligned(PDPageContentStream c, PDType1Font font, float size, float xRight, float y, Color color, String value) throws IOException {
        String s = sanitize(value);
        float width = font.getStringWidth(s) / 1000 * size;
        text(c, font, size, xRight - width, y, color, s);
    }

    private void textCentered(PDPageContentStream c, PDType1Font font, float size, float xCenter, float y, Color color, String value) throws IOException {
        String s = sanitize(value);
        float width = font.getStringWidth(s) / 1000 * size;
        text(c, font, size, xCenter - width / 2, y, color, s);
    }

    private String truncate(String s, int maxChars) {
        if (s == null) return "";
        return s.length() <= maxChars ? s : s.substring(0, maxChars - 3) + "...";
    }

    // PDType1Font/Helvetica can't encode characters outside WinAnsi (e.g. some
    // typographic punctuation) - strip anything that would throw at showText().
    private String sanitize(String s) {
        return s.replaceAll("[^\\x00-\\xFF]", "");
    }

    private String formatAmount(java.math.BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private PDImageXObject loadLogo(PDDocument document) {
        try (InputStream is = new ClassPathResource("branding/stubline-icon.png").getInputStream()) {
            return PDImageXObject.createFromByteArray(document, is.readAllBytes(), "logo");
        } catch (IOException ex) {
            log.warn("Could not load ticket logo image, continuing without it: {}", ex.getMessage());
            return null;
        }
    }

    private BufferedImage generateQrCode(String data, int size) throws WriterException {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private PDImageXObject imageToPdImage(PDDocument document, BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return PDImageXObject.createFromByteArray(document, baos.toByteArray(), "qr");
    }
}
