package council;

import java.io.*;
import java.util.*;

/**
 * Loads a simple CSV-like config file mapping member id to host:port.
 * Format per line: id,host,port   (e.g., M1,localhost,9001)
 */
public class NetworkConfig {
    private final Map<String, MemberInfo> members = new LinkedHashMap<>();

    /**
     * Loads configuration from file.
     *
     * @param file config file path
     * @return a populated {@link NetworkConfig}
     * @throws IOException if the file cannot be read
     */
    public static NetworkConfig load(File file) throws IOException {
        NetworkConfig cfg = new NetworkConfig();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line; int ln = 0;
            while ((line = br.readLine()) != null) {
                ln++;
                String s = line.trim();
                if (s.isEmpty() || s.startsWith("#")) continue;
                String[] p = s.split(",");
                if (p.length != 3) {
                    System.err.println("Skip bad config line " + ln + ": " + line);
                    continue;
                }
                String id = p[0].trim();
                String host = p[1].trim();
                int port = Integer.parseInt(p[2].trim());
                cfg.members.put(id, new MemberInfo(id, host, port));
            }
        }
        return cfg;
    }

    /**
     * @return an unmodifiable view of member map
     */
    public Map<String, MemberInfo> members() { return Collections.unmodifiableMap(members); }

    /**
     * Gets info for a member id.
     *
     * @param id member identifier (e.g., "M1")
     * @return {@link MemberInfo} or {@code null} if unknown
     */
    public MemberInfo get(String id) { return members.get(id); }
}