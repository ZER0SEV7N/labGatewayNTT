package com.lab.cuentas;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Map;

@Path("/cuentas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CuentaResource {

    @RestClient
    ClientesClient clientesClient;

    @GET
    public List<Cuenta> listar(@QueryParam("clienteId") Long clienteId){
        if(clienteId != null){
            return Cuenta.list("clienteId", clienteId);
        }

        return Cuenta.listAll();
    }

    @GET
    @Path("/{id}")
    public Cuenta obtener(@PathParam("id") Long id){
        Cuenta cuenta = Cuenta.findById(id);
        if(cuenta == null) {
            throw new NotFoundException("Cuenta " + id + " no existe");
        }

        return cuenta;
    }

    @POST
    @Transactional
    public Response crear(@Valid Cuenta cuenta){
        if(!clienteExiste(cuenta.clienteId)){
            return Response.status(422)
                    .entity(Map.of("error", "El cliente " + cuenta.clienteId + " no existe"))
                    .build();
        }
        cuenta.id = null;
        cuenta.persist();
        return Response.status(Response.Status.CREATED).entity(cuenta).build();
    }

    private boolean clienteExiste(Long clienteId) {
        try (Response respuesta = clientesClient.obtener(clienteId)) {
            return respuesta.getStatus() == 200;
        } catch (WebApplicationException e) {
            if(e.getResponse().getStatus() == 404){
                return false;
            }
            throw e;
        } catch (RuntimeException e) {
            //Clientes-api no responde (detenido, nombre no resuelve, timeout)
            throw new WebApplicationException(Response.status(503)
                    .entity(Map.of("error", "clientes-api no disponible"))
                    .build());
        }
    }
}
