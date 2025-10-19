package council;

import java.io.Closeable;
import java.util.function.Consumer;

/**
 * Abstraction of network transport for sending and receiving messages.
 */
public interface Transport extends Closeable {
    /**
     * Starts the transport and begins listening for inbound messages.
     *
     * @param inboundHandler callback to process each received message
     * @throws Exception if starting the underlying server fails
     */
    void start(Consumer<Message> inboundHandler) throws Exception;

    /**
     * Sends a message to a specific peer.
     *
     * @param toMemberId destination member id
     * @param msg        message to send
     * @throws Exception if connection or I/O fails
     */
    void send(String toMemberId, Message msg) throws Exception;

    /**
     * Broadcasts a message to all peers except self. Implementations may use best-effort.
     *
     * @param msg message to broadcast
     * @throws Exception if an unrecoverable error occurs
     */
    void broadcast(Message msg) throws Exception;

    /**
     * Closes the transport and frees resources.
     */
    @Override void close();
}