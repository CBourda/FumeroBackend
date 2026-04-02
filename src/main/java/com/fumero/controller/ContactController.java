package com.fumero.controller;

import com.fumero.model.ContactRequest;
import com.fumero.service.MailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ContactController {

    private final MailService mailService;

    /**
     * Riceve il form di contatto dal frontend React.
     * Valida i dati con @Valid, poi delega l'invio email al MailService.
     */
    @PostMapping("/contact")
    public ResponseEntity<Map<String, String>> contact(
            @Valid @RequestBody ContactRequest request) {

        log.info("Nuova richiesta di contatto da: {} — clinica: {}",
                request.getEmail(), request.getClinica());

        mailService.sendContactEmail(request);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Richiesta inviata correttamente"
        ));
    }

    /**
     * Riceve la richiesta di appuntamento.
     * Per ora usa lo stesso DTO e lo stesso flusso email.
     * In futuro qui si aggancerà Google Calendar.
     */
    @PostMapping("/appointment")
    public ResponseEntity<Map<String, String>> appointment(
            @Valid @RequestBody ContactRequest request) {

        log.info("Nuova richiesta di appuntamento da: {} — clinica: {}",
                request.getEmail(), request.getClinica());

        mailService.sendContactEmail(request);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Richiesta di appuntamento inviata correttamente"
        ));
    }

    /**
     * Gestisce gli errori di validazione (@Valid).
     * Restituisce un 400 con i campi che non passano la validazione.
     */
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {

        Map<String, String> errors = new java.util.HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));

        log.warn("Errore validazione form: {}", errors);
        return ResponseEntity.badRequest().body(errors);
    }
}