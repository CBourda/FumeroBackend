package com.fumero.controller;

import com.fumero.model.AppointmentRequest;
import com.fumero.model.ContactRequest;
import com.fumero.service.AppointmentService;
import com.fumero.service.MailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ContactController {

    private final MailService mailService;
    private final AppointmentService appointmentService;

    // ─── CONTATTO GENERALE ───
    @PostMapping("/contact")
    public ResponseEntity<Map<String, String>> contact(
            @Valid @RequestBody ContactRequest request) {

        log.info("Nuovo contatto da: {}", request.getEmail());
        mailService.sendContactEmail(request);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Messaggio inviato correttamente"
        ));
    }

    // ─── PRENOTAZIONE TELEVISITA ───
    @PostMapping("/appointment")
    public ResponseEntity<Map<String, String>> appointment(
            @Valid @RequestBody AppointmentRequest request) {

        if (!appointmentService.isPrenotazioneAperta()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "closed",
                    "message", "Le prenotazioni per questa settimana sono chiuse. Riprova martedì sera."
            ));
        }

        int slot = request.getSlot();
        if (slot != 1 && slot != 2) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Slot non valido."
            ));
        }

        boolean slotDisponibile = slot == 1
                ? appointmentService.isSlot1Disponibile()
                : appointmentService.isSlot2Disponibile();

        if (!slotDisponibile) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "slot_taken",
                    "message", "Lo slot selezionato è già occupato. Seleziona l'altro orario."
            ));
        }

        boolean prenotato = appointmentService.prenotaSlot(slot);
        if (!prenotato) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "slot_taken",
                    "message", "Lo slot selezionato è appena stato prenotato. Seleziona l'altro orario."
            ));
        }

        appointmentService.impostaDataTelevista(request);
        log.info("Nuova prenotazione televisita da: {} per: {} (slot {})",
                request.getEmail(), request.getDataTelevista(), slot);

        mailService.sendAppointmentEmails(request);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Richiesta ricevuta. Riceverà a breve una email con gli estremi per il pagamento."
        ));
    }

    // ─── SLOT DISPONIBILI ───
    @GetMapping("/appointment/slot")
    public ResponseEntity<Map<String, Object>> getSlot() {
        boolean aperta = appointmentService.isPrenotazioneAperta();
        LocalDate martedi = appointmentService.calcolaProssimoMartedi();

        List<Map<String, Object>> slots = new ArrayList<>();

        if (appointmentService.isSlot1Disponibile()) {
            slots.add(Map.of(
                    "slot", 1,
                    "ora", "18:00",
                    "label", appointmentService.formattaData(martedi, LocalTime.of(18, 0))
            ));
        }
        if (appointmentService.isSlot2Disponibile()) {
            slots.add(Map.of(
                    "slot", 2,
                    "ora", "18:30",
                    "label", appointmentService.formattaData(martedi, LocalTime.of(18, 30))
            ));
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("aperta", aperta);
        resp.put("slots", slots);
        resp.put("data", appointmentService.formattaData(appointmentService.calcolaProssimoMartediDateTime()));
        return ResponseEntity.ok(resp);
    }

    // ─── RESET SLOT (admin) ───
    @PostMapping("/admin/reset-slots")
    public ResponseEntity<Map<String, String>> resetSlots(
            @RequestHeader("X-Admin-Password") String password) {

        String adminPassword = System.getenv("ADMIN_PASSWORD");
        if (adminPassword == null || !adminPassword.equals(password)) {
            return ResponseEntity.status(403).body(Map.of(
                    "status", "error",
                    "message", "Non autorizzato."
            ));
        }

        appointmentService.resetSlots();
        log.info("Slot resettati via endpoint admin");
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Slot resettati correttamente."
        ));
    }

    // ─── VALIDAZIONE ERRORI ───
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
        log.warn("Errore validazione: {}", errors);
        return ResponseEntity.badRequest().body(errors);
    }
}