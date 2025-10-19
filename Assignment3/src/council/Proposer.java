package council;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proposer executes Phase 1 and Phase 2 of Paxos.
 *  - startPrepare(candidate): broadcast PREPARE(n)
 *  - onPromise: on majority, choose value (highest previously accepted, else original) & broadcast ACCEPT_REQUEST(n, v)
 *  - onAccepted: on majority, broadcast DECIDE
 * Includes once-only guards to avoid duplicate ACCEPT_REQUEST or DECIDE.
 */
public class Proposer {
    private final String selfId;
    private final Transport transport;
    private final NetworkConfig cfg;

    private long localCounter = 0;

    // Tracking maps
    private final Map<ProposalNumber, Set<String>> promises  = new ConcurrentHashMap<>();
    private final Map<ProposalNumber, Set<String>> accepteds = new ConcurrentHashMap<>();
    private final Map<ProposalNumber, String> originalValue  = new ConcurrentHashMap<>();
    private final Map<ProposalNumber, Map<String, ProposalNumber>> accAcceptedN = new ConcurrentHashMap<>();
    private final Map<ProposalNumber, Map<String, String>>          accAcceptedV = new ConcurrentHashMap<>();

    // Once-only guards
    private final Set<ProposalNumber> acceptPhaseStarted = ConcurrentHashMap.newKeySet();
    private final Set<ProposalNumber> decidedProposals   = ConcurrentHashMap.newKeySet();

    /**
     * Constructs a proposer.
     *
     * @param selfId    this member id
     * @param transport transport for sending messages
     * @param cfg       network configuration for majority and peers
     */
    public Proposer(String selfId, Transport transport, NetworkConfig cfg) {
        this.selfId = selfId; this.transport = transport; this.cfg = cfg;
    }

    /**
     * Generates the next unique proposal number for this proposer.
     *
     * @return new {@link ProposalNumber} with incremented counter and this proposer's id
     */
    public synchronized ProposalNumber nextProposalNumber() {
        localCounter += 1;
        return new ProposalNumber(localCounter, selfId);
    }

    /**
     * Starts Phase 1 by broadcasting PREPARE(n) with a new proposal number.
     *
     * @param candidate candidate value to propose (e.g., "M5")
     * @throws Exception if broadcasting fails
     */
    public void startPrepare(String candidate) throws Exception {
        ProposalNumber pn = nextProposalNumber();
        originalValue.put(pn, candidate);
        System.out.println("[" + selfId + "] PREPARE " + pn + " for " + candidate);
        Message m = new Message(MessageType.PREPARE, selfId, pn, null, null);
        transport.broadcast(m);
    }

    /**
     * Handles PROMISE(n) responses. Once a majority is reached and not yet started,
     * chooses a value and broadcasts ACCEPT_REQUEST(n, v).
     *
     * @param promiseMsg PROMISE message received from an acceptor
     * @throws Exception if broadcasting the phase-2 request fails
     */
    public void onPromise(Message promiseMsg) throws Exception {
        ProposalNumber pn = promiseMsg.proposal;
        promises.computeIfAbsent(pn, k -> ConcurrentHashMap.newKeySet())
                .add(promiseMsg.senderId);

        // Collect prior accepted info (if any) to apply Paxos rule
        if (promiseMsg.extra != null) {
            String accNumStr = promiseMsg.extra.get("accNum");
            String accVal    = promiseMsg.extra.get("accVal");
            if (accNumStr != null && accVal != null) {
                ProposalNumber accN = ProposalNumber.parse(accNumStr);
                accAcceptedN.computeIfAbsent(pn, k -> new ConcurrentHashMap<>())
                        .put(promiseMsg.senderId, accN);
                accAcceptedV.computeIfAbsent(pn, k -> new ConcurrentHashMap<>())
                        .put(promiseMsg.senderId, accVal);
            }
        }

        if (reachedMajority(promises.get(pn)) && acceptPhaseStarted.add(pn)) {
            String chosenVal = chooseValueForAcceptPhase(pn);
            System.out.println("[" + selfId + "] ACCEPT_REQUEST " + pn + " value=" + chosenVal);
            Message accReq = new Message(MessageType.ACCEPT_REQUEST, selfId, pn, chosenVal, null);
            transport.broadcast(accReq);
        }
    }

    /**
     * Handles ACCEPTED(n, v) responses. Once a majority is reached and not yet decided,
     * broadcasts DECIDE.
     *
     * @param acceptedMsg ACCEPTED message received from an acceptor
     * @throws Exception if broadcasting DECIDE fails
     */
    public void onAccepted(Message acceptedMsg) throws Exception {
        ProposalNumber pn = acceptedMsg.proposal;
        accepteds.computeIfAbsent(pn, k -> ConcurrentHashMap.newKeySet())
                .add(acceptedMsg.senderId);

        if (reachedMajority(accepteds.get(pn)) && decidedProposals.add(pn)) {
            Message decide = new Message(MessageType.DECIDE, selfId, pn, acceptedMsg.value, null);
            System.out.println("[" + selfId + "] DECIDE " + pn + " -> " + acceptedMsg.value);
            transport.broadcast(decide);
        }
    }

    /**
     * Selects the value to propose in Phase 2 according to Paxos:
     * if any acceptor reported a prior accepted (n, v), use the v with the highest n;
     * otherwise use the original value supplied at startPrepare.
     *
     * @param pn proposal number for which to choose the value
     * @return chosen value for the accept phase
     */
    private String chooseValueForAcceptPhase(ProposalNumber pn) {
        Map<String, ProposalNumber> mapN = accAcceptedN.getOrDefault(pn, Map.of());
        if (mapN.isEmpty()) return originalValue.get(pn);

        String bestV = null; ProposalNumber bestN = null;
        for (Map.Entry<String, ProposalNumber> e : mapN.entrySet()) {
            ProposalNumber n = e.getValue();
            if (bestN == null || n.compareTo(bestN) > 0) {
                bestN = n;
                String fromId = e.getKey();
                bestV = accAcceptedV.getOrDefault(pn, Map.of()).get(fromId);
            }
        }
        return (bestV != null) ? bestV : originalValue.get(pn);
    }

    /**
     * Checks if a set of voter ids reaches majority.
     *
     * @param s voter set (may be null)
     * @return true if size &gt;= majority quorum
     */
    private boolean reachedMajority(Set<String> s) {
        return s != null && s.size() >= (cfg.members().size() / 2 + 1);
    }
}