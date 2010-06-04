package at.fhooe.mcm.deadreckoning.host.dsr;

/**
 * @class RREPPkg
 * @brief this class represents a RREP package used by DSR.
 *
 * Container for a RREP package in DSR. It contains the route from initiator
 * to target.
 *
 * @author Florian Lettner, Lukas Bischof, Peter Riedl
 * @date 26.05.2010
 * @version 1.0
 */
public class RREPPkg {

    /** @brief The route record containing the route from the initiator to the target of the data. */
    private RouteRecord m_rec = new RouteRecord();

    /**
     * @brief Initiates a new RREP package with the passed RouteRecord.
     *
     * @param _rr The route to be set.
     */
    public RREPPkg(RouteRecord _rr) {
        m_rec = _rr;
    }

    /**
     * @brief Instantiates a new RREP package from the passed string representation of the RREP package.
     *
     * @param _pkg The string representation of a RREP package.
     */
    public RREPPkg(String _pkg) {
        parseRREP(_pkg);
    }

    /**
     * @brief Creates a string representation of a RREP package.
     *
     * @return The string representation of a RREP package.
     */
    public String toString() {
        StringBuffer b = new StringBuffer("[RREP]");
        b.append(m_rec.toString());
        return b.toString();
    }

    /**
     * @brief Parses the string representation of a RREP package.
     *
     * Assures that the passed string is a representation of a RREP package and
     * extracts the RouteRecord from the package.
     *
     * @param _pkg The string representation of the RREP package.
     */
    private void parseRREP(String _pkg) {
        String buff = _pkg;
        if (!buff.startsWith("[RREP]")) {
            return;
        }
        int headerEndIdx = buff.indexOf("]");
        buff = buff.substring(headerEndIdx + 1);
        setRouteRecord(new RouteRecord(buff));
    }

    /**
     * @brief Provides the route from initiator to target.
     *
     * @return the route from initiator to target.
     */
    public RouteRecord getRouteRecord() {
        return m_rec;
    }

    /**
     * @brief Sets the passed RouteRecord as RouteRecord for the package.
     *
     * @param _rec the RouteRecord to be set.
     */
    public void setRouteRecord(RouteRecord _rec) {
        this.m_rec = _rec;
    }
}
