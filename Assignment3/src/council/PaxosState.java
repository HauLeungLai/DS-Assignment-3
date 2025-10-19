package council;

/**
 * Minimal acceptor state:
 *  - highestPromised: highest n promised during phase-1
 *  - acceptedNumber / acceptedValue: last accepted (n, v) during phase-2
 */
public class PaxosState {
    private ProposalNumber highestPromised;
    private ProposalNumber acceptedNumber;
    private String acceptedValue;

    /**
     * @return current highest promised proposal number or null
     */
    public synchronized ProposalNumber getHighestPromised() { return highestPromised; }

    /**
     * @return last accepted proposal number or null
     */
    public synchronized ProposalNumber getAcceptedNumber()  { return acceptedNumber;  }

    /**
     * @return last accepted value or null
     */
    public synchronized String          getAcceptedValue()  { return acceptedValue;   }

    /**
     * Records a promise for proposal number n (i.e., will not accept lower n).
     *
     * @param n proposal number promised
     */
    public synchronized void promise(ProposalNumber n) { this.highestPromised = n; }

    /**
     * Records an acceptance of (n, v) and updates highestPromised accordingly.
     *
     * @param n proposal number accepted
     * @param v value accepted
     */
    public synchronized void accept(ProposalNumber n, String v) {
        this.acceptedNumber = n; this.acceptedValue = v;
    }
}