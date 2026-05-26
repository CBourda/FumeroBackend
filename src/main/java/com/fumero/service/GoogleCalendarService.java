package com.fumero.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import com.fumero.model.AppointmentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarService {

    private final AppointmentService appointmentService;

    @Value("${google.client.id}")
    private String clientId;

    @Value("${google.client.secret}")
    private String clientSecret;

    @Value("${google.refresh.token}")
    private String refreshToken;

    @Value("${google.calendar.id}")
    private String calendarId;

    private Calendar buildCalendarService() throws Exception {
        var credentials = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(refreshToken)
                .build();

        return new Calendar.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        ).setApplicationName("Fumero Backend Calendar").build();
    }

    public String createTentativeEvent(AppointmentRequest request) {
        try {
            Calendar service = buildCalendarService();

            // Calcola LocalDateTime dall'slot
            LocalDate martedi = appointmentService.calcolaProssimoMartedi();
            LocalTime ora = request.getSlot() == 2
                    ? LocalTime.of(18, 30)
                    : LocalTime.of(18, 0);
            LocalDateTime start = LocalDateTime.of(martedi, ora);
            LocalDateTime end = start.plusMinutes(30);

            // Formato ISO con offset Roma (+02:00 estate, +01:00 inverno)
            ZoneId zonaRoma = ZoneId.of("Europe/Rome");
            ZonedDateTime startZoned = start.atZone(zonaRoma);
            ZonedDateTime endZoned = end.atZone(zonaRoma);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

            EventDateTime startDt = new EventDateTime()
                    .setDateTime(new DateTime(startZoned.format(fmt)))
                    .setTimeZone("Europe/Rome");

            EventDateTime endDt = new EventDateTime()
                    .setDateTime(new DateTime(endZoned.format(fmt)))
                    .setTimeZone("Europe/Rome");

            Event event = new Event()
                    .setSummary("Televisita — " + request.getNome())
                    .setDescription(
                            "Paziente: " + request.getNome() + "\n" +
                                    "Email: " + request.getEmail() + "\n" +
                                    "Telefono: " + request.getTelefono() + "\n" +
                                    "CF: " + request.getCodiceFiscale() + "\n" +
                                    "Indirizzo: " + request.getIndirizzo() + "\n\n" +
                                    "⚠ In attesa di conferma pagamento"
                    )
                    .setStart(startDt)
                    .setEnd(endDt)
                    .setStatus("tentative")
                    .setConferenceData(new ConferenceData()
                            .setCreateRequest(new CreateConferenceRequest()
                                    .setRequestId("fumero-" + System.currentTimeMillis())
                                    .setConferenceSolutionKey(new ConferenceSolutionKey()
                                            .setType("hangoutsMeet"))));

            Event created = service.events()
                    .insert(calendarId, event)
                    .setConferenceDataVersion(1)
                    .execute();

            String meetLink = created.getConferenceData()
                    .getEntryPoints().stream()
                    .filter(e -> "video".equals(e.getEntryPointType()))
                    .findFirst()
                    .map(EntryPoint::getUri)
                    .orElse(null);

            log.info("Evento creato: {} — Meet: {}", created.getId(), meetLink);
            return meetLink;

        } catch (Exception e) {
            log.error("Errore creazione evento Google Calendar: {}", e.getMessage(), e);
            return null;
        }
    }
}