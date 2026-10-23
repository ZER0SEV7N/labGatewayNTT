package com.lab.clientes;
 
import io.quarkus.logging.Log;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
 
@Path("/clientes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClienteResource {
 
    @GET
    public List<Cliente> listar(@HeaderParam("X-Consumer-Username") String consumidor,
                                @HeaderParam("X-Correlation-ID") String correlationId) {
        Log.infof("GET /clientes | consumidor=%s | correlationId=%s", consumidor, correlationId);
        return Cliente.listAll();
    }
 
    @GET
    @Path("/{id}")
    public Cliente obtener(@PathParam("id") Long id) {
        return buscar(id);
    }
 
    @POST
    @Transactional
    public Response crear(Cliente cliente) {
        cliente.id = null;
        cliente.persist();
        return Response.status(Response.Status.CREATED).entity(cliente).build();
    }
 
    @PUT
    @Path("/{id}")
    @Transactional
    public Cliente actualizar(@PathParam("id") Long id, Cliente datos) {
        Cliente cliente = buscar(id);
        cliente.nombre = datos.nombre;
        cliente.email = datos.email;
        return cliente;
    }
 
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response eliminar(@PathParam("id") Long id) {
        if (!Cliente.deleteById(id)) {
            throw new NotFoundException("Cliente " + id + " no existe");
        }
        return Response.noContent().build();
    }
 
    private Cliente buscar(Long id) {
        Cliente cliente = Cliente.findById(id);
        if (cliente == null) {
            throw new NotFoundException("Cliente " + id + " no existe");
        }
        return cliente;
    }
}
