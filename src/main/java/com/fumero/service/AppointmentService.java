package com.fumero.service;

import com.fumero.model.AppointmentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final GoogleCalendarService googleCalendarService;

    private static final DayOfWeek GIORNO_TELEVISITA = DayOfWeek.TUESDAY;
    private static final LocalTime SLOT1 = LocalTime.of(18, 0);
    private static final LocalTime SLOT2 = LocalTime.of(18, 30);
    private static final LocalTime ORA_CHIUSURA = LocalTime.of(23, 59);

    // Mappa locale per race condition (due utenti che prenotano simultaneamente)
    private final ConcurrentHashMap<String, Boolean> slotOccupati = new ConcurrentHashMap<>();

    private String slotKey(LocalDate martedi, LocalTime ora) {
        return martedi + "_" + ora;
    }

    public LocalDate calcolaProssimoMartedi() {
        return googleCalendarService.calcolaProssimoMartedi();
    }

    public boolean isPrenotazioneAperta() {
        LocalDateTime ora = LocalDateTime.now();
        LocalDate martedi = calcolaProssimoMartedi();
        LocalDate domenica = martedi.minusDays(2);
        LocalDateTime chiusura = LocalDateTime.of(domenica, ORA_CHIUSURA);
        return ora.isBefore(chiusura);
    }

    public boolean isSlot1Disponibile() {
        LocalDate martedi = calcolaProssimoMartedi();
        if (slotOccupati.getOrDefault(slotKey(martedi, SLOT1), false)) return false;
        return !googleCalendarService.isSlotOccupato(martedi, SLOT1);
    }

    public boolean isSlot2Disponibile() {
        LocalDate martedi = calcolaProssimoMartedi();
        if (slotOccupati.getOrDefault(slotKey(martedi, SLOT2), false)) return false;
        return !googleCalendarService.isSlotOccupato(martedi, SLOT2);
    }

    public boolean prenotaSlot(int slot) {
        LocalDate martedi = calcolaProssimoMartedi();
        LocalTime ora = slot == 1 ? SLOT1 : SLOT2;
        String key = slotKey(martedi, ora);
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

    public LocalDateTime calcolaProssimoMartediDateTime() {
        return LocalDateTime.of(calcolaProssimoMartedi(), SLOT1);
    }

    public String formattaData(LocalDateTime data) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'alle' HH:mm", Locale.ITALIAN);
        return data.format(formatter);
    }

    public void resetSlots() {
        slotOccupati.clear();
    }

    public void resetSlot1() {
        slotOccupati.remove(slotKey(calcolaProssimoMartedi(), SLOT1));
    }

    public void resetSlot2() {
        slotOccupati.remove(slotKey(calcolaProssimoMartedi(), SLOT2));
    }
}