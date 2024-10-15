
package audio.hummingbird.app;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import jakarta.json.JsonArray;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.client.Entity;

import io.helidon.microprofile.testing.junit5.HelidonTest;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@HelidonTest
class MainTest {

    @Inject
    private WebTarget target;


    @Test
    void testPokemonTypes() {
        JsonArray types = target
                .path("type")
                .request()
                .get(JsonArray.class);
        assertThat(types.size(), is(18));
    }

    @Test
    void testPokemon() {
        assertThat(getPokemonCount(), is(6));

        Pokemon pokemon = target
                .path("pokemon/1")
                .request()
                .get(Pokemon.class);
        assertThat(pokemon.getName(), is("Bulbasaur"));

        pokemon = target
                .path("pokemon/name/Charmander")
                .request()
                .get(Pokemon.class);
        assertThat(pokemon.getType(), is(10));

        Response response = target
                .path("pokemon/1")
                .request()
                .get();
        assertThat(response.getStatus(), is(200));

        Pokemon test = new Pokemon();
        test.setType(1);
        test.setId(100);
        test.setName("Test");
        response = target
                .path("pokemon")
                .request()
                .post(Entity.entity(test, MediaType.APPLICATION_JSON));
        assertThat(response.getStatus(), is(204));
        assertThat(getPokemonCount(), is(7));

        response = target
                .path("pokemon/100")
                .request()
                .delete();
        assertThat(response.getStatus(), is(204));
        assertThat(getPokemonCount(), is(6));
    }

    private int getPokemonCount() {
        JsonArray pokemons = target
                .path("pokemon")
                .request()
                .get(JsonArray.class);
        return pokemons.size();
    }


    @Test
    void testGreet() {
        Message message = target
                .path("simple-greet")
                .request()
                .get(Message.class);
        assertThat(message.getMessage(), is("Hello World!"));
    }
                
}
