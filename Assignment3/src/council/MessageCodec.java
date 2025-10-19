package council;

import java.util.*;

/**
 * Simple line-based codec:
 *   key=value;key=value...
 * Reserved keys: type, from, p, value
 * Extras are prefixed with x_ (e.g., x_accNum, x_accVal).
 */
public class MessageCodec {

    /**
     * Encodes a {@link Message} into a single line string.
     *
     * @param m message to encode
     * @return textual representation suitable for a single TCP line
     */
    public String encode(Message m) {
        List<String> kv = new ArrayList<>();
        kv.add("type=" + m.type.name());
        kv.add("from=" + m.senderId);
        if (m.proposal != null) kv.add("p=" + m.proposal.toString());
        if (m.value != null) kv.add("value=" + m.value);
        for (Map.Entry<String,String> e : m.extra.entrySet()) {
            kv.add("x_" + e.getKey() + "=" + e.getValue());
        }
        return String.join(";", kv);
    }

    /**
     * Decodes a message from the line format produced by {@link #encode(Message)}.
     *
     * @param line encoded line
     * @return reconstructed {@link Message}
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    public Message decode(String line) {
        Map<String,String> map = new HashMap<>();
        for (String part : line.split(";")) {
            int i = part.indexOf('=');
            if (i > 0) map.put(part.substring(0, i), part.substring(i + 1));
        }
        MessageType type = MessageType.valueOf(map.get("type"));
        String from = map.get("from");
        ProposalNumber pn = null;
        if (map.containsKey("p")) pn = ProposalNumber.parse(map.get("p"));
        String value = map.get("value");
        Map<String,String> extra = new HashMap<>();
        for (Map.Entry<String,String> e : map.entrySet()) {
            if (e.getKey().startsWith("x_")) {
                extra.put(e.getKey().substring(2), e.getValue());
            }
        }
        return new Message(type, from, pn, value, extra);
    }
}