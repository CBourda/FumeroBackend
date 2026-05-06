package com.fumero.service;

import com.fumero.model.AppointmentRequest;
import com.fumero.model.ContactRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Service
public class MailService {

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
        String jsonBody = "{\"from\":\"" + mailFrom + "\",\"to\":[\"" + to + "\"],\"subject\":\"" + subject.replace("\"", "\\\"") + "\",\"html\":\"" + html.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"}";

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

    public void sendContactEmail(ContactRequest req) {
        sendContactToDottore(req);
        sendContactConfirmToPaziente(req);
    }

    private void sendContactToDottore(ContactRequest req) {
        String html = "<h2>Nuovo messaggio dal sito</h2><table style='font-family:Arial,sans-serif;font-size:14px'><tr><td><b>Nome:</b></td><td>" + req.getNome() + "</td></tr><tr><td><b>Email:</b></td><td>" + req.getEmail() + "</td></tr><tr><td><b>Telefono:</b></td><td>" + (req.getTelefono() != null ? req.getTelefono() : "—") + "</td></tr><tr><td><b>Messaggio:</b></td><td>" + req.getMessaggio() + "</td></tr></table>";
        sendEmail(mailTo, "[Sito Fumero] Nuovo messaggio da " + req.getNome(), html);
        log.info("Email contatto inviata al dottore da: {}", req.getEmail());
    }

    private void sendContactConfirmToPaziente(ContactRequest req) {
        String html = "<p>Gentile " + req.getNome() + ",</p><p>Il suo messaggio è stato ricevuto. Le risponderemo entro 48 ore lavorative.</p><br><p>Cordiali saluti,<br><b>Dott. Andrea Fumero</b><br>Cardiochirurgo</p>";
        try {
            sendEmail(req.getEmail(), "Messaggio ricevuto — Dott. Andrea Fumero", html);
        } catch (Exception e) {
            log.warn("Errore conferma contatto a {}: {}", req.getEmail(), e.getMessage());
        }
    }

    public void sendAppointmentEmails(AppointmentRequest req) {
        sendIbanToPaziente(req);
        sendAppointmentNotifyToDottore(req);
    }

    private void sendIbanToPaziente(AppointmentRequest req) {
        String causale = "Televisita — " + req.getNome() + " — CF: " + req.getCodiceFiscale() + " — " + req.getIndirizzo() + " — " + req.getDataTelevista();
        String html = "<p>Gentile " + req.getNome() + ",</p><p>La sua richiesta di televisita è stata ricevuta.</p><p>Per confermare l'appuntamento, effettui un bonifico con i seguenti estremi:</p><table style='font-family:Arial,sans-serif;font-size:14px;border-collapse:collapse;margin:16px 0'><tr style='background:#f5f5f5'><td style='padding:8px 16px;color:#666'>Intestato a</td><td style='padding:8px 16px'><b>Dott. Andrea Davide Fumero</b></td></tr><tr><td style='padding:8px 16px;color:#666'>IBAN</td><td style='padding:8px 16px;font-family:monospace'><b>" + iban + "</b></td></tr><tr style='background:#f5f5f5'><td style='padding:8px 16px;color:#666'>Importo</td><td style='padding:8px 16px'><b>€ 200,00</b></td></tr><tr><td style='padding:8px 16px;color:#666'>Causale</td><td style='padding:8px 16px'>" + causale + "</td></tr></table><p>Dopo il pagamento, risponda a questa email allegando la ricevuta del bonifico ed eventuali referti.</p><p>Il Dott. Fumero confermerà l'appuntamento e le invierà il link Google Meet.</p><p>Data prevista: <b>" + req.getDataTelevista() + "</b></p><br><p>Cordiali saluti,<br><b>Dott. Andrea Fumero</b><br>Cardiochirurgo</p>";
        sendEmail(req.getEmail(), "Richiesta televisita ricevuta — Dott. Andrea Fumero", html);
        log.info("Email IBAN inviata a: {}", req.getEmail());
    }

    private void sendAppointmentNotifyToDottore(AppointmentRequest req) {
        String html = "<h2>Nuova richiesta di televisita</h2><p style='color:#e67e22'><b>⏳ In attesa di verifica pagamento</b></p><table style='font-family:Arial,sans-serif;font-size:14px;border-collapse:collapse;margin:16px 0'><tr style='background:#f5f5f5'><td style='padding:8px 16px;color:#666'>Paziente</td><td style='padding:8px 16px'><b>" + req.getNome() + "</b></td></tr><tr><td style='padding:8px 16px;color:#666'>Email</td><td style='padding:8px 16px'>" + req.getEmail() + "</td></tr><tr style='background:#f5f5f5'><td style='padding:8px 16px;color:#666'>Telefono</td><td style='padding:8px 16px'>" + req.getTelefono() + "</td></tr><tr><td style='padding:8px 16px;color:#666'>Data</td><td style='padding:8px 16px'><b>" + req.getDataTelevista() + "</b></td></tr><tr style='background:#f5f5f5'><td style='padding:8px 16px;color:#666'>Motivo</td><td style='padding:8px 16px'>" + req.getMotivo() + "</td></tr></table><p>Quando riceve la ricevuta del bonifico, confermi l'appuntamento inviando al paziente il link Google Meet.</p>";
        try {
            sendEmail(mailTo, "[Televisita] Nuova prenotazione — " + req.getNome() + " — " + req.getDataTelevista(), html);
            log.info("Notifica televisita inviata al dottore per: {}", req.getEmail());
        } catch (Exception e) {
            log.warn("Errore notifica televisita al dottore: {}", e.getMessage());
        }
    }
}