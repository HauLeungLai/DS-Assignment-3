package council;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic message structure used across Paxos roles.
 * The {@code extra} map can carry acceptor's previously accepted (n,v) in PROMISE.
 */
public class Message {
    public final MessageType type;
    public final String senderId;
    public final ProposalNumber proposal;
    public final String value;
    public final Map<String, String> extra;

    /**
     * Constructs a message.
     *
     * @param type     logical type of the message
     * @param senderId sender member id
     * @param proposal proposal number (may be null)
     * @param value    candidate value (may be null)
     * @param extra    additional metadata (may be null; copied internally)
     */
    public Message(MessageType type, String senderId,
                   ProposalNumber proposal, String value, Map<String,String> extra) {
        this.type = type;
        this.senderId = senderId;
        this.proposal = proposal;
        this.value = value;
        this.extra = (extra == null) ? new HashMap<>() : new HashMap<>(extra);
    }

    /**
     * Creates a simple message without proposal/value/extra.
     *
     * @param t    type
     * @param from sender id
     * @return new {@link Message}
     */
    public static Message simple(MessageType t, String from) {
        return new Message(t, from, null, null, null);
    }

    /**
     * Puts a key-value pair into the extra map and returns {@code this}.
     *
     * @param k extra key
     * @param v extra value
     * @return this message (for chaining)
     */
    public Message withExtra(String k, String v) {
        this.extra.put(k, v);
        return this;
    }
}