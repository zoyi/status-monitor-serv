package com.zoyi.status.monitor.server;

import com.zoyi.status.monitor.server.dto.SquareAuthToken;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Optional;

import static com.google.common.base.Strings.*;


/**
 * Created by lloyd on 2016-05-11
 */
public class App {
  public static final String API_BASE = "walkinsights.com";
  public static final String STATION_HOST = "station5.walkinsights.com";
  /*public static final String STATION_HOST = "127.0.0.1";*/
  public static final String STATION_URI = "/squares";
  public static final String STATION_PATH = "/sensor.io";
  public static final int STATION_PORT = 443;

  public static final String USER_EMAIL = "lloyd@zoyi.co";
  public static final String USER_PASSWORD = "";
  /*public static final String USER_TOKEN = "JGBZhPPiVw4zY5gAecz5";*/
  public static final String SQUARE_MAC = "f4fd2b000000";

  public static final Logger LOG = LoggerFactory.getLogger(App.class);


  public static void main(String[] args) {
    final PeriodicExecutor executor = new PeriodicExecutor();

    // Sign in: User
    executor.userSignIn(USER_EMAIL, USER_PASSWORD, userToken -> {
      // User authentication failed
      if (isNullOrEmpty(userToken)) LOG.error("User authentication failed!");

      // Succeed
      else {
        //#
        LOG.info("UserAuthToken retrieved: "+userToken);

        // Sign in: Square
        executor.squareSignIn(USER_EMAIL, userToken, SQUARE_MAC, authToken -> {
          // Square authentication failed
          if (authToken == null) LOG.error("Square authentication failed!");
          else {
            //#
            LOG.info("SquareAuthToken retrieved: "+authToken);

            // Create WebSocket for local connection test
            /*executor.createWebSocketServerTester(STATION_PORT);*/

            // Connect to Station
            executor.execute(authToken, STATION_HOST, STATION_PORT, STATION_URI, STATION_PATH);
          }
        });
      }
    });
  }


  public static class PeriodicExecutor {
    private final Vertx vertx = Vertx.vertx();
    private final HttpClientOptions defaultHttpOptions =
        new HttpClientOptions().setDefaultHost(API_BASE)
                               .setDefaultPort(443)
                               .setSsl(true)
                               .setTrustAll(true);


    public void createWebSocketServerTester(int port) {
      final HttpServer server = vertx.createHttpServer(
          new HttpServerOptions().setPort(port)
      );

      server.requestHandler(request -> {
        final MultiMap headers = request.headers();
        System.out.println(headers.toString());

      }).websocketHandler(webSocket -> {
        System.out.println("WebSocket client connected!");

        webSocket.handler(buffer -> {
          System.out.println("Server received: "+buffer.toString());
          webSocket.writeBinaryMessage(Buffer.buffer("This is Server!"));
        });

      }).listen();
    }


    public void execute(SquareAuthToken squareAuthToken, String stationHost, int stationPort, String stationUri, String stationPath) {
      final HttpClientOptions options =
          new HttpClientOptions().setConnectTimeout(5000)
                                 /*.setDefaultPort(443)*/
                                 .setSsl(true)
                                 .setTrustAll(true);

      final HttpClient webSocketClient = vertx.createHttpClient(options);
      final JsonObject query =
          new JsonObject().put("square_mac", "f4fd2b000000")
                          .put("shop_id", "1364")
                          .put("ts", squareAuthToken.getTsString())
                          .put("auth_token", squareAuthToken.getAuthToken());

      final MultiMap header = MultiMap.caseInsensitiveMultiMap()
                                      .set("path", stationPath)
                                      .set("query", query.encode());

      // WebSocket connect
      webSocketClient.websocket(stationPort, stationHost, stationUri, header, webSocket -> {
        webSocket.handler(data -> {
          System.out.println("Received data " + data.toString("ISO-8859-1"));
          webSocketClient.close();
        });

        webSocket.exceptionHandler(Throwable::printStackTrace);
        webSocket.endHandler(_v -> System.out.println("- end -"));

        webSocket.writeBinaryMessage(Buffer.buffer("Hello world"));
      }, Throwable::printStackTrace);
    }


    /*
     * Retrieve UserAuthToken by API:
     *    [POST] /api/v1/users/sign_in
     */
    public void userSignIn(String userEmail, String userPassword, Handler<String> userTokenHandler) {
      // Execute
      vertx.createHttpClient(defaultHttpOptions)
           .post("/api/v1/users/sign_in", response -> {
             // Succeed
             if (response.statusCode() == 200)
               response.bodyHandler(buffer ->
                   userTokenHandler.handle(
                       Optional.of(buffer.toJsonObject())
                               .filter(json -> json.containsKey("user"))
                               .map(json -> json.getJsonObject("user"))
                               .map(userJson -> userJson.getString("authentication_token", null))
                               .orElse(null)
                   ));
             // Failed
             else userTokenHandler.handle(null);
           })
           .end(String.format("user[email]=%s&user[password]=%s", userEmail, userPassword));
    }


    /*
     * Retrieve SquareAuthToken by API:
     *    [GET] /api/v1/squares/:square_mac/auth_token
     */
    public void squareSignIn(String userEmail, String userToken, String squareMac, Handler<SquareAuthToken> authTokenHandler) {
      vertx.createHttpClient(defaultHttpOptions)
           .get("/api/v1/squares/"+squareMac+"/auth_token", response -> {
             // Succeed
             if (response.statusCode() == 200)
               response.bodyHandler(buffer ->
                   authTokenHandler.handle(
                       Optional.of(buffer.toJsonObject())
                               .filter(json -> json.containsKey("square"))
                               .map(json -> json.getJsonObject("square"))
                               .filter(squareJson -> squareJson.containsKey("auth_token"))
                               .filter(squareJson -> squareJson.containsKey("ts"))
                               .map(squareJson ->
                                   SquareAuthToken.of(
                                       squareJson.getString("auth_token"),
                                       squareJson.getLong("ts")))
                               .orElse(null)
                   ));
             // Failed
             else authTokenHandler.handle(null);
           })
           .putHeader("X-User-Email", userEmail)
           .putHeader("X-User-Token", userToken)
           .end();
    }
  }
}
