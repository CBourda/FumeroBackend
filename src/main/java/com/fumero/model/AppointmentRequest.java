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

    @NotBlank(message = "Il motivo è obbligatorio")
    @Size(max = 2000, message = "Testo troppo lungo")
    private String motivo;

    // Data televisita calcolata lato backend — non serve dal frontend
    // Viene impostata automaticamente dal controller
    private String dataTelevista;
}