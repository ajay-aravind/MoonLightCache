
package in.ajay.tarangam;

import in.ajay.core.*;
import in.ajay.logging.ILogger;
import in.ajay.logging.SysOutLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CacheServer implements AutoCloseable {

    private final ILogger logger = new SysOutLogger();
    private MoonLightCache cache;
    private static final String HOST = "localhost";
    private static final int PORT = 40001;

    private final ExecutorService clientExecutor =
            Executors.newVirtualThreadPerTaskExecutor();

    private volatile boolean running;
    private ServerSocket serverSocket;

    public void start() throws IOException {
        if (running) {
            throw new IllegalStateException("Server is already running");
        }

        InetAddress address = InetAddress.getByName(HOST);

        serverSocket = new ServerSocket(
                PORT, 50, address);

        running = true;

        System.out.printf(
                "Cache server listening on %s:%d%n",
                serverSocket.getInetAddress().getHostAddress(),
                serverSocket.getLocalPort());

        logger.LogInfo("Starting moonlight cache");
        cache = new MoonLightCache();
        AppConfig.list();

        try {
            while (running) {
                try {
                    Socket client = serverSocket.accept();

                    System.out.printf(
                            "Client connected: %s%n",
                            client.getRemoteSocketAddress());

                    clientExecutor.submit(() -> handleClient(client));

                } catch (SocketException e) {
                    if (running) {
                        throw e;
                    }
                    // Server was intentionally stopped.
                }
            }
        } finally {
            running = false;
            clientExecutor.shutdown();
        }
    }

    private void handleClient(Socket client) {
        try (client) {
            client.setSoTimeout(30_000);

            System.out.printf(
                    "Handling client %s%n",
                    client.getRemoteSocketAddress());

            // Read requests here using RequestParser.read(client.getInputStream()).

            Command command = RequestParser.read(client.getInputStream());
            logger.LogDebug(command.toString());
            CommandResponse response = cache.ProcessCommand(command);
            logger.LogDebug(response.toString());
            ResponseParser.write(client.getOutputStream(), response);

        } catch (IOException e) {
            System.err.printf(
                    "Client connection error (%s): %s%n",
                    client.getRemoteSocketAddress(),
                    e.getMessage());
        }
        catch(Exception e){
            logger.LogDebug("Exception while handling client request:"+e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        running = false;

        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }

        if(cache != null){
            cache.stop();
        }

        clientExecutor.close();
    }

    public static void main(String[] args) throws IOException {
        try (CacheServer server = new CacheServer()) {
            server.start();
        }
    }
}
