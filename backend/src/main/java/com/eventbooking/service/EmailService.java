package com.eventbooking.service;

import com.eventbooking.dto.kafka.BookingConfirmedEvent;
import com.eventbooking.entity.OtpPurpose;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/**
 * Sends REAL email via SMTP (JavaMailSender, configured under spring.mail.*
 * in application.yml). Two different failure-handling philosophies here,
 * deliberately:
 *
 *  - Booking confirmation: failure is logged and swallowed. The booking
 *    itself already succeeded and is durably CONFIRMED in MySQL by the time
 *    this runs (see BookingConfirmationConsumer) - a failed confirmation
 *    email is unfortunate but must never look like the booking failed.
 *  - OTP email: failure is RE-THROWN. Unlike a booking confirmation (a nice-
 *    to-have notification), the OTP email IS the whole point of the request -
 *    if it can't be delivered, the user has no way to proceed at all, so
 *    the caller needs to know immediately rather than being told "check
 *    your email" for a code that never arrived.
 *
 * Both emails are HTML now (previously plain text) and both set an explicit
 * sender DISPLAY NAME ("StubLine") via MimeMessageHelper.setFrom(address,
 * personal) - without it, mail clients show the raw SMTP username
 * (spring.mail.username, e.g. a personal Gmail address) as the sender
 * instead of the product name.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final String SENDER_NAME = "StubLine";
    private static final String BRAND_INK = "#12121A";
    private static final String BRAND_GOLD = "#E8A33D";
    private static final String BRAND_PAPER = "#F1EFEA";
    private static final String BRAND_PAPER_MUTED = "#8B8A99";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy \u00b7 hh:mm a");

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public void sendBookingConfirmation(BookingConfirmedEvent event, Path ticketPdfPath) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, SENDER_NAME);
            helper.setTo(event.getUserEmail());
            helper.setSubject("Your StubLine ticket for \"" + event.getEventTitle() + "\" is confirmed");
            helper.setText(buildBookingConfirmationHtml(event), true);
            helper.addInline("logo", new ClassPathResource("branding/stubline-logo-full.png"));
            helper.addAttachment("StubLine-Ticket-" + event.getBookingId() + ".pdf", ticketPdfPath.toFile());

            mailSender.send(message);
            log.info("Sent booking confirmation email to {} for booking #{}", event.getUserEmail(), event.getBookingId());
        } catch (Exception ex) {
            log.error("Failed to send booking confirmation email to {} for booking #{}: {}",
                    event.getUserEmail(), event.getBookingId(), ex.getMessage());
        }
    }

    public void sendOtpEmail(String toEmail, String code, OtpPurpose purpose) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, SENDER_NAME);
            helper.setTo(toEmail);
            helper.setSubject("Your StubLine verification code: " + code);
            helper.setText(buildOtpHtml(code, purpose), true);
            helper.addInline("logo", new ClassPathResource("branding/stubline-logo-full.png"));

            mailSender.send(message);
            log.info("Sent OTP email ({}) to {}", purpose, toEmail);
        } catch (Exception ex) {
            log.error("Failed to send OTP email to {}: {}", toEmail, ex.getMessage());
            throw new RuntimeException("Could not send the verification email. Please check the address and try again.", ex);
        }
    }

    /**
     * Same "log and swallow, never throw" philosophy as
     * sendBookingConfirmation() - the event is already cancelled in MySQL by
     * the time this runs (see EventService.cancelEvent()), a failed
     * notification email must never look like the cancellation itself
     * failed, and one recipient's bad address shouldn't stop the rest of
     * the affected users from being notified (EventService loops and calls
     * this per-booking, catching per-call).
     */
    public void sendEventCancelledEmail(String toEmail, String userName, String eventTitle, Long bookingId, String reason) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, SENDER_NAME);
            helper.setTo(toEmail);
            helper.setSubject("\"" + eventTitle + "\" has been cancelled");
            helper.setText(buildEventCancelledHtml(userName, eventTitle, bookingId, reason), true);
            helper.addInline("logo", new ClassPathResource("branding/stubline-logo-full.png"));

            mailSender.send(message);
            log.info("Sent event-cancelled email to {} for booking #{}", toEmail, bookingId);
        } catch (Exception ex) {
            log.error("Failed to send event-cancelled email to {} for booking #{}: {}", toEmail, bookingId, ex.getMessage());
        }
    }

    public void sendEventPostponedEmail(String toEmail, String userName, String eventTitle, Long bookingId,
                                         String oldDate, String newDate, String note) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, SENDER_NAME);
            helper.setTo(toEmail);
            helper.setSubject("\"" + eventTitle + "\" has a new date");
            helper.setText(buildEventPostponedHtml(userName, eventTitle, bookingId, oldDate, newDate, note), true);
            helper.addInline("logo", new ClassPathResource("branding/stubline-logo-full.png"));

            mailSender.send(message);
            log.info("Sent event-postponed email to {} for booking #{}", toEmail, bookingId);
        } catch (Exception ex) {
            log.error("Failed to send event-postponed email to {} for booking #{}: {}", toEmail, bookingId, ex.getMessage());
        }
    }

    /**
     * The profile "name" field is required at signup/Google-login and can't
     * be blanked out via profile update (UpdateProfileRequest.name is
     * @NotBlank) - so this should never actually see a blank value in
     * practice. Kept as a defensive fallback anyway: an email that greets
     * nobody by name looks broken, "Hi ," reads like a template bug, and
     * this is cheap insurance against any future path that skips that
     * validation.
     */
    private String displayName(String userName) {
        return (userName == null || userName.isBlank()) ? "User" : userName.trim();
    }

    private String htmlShell(String preheader, String bodyHtml) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>"
                + "<body style=\"margin:0;padding:0;background-color:" + BRAND_PAPER + ";font-family:Helvetica,Arial,sans-serif;\">"
                + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">" + preheader + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:" + BRAND_PAPER + ";padding:32px 16px;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;width:100%;background-color:#FFFFFF;border-radius:12px;overflow:hidden;border:1px solid #E4E1D8;\">"
                + "<tr><td style=\"background-color:" + BRAND_INK + ";padding:28px 32px;\">"
                + "<img src=\"cid:logo\" alt=\"StubLine\" height=\"36\" style=\"display:block;height:36px;width:auto;\"/>"
                + "</td></tr>"
                + "<tr><td style=\"padding:36px 32px;\">" + bodyHtml + "</td></tr>"
                + "<tr><td style=\"padding:20px 32px;background-color:" + BRAND_PAPER + ";border-top:1px solid #E4E1D8;\">"
                + "<p style=\"margin:0;font-size:12px;line-height:1.6;color:" + BRAND_PAPER_MUTED + ";\">"
                + "StubLine &middot; Live event ticketing<br/>This is an automated message - please don't reply directly to this email.</p>"
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private String buildBookingConfirmationHtml(BookingConfirmedEvent event) {
        String seats = String.join(", ", event.getSeatLabels());
        String amount = formatAmount(event.getTotalAmount());
        String eventDate = event.getEventDate().format(DATE_FORMAT);

        String body =
                "<p style=\"margin:0 0 4px;font-size:13px;font-weight:bold;letter-spacing:1px;color:#8A6A2E;text-transform:uppercase;\">Booking confirmed</p>"
              + "<h1 style=\"margin:0 0 20px;font-size:24px;line-height:1.3;color:" + BRAND_INK + ";\">Hi " + escape(displayName(event.getUserName())) + ", you're all set!</h1>"
              + "<p style=\"margin:0 0 24px;font-size:15px;line-height:1.6;color:#333;\">Your booking for <strong>" + escape(event.getEventTitle()) + "</strong> is confirmed. Your e-ticket (with QR code) is attached to this email as a PDF - just show it at the door.</p>"
              + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:" + BRAND_PAPER + ";border-radius:8px;border:1px solid #E4E1D8;margin-bottom:24px;\">"
              + "<tr><td style=\"padding:20px 24px;\">"
              + detailRow("Event", escape(event.getEventTitle()))
              + detailRow("Date &amp; time", escape(eventDate))
              + detailRow("Seats", escape(seats))
              + detailRow("Total paid", "Rs. " + amount)
              + detailRow("Booking reference", "#" + event.getBookingId())
              + "</td></tr></table>"
              + "<p style=\"margin:0;font-size:13px;line-height:1.6;color:" + BRAND_PAPER_MUTED + ";\">Need to make changes? You can manage this booking anytime from <strong>My Bookings</strong> in your StubLine account.</p>";

        return htmlShell("Your booking for " + event.getEventTitle() + " is confirmed", body);
    }

    private String buildOtpHtml(String code, OtpPurpose purpose) {
        String action = switch (purpose) {
            case SIGNUP_VERIFICATION -> "verify your email and activate your account";
            case LOGIN -> "log in to your account";
            case PASSWORD_RESET -> "reset your password";
        };

        String spacedCode = String.join(" ", code.split(""));

        String body =
                "<p style=\"margin:0 0 4px;font-size:13px;font-weight:bold;letter-spacing:1px;color:#8A6A2E;text-transform:uppercase;\">Verification code</p>"
              + "<h1 style=\"margin:0 0 16px;font-size:24px;line-height:1.3;color:" + BRAND_INK + ";\">Confirm it's you</h1>"
              + "<p style=\"margin:0 0 24px;font-size:15px;line-height:1.6;color:#333;\">Use this code to " + action + ". It's valid for 10 minutes and can only be used once.</p>"
              + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:24px;\">"
              + "<tr><td align=\"center\" style=\"background-color:" + BRAND_INK + ";border-radius:8px;padding:22px;\">"
              + "<span style=\"font-family:'Courier New',monospace;font-size:32px;font-weight:bold;letter-spacing:10px;color:" + BRAND_GOLD + ";\">" + spacedCode + "</span>"
              + "</td></tr></table>"
              + "<p style=\"margin:0;font-size:13px;line-height:1.6;color:" + BRAND_PAPER_MUTED + ";\">If you didn't request this, you can safely ignore this email - no changes will be made to your account.</p>";

        return htmlShell("Your StubLine verification code is " + code, body);
    }

    private String buildEventCancelledHtml(String userName, String eventTitle, Long bookingId, String reason) {
        String body =
                "<p style=\"margin:0 0 4px;font-size:13px;font-weight:bold;letter-spacing:1px;color:#B54848;text-transform:uppercase;\">Event cancelled</p>"
              + "<h1 style=\"margin:0 0 20px;font-size:24px;line-height:1.3;color:" + BRAND_INK + ";\">Hi " + escape(displayName(userName)) + ", we're sorry about this</h1>"
              + "<p style=\"margin:0 0 24px;font-size:15px;line-height:1.6;color:#333;\"><strong>" + escape(eventTitle) + "</strong> has been cancelled. Your booking (reference #" + bookingId + ") is affected.</p>"
              + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:" + BRAND_PAPER + ";border-radius:8px;border:1px solid #E4E1D8;margin-bottom:24px;\">"
              + "<tr><td style=\"padding:20px 24px;\">"
              + detailRow("Event", escape(eventTitle))
              + detailRow("Reason", escape(reason))
              + detailRow("Booking reference", "#" + bookingId)
              + "</td></tr></table>"
              + "<p style=\"margin:0;font-size:13px;line-height:1.6;color:" + BRAND_PAPER_MUTED + ";\">A refund for this booking will be processed to your original payment method. If you don't hear back within a few days, or have questions, please reply to this email.</p>";

        return htmlShell(eventTitle + " has been cancelled", body);
    }

    private String buildEventPostponedHtml(String userName, String eventTitle, Long bookingId,
                                            String oldDate, String newDate, String note) {
        String noteRow = (note == null || note.isBlank()) ? "" : detailRow("Note from the organizer", escape(note));

        String body =
                "<p style=\"margin:0 0 4px;font-size:13px;font-weight:bold;letter-spacing:1px;color:#8A6A2E;text-transform:uppercase;\">Event postponed</p>"
              + "<h1 style=\"margin:0 0 20px;font-size:24px;line-height:1.3;color:" + BRAND_INK + ";\">Hi " + escape(displayName(userName)) + ", the date has changed</h1>"
              + "<p style=\"margin:0 0 24px;font-size:15px;line-height:1.6;color:#333;\"><strong>" + escape(eventTitle) + "</strong> has a new date. Your booking (reference #" + bookingId + ") stays valid - no action needed from you.</p>"
              + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:" + BRAND_PAPER + ";border-radius:8px;border:1px solid #E4E1D8;margin-bottom:24px;\">"
              + "<tr><td style=\"padding:20px 24px;\">"
              + detailRow("Event", escape(eventTitle))
              + detailRow("Previous date", escape(oldDate))
              + detailRow("New date", escape(newDate))
              + noteRow
              + detailRow("Booking reference", "#" + bookingId)
              + "</td></tr></table>"
              + "<p style=\"margin:0;font-size:13px;line-height:1.6;color:" + BRAND_PAPER_MUTED + ";\">If the new date doesn't work for you, you can cancel from <strong>My Bookings</strong> in your StubLine account.</p>";

        return htmlShell(eventTitle + " has a new date", body);
    }

    private String detailRow(String label, String value) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:8px;\">"
                + "<tr>"
                + "<td style=\"font-size:13px;color:" + BRAND_PAPER_MUTED + ";padding:4px 0;width:140px;\">" + label + "</td>"
                + "<td style=\"font-size:14px;color:" + BRAND_INK + ";font-weight:bold;padding:4px 0;text-align:right;\">" + value + "</td>"
                + "</tr></table>";
    }

    private String formatAmount(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
