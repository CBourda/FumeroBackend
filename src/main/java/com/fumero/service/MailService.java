package com.fumero.service;

import com.fumero.model.AppointmentRequest;
import com.fumero.model.ContactRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final AppointmentService appointmentService;

    @Value("${mail.to}")
    private String mailTo;

    @Value("${mail.from}")
    private String mailFrom;

    @Value("${mail.iban}")
    private String iban;

    @Value("${resend.api.key}")
    private String resendApiKey;

    private static final String RESEND_URL = "https://api.resend.com/emails";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private void sendEmail(String to, String subject, String html) {
        sendEmailWithReplyTo(to, subject, html, null);
    }

    private void sendEmailWithReplyTo(String to, String subject, String html, String replyTo) {
        String escapedHtml = html.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
        String escapedSubject = subject.replace("\"", "\\\"");
        String replyToPart = replyTo != null ? ",\"reply_to\":\"" + replyTo + "\"" : "";
        String jsonBody = "{\"from\":\"" + mailFrom + "\",\"to\":[\"" + to + "\"],\"subject\":\"" + escapedSubject + "\",\"html\":\"" + escapedHtml + "\"" + replyToPart + "}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_URL))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email inviata a {} — status {}", to, response.statusCode());
            } else {
                log.error("Errore Resend: {} — {}", response.statusCode(), response.body());
                throw new RuntimeException("Errore invio email: " + response.statusCode());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Errore HTTP Resend: {}", e.getMessage());
            throw new RuntimeException("Errore invio email", e);
        }
    }

    // --- CONTATTO GENERALE ---

    public void sendContactEmail(ContactRequest req) {
        sendContactToDottore(req);
        sendContactConfirmToPaziente(req);
    }

    private void sendContactToDottore(ContactRequest req) {
        String html = "<h2>Nuovo messaggio dal sito</h2>" +
                "<table style='font-family:Arial,sans-serif;font-size:14px'>" +
                "<tr><td><b>Nome:</b></td><td>" + req.getNome() + "</td></tr>" +
                "<tr><td><b>Email:</b></td><td>" + req.getEmail() + "</td></tr>" +
                "<tr><td><b>Telefono:</b></td><td>" + (req.getTelefono() != null ? req.getTelefono() : "—") + "</td></tr>" +
                "<tr><td><b>Messaggio:</b></td><td>" + req.getMessaggio() + "</td></tr>" +
                "</table>";
        sendEmailWithReplyTo(mailTo, "[Sito Fumero] Nuovo messaggio da " + req.getNome(), html, req.getEmail());
        log.info("Email contatto inviata al dottore da: {}", req.getEmail());
    }

    private void sendContactConfirmToPaziente(ContactRequest req) {
        String html = "<p>Gentile " + req.getNome() + ",</p>" +
                "<p>Il suo messaggio è stato ricevuto. Le risponderemo entro 48 ore lavorative.</p>" +
                "<br><p>Cordiali saluti,<br><b>Dott. Andrea Fumero</b><br>Cardiochirurgo</p>";
        try {
            sendEmail(req.getEmail(), "Messaggio ricevuto — Dott. Andrea Fumero", html);
        } catch (Exception e) {
            log.warn("Errore conferma contatto a {}: {}", req.getEmail(), e.getMessage());
        }
    }

    // --- PRENOTAZIONE TELEVISITA ---

    public void sendAppointmentEmails(AppointmentRequest req, String meetLink, String cancelLink) {
        sendIbanToPaziente(req, meetLink, cancelLink);
        sendAppointmentNotifyToDottore(req, meetLink);
    }

    private void sendIbanToPaziente(AppointmentRequest req, String meetLink, String cancelLink) {
        String causale = "Televisita — " + req.getNome() +
                " — CF: " + req.getCodiceFiscale() +
                " — " + req.getIndirizzo() +
                " — " + req.getDataTelevista();

        // Genera link "Aggiungi a Google Calendar"
        LocalDate martedi = appointmentService.calcolaProssimoMartedi();
        LocalTime ora = req.getSlot() == 2 ? LocalTime.of(18, 30) : LocalTime.of(18, 0);
        ZonedDateTime startUtc = LocalDateTime.of(martedi, ora)
                .atZone(ZoneId.of("Europe/Rome"))
                .withZoneSameInstant(ZoneId.of("UTC"));
        ZonedDateTime endUtc = startUtc.plusMinutes(30);
        DateTimeFormatter gcalFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        String gcalDates = startUtc.format(gcalFmt) + "/" + endUtc.format(gcalFmt);
        String meetDetails = meetLink != null
                ? "Link+Google+Meet:+" + meetLink.replace("://", "%3A%2F%2F").replace("/", "%2F")
                : "Televisita+con+Dott.+Andrea+Fumero";
        String gcalLink = "https://calendar.google.com/calendar/render?action=TEMPLATE" +
                "&text=Televisita+Dott.+Fumero" +
                "&dates=" + gcalDates +
                "&details=" + meetDetails +
                "&location=Google+Meet";

        String addToCalendarBtn = "<p style='margin:20px 0'>" +
                "<a href='" + gcalLink + "' style='background:#1a73e8;color:white;padding:12px 24px;" +
                "text-decoration:none;border-radius:4px;font-family:Arial,sans-serif;font-size:14px;display:inline-block'>" +
                "&#128197; Aggiungi a Google Calendar</a></p>";

        String meetBtn = meetLink != null
                ? "<p style='margin:8px 0'><a href='" + meetLink + "' style='background:#34a853;color:white;" +
                "padding:12px 24px;text-decoration:none;border-radius:4px;font-family:Arial,sans-serif;" +
                "font-size:14px;display:inline-block'>&#127909; Link Google Meet (provvisorio)</a></p>" +
                "<p style='font-size:12px;color:#888'>Il link diventerà definitivo dopo la conferma del pagamento da parte del Dott. Fumero.</p>"
                : "";

        String html = "<p>Gentile " + req.getNome() + ",</p>" +
                "<p>La sua richiesta di televisita è stata ricevuta.</p>" +
                "<p>Per confermare l'appuntamento, effettui un bonifico con i seguenti estremi:</p>" +
                "<table style='font-family:Arial,sans-serif;font-size:14px;border-collapse:collapse;margin:16px 0'>" +
                "<tr style='background:#f5f5f5'><td style='padding:8px 16px;color:#666'>Intestato a</td><td style='padding:8px 16px'><b>Dott. Andrea Davide Fumero</b></td></tr>" +
                "<tr><td style='padding:8px 16px;color:#666'>IBAN</td><td style='padding:8px 16px;font-family:monospace'><b>" + iban + "</b></td></tr>" +
                "<tr style='background:#f5f5f5'><td style='padding:8px 16px;color:#666'>Importo</td><td style='padding:8px 16px'><b>€ 200,00</b></td></tr>" +
                "<tr><td style='padding:8px 16px;color:#666'>Causale</td><td style='padding:8px 16px'>" + causale + "</td></tr>" +
                "</table>" +
                "<p>&#9888; <b>Dopo il pagamento</b>, invii la ricevuta del bonifico ed eventuali referti direttamente a: <b>andrea.fumero@humanitas.it</b></p>" +
                "<p>Il Dott. Fumero verificherà il pagamento e confermerà l'appuntamento.</p>" +
                "<p>Data prevista: <b>" + req.getDataTelevista() + "</b></p>" +
                addToCalendarBtn +
                meetBtn +
                "<p style='margin:20px 0'><a href='" + cancelLink + "' style='background:#e74c3c;color:white;padding:12px 24px;text-decoration:none;border-radius:4px;font-family:Arial,sans-serif;font-size:14px;display:inline-block'>❌ Annulla prenotazione</a></p>" +
                "<p style='font-size:12px;color:#888'>Il link di annullamento è valido per questa prenotazione.</p>" +
                "<br><p>Cordiali saluti,<br><b>Dott. Andrea Fumero</b><br>Cardiochirurgo</p>";

        sendEmail(req.getEmail(), "Richiesta televisita ricevuta — Dott. Andrea Fumero", html);
        log.info("Email IBAN inviata a: {}", req.getEmail());
    }

    private void sendAppointmentNotifyToDottore(AppointmentRequest req, String meetLink) {
        String meetHtml = meetLink != null
                ? "<p style='margin:16px 0'><a href='" + meetLink + "' style='background:#1a73e8;color:white;padding:10px 20px;text-decoration:none;border-radius:4px'>&#127909; Apri Google Meet</a></p>"
                : "<p style='color:#e74c3c'>&#9888; Link Meet non disponibile — crearlo manualmente.</p>";

        String html = "<h2>Nuova richiesta di televisita</h2>" +
                "<p style='color:#e67e22'><b>&#9203; In attesa di verifica pagamento</b></p>" +
                "<table style='font-family:Arial,sans-serif;font-size:14px;border-collapse:collapse;margin:16px 0'>" +
                "<tr style='background:#f5f5f5'><td style='padding:8px 16px;color:#666'>Paziente</td><td style='padding:8px 16px'><b>" + req.getNome() + "</b></td></tr>" +
                "<tr><td style='padding:8px 16px;color:#666'>Email</td><td style='padding:8px 16px'>" + req.getEmail() + "</td></tr>" +
                "<tr style='background:#f5f5f5'><td style='padding:8px 16px;color:#666'>Telefono</td><td style='padding:8px 16px'>" + req.getTelefono() + "</td></tr>" +
                "<tr><td style='padding:8px 16px;color:#666'>Data</td><td style='padding:8px 16px'><b>" + req.getDataTelevista() + "</b></td></tr>" +
                "<tr style='background:#f5f5f5'><td style='padding:8px 16px;color:#666'>Motivo</td><td style='padding:8px 16px'>" + req.getMotivo() + "</td></tr>" +
                "</table>" +
                "<p><b>Link Google Meet (tentativo — in attesa conferma pagamento):</b></p>" +
                meetHtml +
                "<p>Quando riceve la ricevuta del bonifico, accetti l'invito sul calendario per confermare l'appuntamento al paziente.</p>";

        try {
            sendEmailWithReplyTo(mailTo,
                    "[Televisita] Nuova prenotazione — " + req.getNome() + " — " + req.getDataTelevista(),
                    html, req.getEmail());
            log.info("Notifica televisita inviata al dottore per: {}", req.getEmail());
        } catch (Exception e) {
            log.warn("Errore notifica televisita al dottore: {}", e.getMessage());
        }
    }
}