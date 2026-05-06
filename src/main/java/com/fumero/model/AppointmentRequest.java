package com.fumero.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AppointmentRequest {

    @NotBlank(message = "Il nome è obbligatorio")
    @Size(max = 100)
    private String nome;

    @NotBlank(message = "L'email è obbligatoria")
    @Email(message = "Email non valida")
    private String email;

    @NotBlank(message = "Il telefono è obbligatorio")
    @Size(max = 20)
    private String telefono;

    @NotBlank(message = "Il codice fiscale è obbligatorio")
    @Size(min = 16, max = 16, message = "Il codice fiscale deve essere di 16 caratteri")
    private String codiceFiscale;

    @NotBlank(message = "L'indirizzo è obbligatorio")
    @Size(max = 200)
    private String indirizzo;

    @NotBlank(message = "Il motivo è obbligatorio")
    @Size(max = 2000, message = "Testo troppo lungo")
    private String motivo;

    private String dataTelevista;
}