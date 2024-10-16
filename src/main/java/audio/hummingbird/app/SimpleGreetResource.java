package audio.hummingbird.app;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple JAX-RS resource to greet you. Examples:
 *
 * <p>Get default greeting message: curl -X GET http://localhost:8080/simple-greet
 *
 * <p>The message is returned as a JSON object.
 */
@Path("/simple-greet")
public class SimpleGreetResource {
  private Logger logger = LoggerFactory.getLogger(SimpleGreetResource.class);
  private final String message;

  @Inject
  public SimpleGreetResource(@ConfigProperty(name = "app.greeting") String message) {
    this.message = message;
  }

  /**
   * Return a worldly greeting message.
   *
   * @return {@link Message}
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Message getDefaultMessage() {
    String msg = String.format("%s %s!", message, "World");
    Message message = new Message();
    message.setMessage(msg);
    logger.info("Message: {}", message.getMessage());
    return message;
  }
}
