package com.lab.cuentas;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Entity
public class Cuenta extends PanacheEntity {

    @NotNull(message = "clienteId es obligatorio")
    public Long clienteId;

    @NotNull(message = "moneda es obligatorio")
    @Pattern(regexp = "PEN|USD", message = "moneda debe ser PEN o USD")
    public String moneda;

    @NotNull(message = "saldo es obligatorio")
    @PositiveOrZero(message = "saldo no puede ser negativo")
    public BigDecimal saldo;
}
