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

import java.util.HashMap;
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

        // Verifica se le prenotazioni sono aperte
        if (!appointmentService.isPrenotazioneAperta()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "closed",
                    "message", "Le prenotazioni per questa settimana sono chiuse. Riprova martedì sera."
            ));
        }

        // Calcola e imposta la data televisita
        appointmentService.impostaDataTelevista(request);
        log.info("Nuova prenotazione televisita da: {} per: {}",
                request.getEmail(), request.getDataTelevista());

        // Invia email IBAN al paziente + notifica al dottore
        mailService.sendAppointmentEmails(request);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Richiesta ricevuta. Riceverà a breve una email con gli estremi per il pagamento."
        ));
    }

    // ─── SLOT DISPONIBILE (usato dal frontend per mostrare la data) ───
    @GetMapping("/appointment/slot")
    public ResponseEntity<Map<String, Object>> getSlot() {
        boolean aperta = appointmentService.isPrenotazioneAperta();
        String data = appointmentService.formattaData(
                appointmentService.calcolaProssimoMartedi());

        Map<String, Object> resp = new HashMap<>();
        resp.put("aperta", aperta);
        resp.put("data", data);
        return ResponseEntity.ok(resp);
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