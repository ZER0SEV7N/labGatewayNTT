package com.lab.cuentas;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DataLoader {

    @Transactional
    void cargarDatos(@Observes StartupEvent event){
        if(Cuenta.count() == 0){
            crear(1L, "PEN", "1500.00");
            crear(1L, "USD", "250.00");
            crear(2L, "PEN", "800.00");
        }
    }

    private void crear(Long clienteId, String moneda, String saldo){
        Cuenta cuenta = new Cuenta();
        cuenta.clienteId = clienteId;
        cuenta.moneda = moneda;
        cuenta.saldo = new java.math.BigDecimal(saldo);
        cuenta.persist();
    }
}
