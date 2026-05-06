package com.fumero.service;

import com.fumero.model.AppointmentRequest;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AppointmentService {

    private static final DayOfWeek GIORNO_TELEVISITA = DayOfWeek.TUESDAY;
    private static final LocalTime SLOT1 = LocalTime.of(18, 0);
    private static final LocalTime SLOT2 = LocalTime.of(18, 30);
    private static final LocalTime ORA_CHIUSURA = LocalTime.of(23, 59);
    private static final DayOfWeek GIORNO_CHIUSURA = DayOfWeek.SUNDAY;

    // Key: "yyyy-MM-dd_18:00" o "yyyy-MM-dd_18:30" — true = occupato
    private final ConcurrentHashMap<String, Boolean> slotOccupati = new ConcurrentHashMap<>();

    private String slotKey(LocalDate martedi, LocalTime ora) {
        return martedi + "_" + ora;
    }

    public LocalDate calcolaProssimoMartedi() {
        LocalDateTime ora = LocalDateTime.now();
        LocalDate oggi = ora.toLocalDate();

        LocalDate martedi = oggi;
        while (martedi.getDayOfWeek() != GIORNO_TELEVISITA) {
            martedi = martedi.plusDays(1);
        }

        LocalDate domenica = martedi.minusDays(2);
        LocalDateTime chiusura = LocalDateTime.of(domenica, ORA_CHIUSURA);

        if (ora.isAfter(chiusura)) {
            martedi = martedi.plusWeeks(1);
        }

        return martedi;
    }

    public boolean isPrenotazioneAperta() {
        LocalDateTime ora = LocalDateTime.now();
        LocalDate martedi = calcolaProssimoMartedi();
        LocalDate domenica = martedi.minusDays(2);
        LocalDateTime chiusura = LocalDateTime.of(domenica, ORA_CHIUSURA);
        return ora.isBefore(chiusura);
    }

    public boolean isSlot1Disponibile() {
        return !slotOccupati.getOrDefault(slotKey(calcolaProssimoMartedi(), SLOT1), false);
    }

    public boolean isSlot2Disponibile() {
        return !slotOccupati.getOrDefault(slotKey(calcolaProssimoMartedi(), SLOT2), false);
    }

    public boolean prenotaSlot(int slot) {
        LocalDate martedi = calcolaProssimoMartedi();
        LocalTime ora = slot == 1 ? SLOT1 : SLOT2;
        String key = slotKey(martedi, ora);
        // putIfAbsent restituisce null se il key non esisteva → prenotazione riuscita
        return slotOccupati.putIfAbsent(key, true) == null;
    }

    public String formattaData(LocalDate martedi, LocalTime ora) {
        LocalDateTime dt = LocalDateTime.of(martedi, ora);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'alle' HH:mm", Locale.ITALIAN);
        return dt.format(formatter);
    }

    public void impostaDataTelevista(AppointmentRequest request) {
        LocalDate martedi = calcolaProssimoMartedi();
        LocalTime ora = request.getSlot() == 2 ? SLOT2 : SLOT1;
        request.setDataTelevista(formattaData(martedi, ora));
    }

    // Mantenuto per compatibilità con getSlot() senza slot specifico
    public LocalDateTime calcolaProssimoMartediDateTime() {
        return LocalDateTime.of(calcolaProssimoMartedi(), SLOT1);
    }

    public String formattaData(LocalDateTime data) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'alle' HH:mm", Locale.ITALIAN);
        return data.format(formatter);
    }
}