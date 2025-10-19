package council;

/**
 * Types of messages used in (basic) Paxos.
 */
public enum MessageType {
    /** proposer -> acceptors: phase-1 request */
    PREPARE,
    /** acceptor -> proposer: phase-1 response (may include previously accepted (n, v)) */
    PROMISE,
    /** proposer -> acceptors: phase-2 request with the value to accept */
    ACCEPT_REQUEST,
    /** acceptor -> proposer (and observed by learners): phase-2 response */
    ACCEPTED,
    /** decision announcement broadcast */
    DECIDE,
}