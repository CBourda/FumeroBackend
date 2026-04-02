package com.fumero.service;

import com.fumero.model.ContactRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    // Indirizzo del dottore — impostato in application.properties
    @Value("${mail.to}")
    private String mailTo;

    // Indirizzo mittente verificato su Brevo
    @Value("${mail.from}")
    private String mailFrom;

    /**
     * Invia al dottore la richiesta di contatto ricevuta dal sito.
     * Invia anche una email di conferma al paziente.
     */
    public void sendContactEmail(ContactRequest req) {
        sendToDottore(req);
        sendConfirmToPaziente(req);
    }

    // --- Email al dottore ---
    private void sendToDottore(ContactRequest req) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(mailTo);
            helper.setSubject("[Sito Fumero] Nuova richiesta da " + req.getNome()
                    + " — " + req.getClinica());

            String body = """
                    <h2>Nuova richiesta di contatto</h2>
                    <table style="font-family: Arial, sans-serif; font-size: 14px;">
                        <tr><td><b>Nome:</b></td><td>%s</td></tr>
                        <tr><td><b>Email:</b></td><td>%s</td></tr>
                        <tr><td><b>Telefono:</b></td><td>%s</td></tr>
                        <tr><td><b>Clinica:</b></td><td>%s</td></tr>
                        <tr><td><b>Messaggio:</b></td><td>%s</td></tr>
                    </table>
                    """.formatted(
                    req.getNome(),
                    req.getEmail(),
                    req.getTelefono() != null ? req.getTelefono() : "non fornito",
                    req.getClinica(),
                    req.getMessaggio()
            );

            helper.setText(body, true);
            mailSender.send(message);
            log.info("Email inviata al dottore per richiesta da: {}", req.getEmail());

        } catch (MessagingException e) {
            log.error("Errore invio email al dottore: {}", e.getMessage());
            throw new RuntimeException("Errore nell'invio dell'email", e);
        }
    }

    // --- Email di conferma al paziente ---
    private void sendConfirmToPaziente(ContactRequest req) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(req.getEmail());
            helper.setSubject("Richiesta ricevuta — Dott. Andrea Fumero");

            String body = """
                    <p>Gentile %s,</p>
                    <p>La sua richiesta è stata ricevuta correttamente.<br>
                    Il Dott. Fumero la contatterà al più presto.</p>
                    <p>Riepilogo della sua richiesta:</p>
                    <blockquote style="border-left: 3px solid #ccc; padding-left: 12px; color: #555;">
                        %s
                    </blockquote>
                    <br>
                    <p>Cordiali saluti,<br>
                    <b>Dott. Andrea Fumero</b><br>
                    Cardiochirurgo</p>
                    """.formatted(req.getNome(), req.getMessaggio());

            helper.setText(body, true);
            mailSender.send(message);
            log.info("Email di conferma inviata a: {}", req.getEmail());

        } catch (MessagingException e) {
            // Non blocchiamo il flusso se la conferma fallisce
            log.warn("Errore invio conferma al paziente {}: {}", req.getEmail(), e.getMessage());
        }
    }
}