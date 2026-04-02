package com.fumero.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactRequest {

    @NotBlank(message = "Il nome è obbligatorio")
    @Size(max = 100)
    private String nome;

    @NotBlank(message = "L'email è obbligatoria")
    @Email(message = "Email non valida")
    private String email;

    // Telefono opzionale
    @Size(max = 20)
    private String telefono;

    // A quale clinica si riferisce il messaggio
    // es. "Humanitas", "Visconti di Modrone", "Vigevano", "Cagliari"
    @NotBlank(message = "La clinica è obbligatoria")
    private String clinica;

    @NotBlank(message = "Il messaggio è obbligatorio")
    @Size(max = 2000, message = "Messaggio troppo lungo")
    private String messaggio;

    @Size(max = 255, message = "Nota troppo lunga")
    private String note;

}