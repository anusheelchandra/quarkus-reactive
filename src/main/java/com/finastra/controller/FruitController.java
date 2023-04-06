package com.finastra.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.finastra.entity.Fruit;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.panache.common.Sort;
import io.smallrye.common.constraint.NotNull;
import io.smallrye.mutiny.CompositeException;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestPath;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.List;

import static javax.ws.rs.core.Response.*;

@Path("/fruits")
@ApplicationScoped
public class FruitController {

  static final Logger LOGGER = Logger.getLogger(FruitController.class.getName());

  @GET
  public Uni<List<Fruit>> get() {
    return Fruit.listAll(Sort.by("name"));
  }

  @GET
  @Path("{id}")
  public Uni<Fruit> getSingle(@RestPath Long id) {
    return Fruit.findById(id);
  }

  @POST
  public Uni<Response> create(@NotNull Fruit fruit) {
    if (fruit == null || fruit.id != null) {
      throw new WebApplicationException("Id was invalidly set on request.", 422);
    }

    return Panache.withTransaction(fruit::persist)
        .replaceWith(ok(fruit).status(Status.CREATED)::build);
  }

  @PUT
  @Path("{id}")
  public Uni<Response> update(@RestPath Long id, Fruit fruit) {
    if (fruit == null || fruit.name == null) {
      throw new WebApplicationException("Fruit name was not set on request.", 422);
    }

    return Panache.withTransaction(
            () ->
                Fruit.<Fruit>findById(id)
                    .onItem()
                    .ifNotNull()
                    .invoke(entity -> entity.name = fruit.name))
        .onItem()
        .ifNotNull()
        .transform(entity -> ok(entity).build())
        .onItem()
        .ifNull()
        .continueWith(ok().status(Status.NOT_FOUND)::build);
  }

  @DELETE
  @Path("{id}")
  public Uni<Response> delete(@RestPath Long id) {
    return Panache.withTransaction(() -> Fruit.deleteById(id))
        .map(
            deleted ->
                deleted
                    ? ok().status(Status.NO_CONTENT).build()
                    : ok().status(Status.NOT_FOUND).build());
  }

  @Provider
  public static class ErrorMapper implements ExceptionMapper<Exception> {

    @Inject ObjectMapper objectMapper;

    @Override
    public Response toResponse(Exception exception) {
      LOGGER.error("Failed to handle request", exception);

      Throwable throwable = exception;

      int code = 500;
      if (throwable instanceof WebApplicationException) {
        code = ((WebApplicationException) exception).getResponse().getStatus();
      }

      // This is a Mutiny exception and it happens, for example, when we try to insert a new
      // fruit but the name is already in the database
      if (throwable instanceof CompositeException) {
        throwable = ((CompositeException) throwable).getCause();
      }

      ObjectNode exceptionJson = objectMapper.createObjectNode();
      exceptionJson.put("exceptionType", throwable.getClass().getName());
      exceptionJson.put("code", code);

      if (exception.getMessage() != null) {
        exceptionJson.put("error", throwable.getMessage());
      }

      return status(code).entity(exceptionJson).build();
    }
  }
}
