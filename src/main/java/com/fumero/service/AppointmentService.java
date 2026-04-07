package com.fumero.service;

import com.fumero.model.AppointmentRequest;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
public class AppointmentService {

    // Televisita sempre il martedì alle 18:00
    private static final DayOfWeek GIORNO_TELEVISITA = DayOfWeek.TUESDAY;
    private static final LocalTime ORA_TELEVISITA = LocalTime.of(18, 0);

    // Prenotazioni chiuse dalla domenica a mezzanotte (2 giorni prima del martedì)
    private static final DayOfWeek GIORNO_CHIUSURA = DayOfWeek.SUNDAY;
    private static final LocalTime ORA_CHIUSURA = LocalTime.of(23, 59);

    /**
     * Calcola il prossimo martedì disponibile alle 18:00.
     * Se oggi è dopo domenica mezzanotte, salta al martedì della settimana successiva.
     */
    public LocalDateTime calcolaProssimoMartedi() {
        LocalDateTime ora = LocalDateTime.now();
        LocalDate oggi = ora.toLocalDate();

        // Trova il prossimo martedì
        LocalDate martedi = oggi;
        while (martedi.getDayOfWeek() != GIORNO_TELEVISITA) {
            martedi = martedi.plusDays(1);
        }

        // Controlla se le prenotazioni sono chiuse (dopo domenica mezzanotte)
        LocalDate domenicaPrecedente = martedi.minusDays(2);
        LocalDateTime chiusura = LocalDateTime.of(domenicaPrecedente, ORA_CHIUSURA);

        if (ora.isAfter(chiusura)) {
            // Passa al martedì successivo
            martedi = martedi.plusWeeks(1);
        }

        return LocalDateTime.of(martedi, ORA_TELEVISITA);
    }

    /**
     * Verifica se le prenotazioni sono aperte in questo momento.
     */
    public boolean isPrenotazioneAperta() {
        LocalDateTime ora = LocalDateTime.now();
        LocalDateTime prossimoMartedi = calcolaProssimoMartedi();

        // Domenica a mezzanotte prima del martedì
        LocalDate domenicaChiusura = prossimoMartedi.toLocalDate().minusDays(2);
        LocalDateTime chiusura = LocalDateTime.of(domenicaChiusura, ORA_CHIUSURA);

        return ora.isBefore(chiusura);
    }

    /**
     * Formatta la data per le email — es: "martedì 15 aprile 2025 alle 18:00"
     */
    public String formattaData(LocalDateTime data) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'alle' HH:mm",
                java.util.Locale.ITALIAN);
        return data.format(formatter);
    }

    /**
     * Imposta la data televisita nella request.
     */
    public void impostaDataTelevista(AppointmentRequest request) {
        LocalDateTime martedi = calcolaProssimoMartedi();
        request.setDataTelevista(formattaData(martedi));
    }
}