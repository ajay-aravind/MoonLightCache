
package in.ajay.tarangam;

import in.ajay.core.*;
import in.ajay.logging.ILogger;
import in.ajay.logging.SysOutLogger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.GeneralSecurityException;
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

    public void start() throws IOException, GeneralSecurityException {

        AppConfig.printConfig();
        AppConfig.getEnv().forEach(entry ->
                logger.LogDebug(entry.getKey() + "=" + entry.getValue()));

        if (running) {
            throw new IllegalStateException("Server is already running");
        }

        SSLContext sslContext = TlsContext.createServerContext();

        SSLServerSocket serverSocket =
                (SSLServerSocket) sslContext.getServerSocketFactory()
                        .createServerSocket(
                                40001,
                                50,
                                InetAddress.getByName("127.0.0.1"));

        serverSocket.setEnabledProtocols(
                new String[]{"TLSv1.3", "TLSv1.2"});

        logger.LogInfo("TLS cache server listening on 127.0.0.1:40001");
        running = true;
        logger.LogInfo("Starting moonlight cache");

        cache = new MoonLightCache();

        try {
            while (running) {
                try {
                    SSLSocket client = (SSLSocket) serverSocket.accept();
                    System.out.printf(
                            "Client connected: %s%n",
                            client.getRemoteSocketAddress());

                    clientExecutor.submit(() -> handleClient(client, cache));

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

    private void handleClient(SSLSocket client, MoonLightCache cache) {
        ConnectionHandler handler = new ConnectionHandler(client, logger, cache);
        handler.handleClient();
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

    public static void main(String[] args) throws IOException, GeneralSecurityException {

        try (CacheServer server = new CacheServer()) {
            server.start();
        }
    }
}
