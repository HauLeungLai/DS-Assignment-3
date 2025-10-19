package council;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Learner collects ACCEPTED(n, v) and announces decision once a majority accepts the same (n, v).
 */
public class Learner {
    private final NetworkConfig cfg;
    private volatile boolean decided = false;

    // proposal -> value -> voters set
    private final Map<ProposalNumber, Map<String, Set<String>>> votes = new ConcurrentHashMap<>();

    /**
     * Constructs a learner with cluster configuration.
     *
     * @param cfg network configuration to compute majority
     */
    public Learner(NetworkConfig cfg) { this.cfg = cfg; }

    /**
     * Processes an ACCEPTED(n, v) event.
     * When a value reaches majority for a given proposal n, prints a consensus line.
     *
     * @param accepted ACCEPTED message
     */
    public void onAccepted(Message accepted) {
        votes.computeIfAbsent(accepted.proposal, k -> new ConcurrentHashMap<>());
        Map<String, Set<String>> byVal = votes.get(accepted.proposal);
        byVal.computeIfAbsent(accepted.value, v -> ConcurrentHashMap.newKeySet())
                .add(accepted.senderId);

        if (!decided) {
            Set<String> s = byVal.get(accepted.value);
            if (s.size() >= majority()) {
                decided = true;
                System.out.println("CONSENSUS: " + accepted.value + " has been elected Council President!");
            }
        }
    }

    /**
     * Processes a DECIDE message (idempotent).
     *
     * @param decide DECIDE message
     */
    public void onDecide(Message decide) {
        if (!decided) {
            decided = true;
            System.out.println("CONSENSUS: " + decide.value + " has been elected Council President!");
        }
    }

    /**
     * @return quorum size = floor(N/2) + 1
     */
    private int majority() { return cfg.members().size() / 2 + 1; }

    /**
     * @return whether a decision has been learned
     */
    public boolean isDecided() { return decided; }
}