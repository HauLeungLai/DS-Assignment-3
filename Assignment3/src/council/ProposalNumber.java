package council;

import java.util.Objects;

/**
 * Proposal number (n) represented as (counter, proposerId).
 * Comparable first by counter, then by proposerId to break ties.
 */
public final class ProposalNumber implements Comparable<ProposalNumber> {
    private final long counter;
    private final String proposerId;

    /**
     * Constructs a proposal number.
     *
     * @param counter    monotonically increasing counter local to proposer
     * @param proposerId unique proposer/member id
     */
    public ProposalNumber(long counter, String proposerId) {
        this.counter = counter;
        this.proposerId = proposerId;
    }

    /**
     * @return the counter component of this proposal number
     */
    public long getCounter() { return counter; }

    /**
     * @return the proposer id component of this proposal number
     */
    public String getProposerId() { return proposerId; }

    /**
     * Compares proposal numbers by counter, then by proposer id.
     *
     * @param o other proposal number
     * @return negative if this &lt; o, zero if equal, positive if this &gt; o
     */
    @Override
    public int compareTo(ProposalNumber o) {
        int c = Long.compare(this.counter, o.counter);
        if (c != 0) return c;
        return this.proposerId.compareTo(o.proposerId);
    }

    /**
     * @param o other object
     * @return true if both counter and proposerId are equal
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ProposalNumber)) return false;
        ProposalNumber pn = (ProposalNumber) o;
        return counter == pn.counter && Objects.equals(proposerId, pn.proposerId);
    }

    /**
     * @return hash code based on counter and proposer id
     */
    @Override
    public int hashCode() { return Objects.hash(counter, proposerId); }

    /**
     * @return string form "counter.proposerId" (e.g., "7.M4")
     */
    @Override
    public String toString() { return counter + "." + proposerId; }

    /**
     * Parses a string like "7.M4" into a {@link ProposalNumber}.
     *
     * @param s textual form "counter.proposerId"
     * @return parsed proposal number
     * @throws IllegalArgumentException if the string is not in the expected form
     */
    public static ProposalNumber parse(String s) {
        int i = s.lastIndexOf('.');
        if (i < 0) throw new IllegalArgumentException("Bad ProposalNumber: " + s);
        long cnt = Long.parseLong(s.substring(0, i));
        String pid = s.substring(i + 1);
        return new ProposalNumber(cnt, pid);
    }
}