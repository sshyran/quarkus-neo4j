// tag::get[]
package io.quarkus.it.neo4j;

import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jboss.resteasy.reactive.ResponseStatus;
import org.neo4j.driver.Driver;
import org.neo4j.driver.reactive.RxSession;
import org.reactivestreams.Publisher;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@Path("reactivefruits")
@Consumes(MediaType.APPLICATION_JSON)
public class ReactiveFruitResource {

    @Inject
    Driver driver;

    static Uni<Void> sessionFinalizer(RxSession session) { // <.>
        return Uni.createFrom().publisher(session.close());
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Publisher<String> get() {
        // Create a stream from a resource we can close in a finalizer...
        return Multi.createFrom().resource(driver::rxSession, // <.>
                session -> session.readTransaction(tx -> {
                    var result = tx
                            .run("MATCH (f:Fruit) RETURN f.name as name ORDER BY f.name");
                    return result.records();
                }))
                .withFinalizer(ReactiveFruitResource::sessionFinalizer) // <.>
                .map(record -> record.get("name").asString());
    }
    // end::get[]

    // tag::create[]
    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @ResponseStatus(201)
    public Uni<String> create(Fruit fruit) {

        return Uni.createFrom().emitter(e -> Multi.createFrom().resource(driver::rxSession, // <.>
                session -> session.writeTransaction(tx -> {
                    var result = tx.run(
                            "CREATE (f:Fruit {name: $name}) RETURN f",
                            Map.of("name", fruit.name));
                    return result.records();
                }))
                .withFinalizer(ReactiveFruitResource::sessionFinalizer)
                .map(record -> Fruit.from(record.get("f").asNode()))
                .subscribe().with( // <.>
                        persistedFruit -> e.complete("/fruits/" + persistedFruit.id)));
    }
    // end::create[]

    // tag::get[]
}
// end::get[]
