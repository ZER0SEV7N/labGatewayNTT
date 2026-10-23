package com.lab.clientes;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

@Entity
public class Cliente extends PanacheEntity {
    public String nombre;
    public String email;
}
