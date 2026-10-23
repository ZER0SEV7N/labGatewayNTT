package com.lab.clientes;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DataLoader {

    @Transactional
    void cargarDatos(@Observes StartupEvent event){
        if (Cliente.count() == 0) {
            crear("Ana Torres", "ana@correo.com");
            crear("Luis Pérez", "luis@correo.com");
        }
    }

    private void crear(String nombre, String email) {
        Cliente cliente = new Cliente();
        cliente.nombre = nombre;
        cliente.email = email;
        cliente.persist();
    }
}
