package council;

import java.util.HashMap;
import java.util.Map;

/**
 * Acceptor logic for Phase 1 and Phase 2.
 */
public class Acceptor {
    private final String selfId;
    private final PaxosState state;
    private final Transport transport;

    /**
     * Constructs an acceptor.
     *
     * @param selfId    this member id
     * @param state     persistent acceptor state
     * @param transport transport for replies
     */
    public Acceptor(String selfId, PaxosState state, Transport transport) {
        this.selfId = selfId; this.state = state; this.transport = transport;
    }

    /**
     * Handles a PREPARE(n) request.
     * If n &gt;= highestPromised, records a promise and replies PROMISE
     * optionally including (accNum, accVal) if previously accepted.
     *
     * @param prepare incoming PREPARE message
     * @throws Exception if sending a response fails
     */
    public void onPrepare(Message prepare) throws Exception {
        ProposalNumber n = prepare.proposal;
        boolean promised;
        ProposalNumber prevN;
        String prevV;
        synchronized (state) {
            ProposalNumber hp = state.getHighestPromised();
            if (hp == null || n.compareTo(hp) >= 0) {
                state.promise(n);
                promised = true;
            } else {
                promised = false;
            }
            prevN = state.getAcceptedNumber();
            prevV = state.getAcceptedValue();
        }
        if (promised) {
            Map<String,String> extra = new HashMap<>();
            if (prevN != null) extra.put("accNum", prevN.toString());
            if (prevV != null) extra.put("accVal", prevV);
            Message resp = new Message(MessageType.PROMISE, selfId, n, null, extra);
            transport.send(prepare.senderId, resp);
        }
        // else: silently reject (simplified)
    }

    /**
     * Handles an ACCEPT_REQUEST(n, v).
     * If n &gt;= highestPromised, records acceptance and replies ACCEPTED.
     *
     * @param req incoming ACCEPT_REQUEST message
     * @throws Exception if sending a response fails
     */
    public void onAcceptRequest(Message req) throws Exception {
        ProposalNumber n = req.proposal;
        boolean accepted = false;
        synchronized (state) {
            ProposalNumber hp = state.getHighestPromised();
            if (hp == null || n.compareTo(hp) >= 0) {
                state.promise(n);
                state.accept(n, req.value);
                accepted = true;
            }
        }
        if (accepted) {
            Message resp = new Message(MessageType.ACCEPTED, selfId, n, req.value, null);
            transport.send(req.senderId, resp);
        }
    }
}