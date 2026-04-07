package com.fumero.service;

import com.fumero.model.AppointmentRequest;
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

    @Value("${mail.to}")
    private String mailTo;

    @Value("${mail.from}")
    private String mailFrom;

    @Value("${mail.iban}")
    private String iban;

    // ─────────────────────────────────────────
    // FORM CONTATTO GENERALE
    // ─────────────────────────────────────────

    public void sendContactEmail(ContactRequest req) {
        sendContactToDottore(req);
        sendContactConfirmToPaziente(req);
    }

    private void sendContactToDottore(ContactRequest req) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(mailFrom);
            h.setTo(mailTo);
            h.setSubject("[Sito Fumero] Nuovo messaggio da " + req.getNome());
            String body = """
                    <h2>Nuovo messaggio dal sito</h2>
                    <table style="font-family:Arial,sans-serif;font-size:14px">
                      <tr><td><b>Nome:</b></td><td>%s</td></tr>
                      <tr><td><b>Email:</b></td><td>%s</td></tr>
                      <tr><td><b>Telefono:</b></td><td>%s</td></tr>
                      <tr><td><b>Messaggio:</b></td><td>%s</td></tr>
                    </table>
                    """.formatted(req.getNome(), req.getEmail(),
                    req.getTelefono() != null ? req.getTelefono() : "—",
                    req.getMessaggio());
            h.setText(body, true);
            mailSender.send(msg);
            log.info("Email contatto inviata al dottore da: {}", req.getEmail());
        } catch (MessagingException e) {
            log.error("Errore invio email contatto: {}", e.getMessage());
            throw new RuntimeException("Errore invio email", e);
        }
    }

    private void sendContactConfirmToPaziente(ContactRequest req) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(mailFrom);
            h.setTo(req.getEmail());
            h.setSubject("Messaggio ricevuto — Dott. Andrea Fumero");
            String body = """
                    <p>Gentile %s,</p>
                    <p>Il suo messaggio è stato ricevuto. Le risponderemo entro 48 ore lavorative.</p>
                    <br>
                    <p>Cordiali saluti,<br><b>Dott. Andrea Fumero</b><br>Cardiochirurgo</p>
                    """.formatted(req.getNome());
            h.setText(body, true);
            mailSender.send(msg);
        } catch (MessagingException e) {
            log.warn("Errore conferma contatto a {}: {}", req.getEmail(), e.getMessage());
        }
    }

    // ─────────────────────────────────────────
    // PRENOTAZIONE TELEVISITA
    // ─────────────────────────────────────────

    public void sendAppointmentEmails(AppointmentRequest req) {
        sendIbanToPaziente(req);
        sendAppointmentNotifyToDottore(req);
    }

    private void sendIbanToPaziente(AppointmentRequest req) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(mailFrom);
            h.setTo(req.getEmail());
            h.setSubject("Richiesta televisita ricevuta — Dott. Andrea Fumero");
            String causale = "Televisita " + req.getNome() + " " + req.getDataTelevista();
            String body = """
                    <p>Gentile %s,</p>
                    <p>La sua richiesta di televisita è stata ricevuta.</p>
                    <p>Per confermare l'appuntamento, effettui un bonifico con i seguenti estremi:</p>
                    <table style="font-family:Arial,sans-serif;font-size:14px;border-collapse:collapse;margin:16px 0">
                      <tr style="background:#f5f5f5">
                        <td style="padding:8px 16px;color:#666">Intestato a</td>
                        <td style="padding:8px 16px"><b>Dott. Andrea Davide Fumero</b></td>
                      </tr>
                      <tr>
                        <td style="padding:8px 16px;color:#666">IBAN</td>
                        <td style="padding:8px 16px;font-family:monospace"><b>%s</b></td>
                      </tr>
                      <tr style="background:#f5f5f5">
                        <td style="padding:8px 16px;color:#666">Importo</td>
                        <td style="padding:8px 16px"><b>€ 200,00</b></td>
                      </tr>
                      <tr>
                        <td style="padding:8px 16px;color:#666">Causale</td>
                        <td style="padding:8px 16px">%s</td>
                      </tr>
                    </table>
                    <p>Dopo il pagamento, risponda a questa email allegando:</p>
                    <ul>
                      <li>La ricevuta del bonifico</li>
                      <li>Eventuali referti o esami diagnostici rilevanti</li>
                    </ul>
                    <p>Il Dott. Fumero confermerà l'appuntamento e le invierà il link per il collegamento.</p>
                    <p>Data prevista: <b>%s</b></p>
                    <br>
                    <p>Cordiali saluti,<br><b>Dott. Andrea Fumero</b><br>Cardiochirurgo</p>
                    """.formatted(req.getNome(), iban, causale, req.getDataTelevista());
            h.setText(body, true);
            mailSender.send(msg);
            log.info("Email IBAN inviata a: {}", req.getEmail());
        } catch (MessagingException e) {
            log.error("Errore invio email IBAN: {}", e.getMessage());
            throw new RuntimeException("Errore invio email", e);
        }
    }

    private void sendAppointmentNotifyToDottore(AppointmentRequest req) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(mailFrom);
            h.setTo(mailTo);
            h.setSubject("[Televisita] Nuova prenotazione — " + req.getNome()
                    + " — " + req.getDataTelevista());
            String body = """
                    <h2>Nuova richiesta di televisita</h2>
                    <p style="color:#e67e22"><b>⏳ In attesa di verifica pagamento</b></p>
                    <table style="font-family:Arial,sans-serif;font-size:14px;border-collapse:collapse;margin:16px 0">
                      <tr style="background:#f5f5f5">
                        <td style="padding:8px 16px;color:#666">Paziente</td>
                        <td style="padding:8px 16px"><b>%s</b></td>
                      </tr>
                      <tr>
                        <td style="padding:8px 16px;color:#666">Email</td>
                        <td style="padding:8px 16px">%s</td>
                      </tr>
                      <tr style="background:#f5f5f5">
                        <td style="padding:8px 16px;color:#666">Telefono</td>
                        <td style="padding:8px 16px">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px 16px;color:#666">Data</td>
                        <td style="padding:8px 16px"><b>%s</b></td>
                      </tr>
                      <tr style="background:#f5f5f5">
                        <td style="padding:8px 16px;color:#666">Motivo</td>
                        <td style="padding:8px 16px">%s</td>
                      </tr>
                    </table>
                    <p>Quando riceve la ricevuta del bonifico, confermi l'appuntamento
                    inviando al paziente il link Google Meet.</p>
                    """.formatted(req.getNome(), req.getEmail(), req.getTelefono(),
                    req.getDataTelevista(), req.getMotivo());
            h.setText(body, true);
            mailSender.send(msg);
            log.info("Notifica televisita inviata al dottore per: {}", req.getEmail());
        } catch (MessagingException e) {
            log.warn("Errore notifica televisita al dottore: {}", e.getMessage());
        }
    }
}