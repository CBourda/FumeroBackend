package com.fumero.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import com.fumero.model.AppointmentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GoogleCalendarService {

    private static final LocalTime SLOT1 = LocalTime.of(18, 0);
    private static final LocalTime SLOT2 = LocalTime.of(18, 30);
    private static final LocalTime ORA_CHIUSURA = LocalTime.of(23, 59);
    private static final DayOfWeek GIORNO_TELEVISITA = DayOfWeek.TUESDAY;

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

    // ─── Calcolo prossimo martedì (autonomo, no dipendenza da AppointmentService) ───
    public LocalDate calcolaProssimoMartedi() {
        LocalDate oggi = LocalDate.now();

        LocalDate martedi = oggi;
        while (martedi.getDayOfWeek() != GIORNO_TELEVISITA) {
            martedi = martedi.plusDays(1);
        }

        // Se il martedì trovato è oggi o già passato, prendi il prossimo
        if (!martedi.isAfter(oggi)) {
            martedi = martedi.plusWeeks(1);
        }

        return martedi;
    }

    // ─── Verifica slot via Freebusy API ───
    public boolean isSlotOccupato(LocalDate martedi, LocalTime ora) {
        try {
            Calendar service = buildCalendarService();

            ZoneId zonaRoma = ZoneId.of("Europe/Rome");
            ZonedDateTime start = LocalDateTime.of(martedi, ora).atZone(zonaRoma);
            ZonedDateTime end = start.plusMinutes(30);

            FreeBusyRequest fbRequest = new FreeBusyRequest()
                    .setTimeMin(new DateTime(start.toInstant().toEpochMilli()))
                    .setTimeMax(new DateTime(end.toInstant().toEpochMilli()))
                    .setItems(List.of(new FreeBusyRequestItem().setId(calendarId)));

            FreeBusyResponse response = service.freebusy().query(fbRequest).execute();

            List<TimePeriod> busy = response.getCalendars().get(calendarId).getBusy();
            boolean occupato = busy != null && !busy.isEmpty();
            log.info("Slot {}/{} — occupato: {}", martedi, ora, occupato);
            return occupato;

        } catch (Exception e) {
            log.error("Errore verifica slot Calendar: {}", e.getMessage());
            return false;
        }
    }

    // ─── Creazione evento tentativo con Meet ───
    public String createTentativeEvent(AppointmentRequest request) {
        try {
            Calendar service = buildCalendarService();

            LocalDate martedi = calcolaProssimoMartedi();
            LocalTime ora = request.getSlot() == 2 ? SLOT2 : SLOT1;
            LocalDateTime start = LocalDateTime.of(martedi, ora);
            LocalDateTime end = start.plusMinutes(30);

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

            List<EventAttendee> attendees = List.of(
                    new EventAttendee()
                            .setEmail(calendarId)
                            .setOrganizer(true)
                            .setResponseStatus("tentative"),
                    new EventAttendee()
                            .setEmail(request.getEmail())
                            .setResponseStatus("needsAction")
            );

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
                    .setAttendees(attendees)
                    .setConferenceData(new ConferenceData()
                            .setCreateRequest(new CreateConferenceRequest()
                                    .setRequestId("fumero-" + System.currentTimeMillis())
                                    .setConferenceSolutionKey(new ConferenceSolutionKey()
                                            .setType("hangoutsMeet"))));

            Event created = service.events()
                    .insert(calendarId, event)
                    .setConferenceDataVersion(1)
                    .setSendUpdates("all")
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

    public String createTentativeEventAndReturnId(AppointmentRequest request) {
        try {
            Calendar service = buildCalendarService();

            LocalDate martedi = calcolaProssimoMartedi();
            LocalTime ora = request.getSlot() == 2 ? SLOT2 : SLOT1;
            LocalDateTime start = LocalDateTime.of(martedi, ora);
            LocalDateTime end = start.plusMinutes(30);

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

            List<EventAttendee> attendees = List.of(
                    new EventAttendee()
                            .setEmail(calendarId)
                            .setOrganizer(true)
                            .setResponseStatus("tentative"),
                    new EventAttendee()
                            .setEmail(request.getEmail())
                            .setResponseStatus("needsAction")
            );

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
                    .setAttendees(attendees)
                    .setConferenceData(new ConferenceData()
                            .setCreateRequest(new CreateConferenceRequest()
                                    .setRequestId("fumero-" + System.currentTimeMillis())
                                    .setConferenceSolutionKey(new ConferenceSolutionKey()
                                            .setType("hangoutsMeet"))));

            Event created = service.events()
                    .insert(calendarId, event)
                    .setConferenceDataVersion(1)
                    .setSendUpdates("all")
                    .execute();

            log.info("Evento creato con ID: {}", created.getId());
            return created.getId();

        } catch (Exception e) {
            log.error("Errore creazione evento: {}", e.getMessage(), e);
            return null;
        }
    }

    public boolean deleteEvent(String eventId) {
        try {
            Calendar service = buildCalendarService();
            service.events().delete(calendarId, eventId).setSendUpdates("all").execute();
            log.info("Evento eliminato: {}", eventId);
            return true;
        } catch (Exception e) {
            log.error("Errore eliminazione evento {}: {}", eventId, e.getMessage());
            return false;
        }
    }

    public String getMeetLinkFromEvent(String eventId) {
        if (eventId == null) return null;
        try {
            Calendar service = buildCalendarService();
            Event event = service.events().get(calendarId, eventId).execute();
            if (event.getConferenceData() == null) return null;
            return event.getConferenceData().getEntryPoints().stream()
                    .filter(e -> "video".equals(e.getEntryPointType()))
                    .findFirst()
                    .map(EntryPoint::getUri)
                    .orElse(null);
        } catch (Exception e) {
            log.error("Errore recupero Meet link: {}", e.getMessage());
            return null;
        }
    }

    public List<Map<String, String>> getUpcomingEvents() {
        try {
            Calendar service = buildCalendarService();

            LocalDate martedi = calcolaProssimoMartedi();
            ZoneId zonaRoma = ZoneId.of("Europe/Rome");
            ZonedDateTime startOfDay = martedi.atStartOfDay(zonaRoma);
            ZonedDateTime endOfDay = martedi.atTime(23, 59).atZone(zonaRoma);

            Events events = service.events().list(calendarId)
                    .setTimeMin(new DateTime(startOfDay.toInstant().toEpochMilli()))
                    .setTimeMax(new DateTime(endOfDay.toInstant().toEpochMilli()))
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();

            return events.getItems().stream()
                    .map(e -> Map.of(
                            "id", e.getId(),
                            "titolo", e.getSummary() != null ? e.getSummary() : "—",
                            "data", e.getStart().getDateTime() != null
                                    ? e.getStart().getDateTime().toString()
                                    : e.getStart().getDate().toString(),
                            "status", e.getStatus() != null ? e.getStatus() : "—"
                    ))
                    .collect(java.util.stream.Collectors.toList());

        } catch (Exception e) {
            log.error("Errore recupero eventi: {}", e.getMessage());
            return List.of();
        }
    }

    public int deleteUpcomingEvents() {
        try {
            Calendar service = buildCalendarService();

            LocalDate martedi = calcolaProssimoMartedi();
            ZoneId zonaRoma = ZoneId.of("Europe/Rome");
            ZonedDateTime startOfDay = martedi.atStartOfDay(zonaRoma);
            ZonedDateTime endOfDay = martedi.atTime(23, 59).atZone(zonaRoma);

            Events events = service.events().list(calendarId)
                    .setTimeMin(new DateTime(startOfDay.toInstant().toEpochMilli()))
                    .setTimeMax(new DateTime(endOfDay.toInstant().toEpochMilli()))
                    .setSingleEvents(true)
                    .execute();

            int count = 0;
            for (Event event : events.getItems()) {
                try {
                    service.events().delete(calendarId, event.getId())
                            .setSendUpdates("all")
                            .execute();
                    log.info("Evento eliminato: {}", event.getId());
                    count++;
                } catch (Exception e) {
                    log.warn("Errore eliminazione evento {}: {}", event.getId(), e.getMessage());
                }
            }
            return count;

        } catch (Exception e) {
            log.error("Errore deleteUpcomingEvents: {}", e.getMessage());
            return 0;
        }
    }
}