package council;

import java.io.*;
import java.util.Scanner;

/**
 * Entry point for a council member node.
 * Responsibilities:
 *  - Load {@code network.config}
 *  - Start TCP listener
 *  - Wire Proposer / Acceptor / Learner roles
 *  - Support interactive input or auto-proposal via flags
 *
 * Usage:
 *   java -cp out council.CouncilMember M4 [--config path] [--propose M5] [--delay 1200]
 */
public class CouncilMember {

    /**
     * Boots a member process with id Mx.
     *
     * @param args command line: {@code <MemberId>} and optional {@code --config path --propose Mx --delay ms}
     * @throws Exception on I/O or networking failures during startup
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -cp out council.CouncilMember <MemberId> [--config path] [--propose Mx] [--delay ms]");
            System.exit(1);
        }
        String selfId = args[0];
        String cfgPath = "network.config";
        String autoPropose = null;
        long autoDelayMs = 1000;

        // Parse optional flags
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> { if (i + 1 < args.length) cfgPath = args[++i]; }
                case "--propose" -> { if (i + 1 < args.length) autoPropose = args[++i]; }
                case "--delay" -> { if (i + 1 < args.length) autoDelayMs = Long.parseLong(args[++i]); }
                default -> { /* ignore unknown */ }
            }
        }

        NetworkConfig cfg = NetworkConfig.load(new File(cfgPath));
        if (cfg.get(selfId) == null) {
            System.err.println("Self id " + selfId + " not found in config.");
            System.exit(2);
        }

        PaxosState state = new PaxosState();

        try (TcpTransport transport = new TcpTransport(selfId, cfg)) {
            Proposer proposer = new Proposer(selfId, transport, cfg);
            Acceptor acceptor = new Acceptor(selfId, state, transport);
            Learner learner = new Learner(cfg);

            // Start listener and message dispatcher
            transport.start(msg -> {
                try {
                    switch (msg.type) {
                        case PREPARE -> acceptor.onPrepare(msg);
                        case PROMISE -> proposer.onPromise(msg);
                        case ACCEPT_REQUEST -> acceptor.onAcceptRequest(msg);
                        case ACCEPTED -> {
                            proposer.onAccepted(msg);
                            learner.onAccepted(msg);
                        }
                        case DECIDE -> learner.onDecide(msg);
                        default -> { /* ignore unknown */ }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // graceful close on shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(transport::close));

            // Interactive input loop
            System.out.println("[" + selfId + "] ready. Type a candidate id to propose (e.g., M5).");
            Scanner sc = new Scanner(System.in);
            while (true) {
                if (!sc.hasNextLine()) {
                    Thread.sleep(50);
                    continue;
                }
                String candidate = sc.nextLine().trim();
                if (candidate.isEmpty()) continue;
                if (!cfg.members().containsKey(candidate)) {
                    System.out.println("[" + selfId + "] Unknown candidate '" + candidate + "'. Must be one of " + cfg.members().keySet());
                    continue;
                }
                try {
                    proposer.startPrepare(candidate);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}