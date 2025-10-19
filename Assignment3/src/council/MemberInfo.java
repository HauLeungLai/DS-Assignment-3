package council;

/**
 * Peer information resolved from network.config.
 */
public class MemberInfo {
    public final String id;
    public final String host;
    public final int port;

    /**
     * Constructs member information.
     *
     * @param id   member id (e.g., "M1")
     * @param host hostname or IP
     * @param port listening TCP port
     */
    public MemberInfo(String id, String host, int port) {
        this.id = id; this.host = host; this.port = port;
    }

    /**
     * @return human-readable member string
     */
    @Override
    public String toString() { return id + "@" + host + ":" + port; }
}