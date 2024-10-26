package audio.hummingbird.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;

import java.nio.file.Files;

/**
 * A simple JAX-RS resource to greet you. Examples:
 *
 * <p>Get default download: curl -X GET <a href="http://localhost:8080/download">...</a>
 *
 * <p>The message is returned as a JSON object.
 */
@Path("/download")
public class DownloadResource {
  private final Logger logger = LoggerFactory.getLogger(DownloadResource.class);

  @Inject
  public DownloadResource() {}

  /**
   * Return a worldly greeting message.
   *
   * @return {@link Response}
   */
  @GET
  @Path("/{fname}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response download(@PathParam("fname") String fname) throws JsonRpcClientErrorException, JsonProcessingException, InterruptedException {
    XrpLedger.sendPayment();
    var path = java.nio.file.Path.of("song.mp3").toAbsolutePath();
    return Response.ok()
            .header("Content-Disposition", "attachment; filename=\"" + fname + "\"")
            .entity((StreamingOutput) output -> Files.copy(path, output))
            .build();
  }
}
