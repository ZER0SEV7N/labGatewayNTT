package com.lab.cuentas;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "clientes")
@Path("/clientes")
public interface ClientesClient {

    @GET
    @Path("/{id}")
    Response obtener(@PathParam("id") Long id);
}
