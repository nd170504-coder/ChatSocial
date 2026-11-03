package com.chatsocial.service;

import com.chatsocial.exception.ApiException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender sender;
    private final String from;
    private final String template;

    public EmailService(JavaMailSender sender, @Value("${app.mail.from}") String from) throws IOException {
        this.sender = sender;
        this.from = from;
        this.template = new ClassPathResource("templates/mail/email-verification.html")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    public void sendVerificationEmail(String email, String otp, long ttlSeconds) {
        long minutes = (ttlSeconds + 59) / 60;
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from, "ChatSocial");
            helper.setTo(email);
            helper.setSubject("ChatSocial - Xác minh tài khoản");
            String html = template.replace("{{OTP}}", otp).replace("{{MINUTES}}", Long.toString(minutes));
            String plain = "Mã xác minh ChatSocial của bạn: " + otp + ". Mã có hiệu lực " + minutes + " phút.";
            helper.setText(plain, html);
            sender.send(message);
        } catch (MessagingException | IOException | MailException exception) {
            // SMTP exceptions may contain credentials or message contents; do not log them.
            log.warn("Verification email delivery failed ({})", exception.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to send verification email; please request a new code later");
        }
    }

    public void sendPasswordResetEmail(String email, String otp, long ttlSeconds) {
        long minutes = (ttlSeconds + 59) / 60;
        sendPasswordEmail(email, "ChatSocial - Đặt lại mật khẩu",
                "Mã đặt lại mật khẩu ChatSocial của bạn: " + otp + ". Mã có hiệu lực "
                        + minutes + " phút và chỉ sử dụng một lần. Không chia sẻ mã này với người khác. "
                        + "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.");
    }

    public void sendPasswordChangedEmail(String email) {
        sendPasswordEmail(email, "ChatSocial - Mật khẩu đã thay đổi",
                "Mật khẩu ChatSocial của bạn đã được thay đổi. Tất cả phiên đăng nhập đã bị thu hồi. "
                        + "Nếu bạn không thực hiện thao tác này, hãy đặt lại mật khẩu ngay.");
    }

    private void sendPasswordEmail(String email, String subject, String text) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from, "ChatSocial");
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(text);
            sender.send(message);
        } catch (MessagingException | IOException | MailException exception) {
            // Do not expose account existence on forgot-password or fail an already committed password change.
            log.warn("Password email delivery failed ({})", exception.getClass().getSimpleName());
        }
    }

}
