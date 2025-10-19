package council;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * TCP-based transport:
 *  - One {@link ServerSocket} per node.
 *  - One-line request/response per connection for simplicity.
 *  - Best-effort broadcast: log WARN on unreachable peers.
 */
public class TcpTransport implements Transport {
    private final String selfId;
    private final MemberInfo self;
    private final Map<String, MemberInfo> directory;
    private final MessageCodec codec = new MessageCodec();

    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running = false;
    private ServerSocket server;
    private Consumer<Message> handler;

    /**
     * Constructs a transport bound to a given member.
     *
     * @param selfId this node id
     * @param cfg    network directory
     * @throws IllegalArgumentException if the id is not found in cfg
     */
    public TcpTransport(String selfId, NetworkConfig cfg) {
        this.selfId = selfId;
        this.directory = cfg.members();
        this.self = cfg.get(selfId);
        if (this.self == null) throw new IllegalArgumentException("Unknown selfId " + selfId);
    }

    /**
     * Starts the server socket and accept loop.
     *
     * @param inboundHandler handler invoked on each inbound message
     * @throws Exception if server socket cannot be opened
     */
    @Override
    public void start(Consumer<Message> inboundHandler) throws Exception {
        this.handler = inboundHandler;
        this.server = new ServerSocket(self.port);
        this.running = true;
        System.out.println("[" + selfId + "] listening on " + self.port);
        pool.submit(() -> {
            while (running) {
                try {
                    Socket s = server.accept();
                    pool.submit(() -> handleClient(s));
                } catch (IOException ignore) { /* server closed */ }
            }
        });
    }

    /**
     * Handles a single inbound TCP connection: reads one line, dispatches, replies "OK".
     *
     * @param s accepted socket
     */
    private void handleClient(Socket s) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true)) {
            String line = br.readLine();
            if (line != null && handler != null) {
                Message m = codec.decode(line);
                handler.accept(m);
                pw.println("OK");
            }
        } catch (IOException ignore) { }
    }

    /**
     * Sends a message to a specific peer via a short-lived TCP connection.
     *
     * @param toMemberId destination member id
     * @param msg        message
     * @throws Exception if connection or I/O fails
     */
    @Override
    public void send(String toMemberId, Message msg) throws Exception {
        MemberInfo peer = directory.get(toMemberId);
        if (peer == null) throw new IllegalArgumentException("Unknown peer: " + toMemberId);
        try (Socket s = new Socket(peer.host, peer.port);
             PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
             BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            pw.println(codec.encode(msg));
            br.readLine(); // read ACK
        }
    }

    /**
     * Best-effort broadcast to all known peers except self.
     * Logs a WARN if a peer is unreachable, and continues.
     *
     * @param msg message to broadcast
     * @throws Exception if an unrecoverable error occurs
     */
    @Override
    public void broadcast(Message msg) throws Exception {
        for (String id : directory.keySet()) {
            if (id.equals(selfId)) continue;
            try {
                send(id, msg);
            } catch (IOException e) {
                System.out.println("[" + selfId + "] WARN: could not reach " + id + " (" + e.getClass().getSimpleName() + ")");
            }
        }
    }

    /**
     * Closes the server and stops the executor.
     */
    @Override
    public void close() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignore) {}
        pool.shutdownNow();
    }
}