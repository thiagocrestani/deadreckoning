package at.fhooe.mcm.dsr;

import at.fhooe.mcm.dsr.packages.DataPkg;
import at.fhooe.mcm.dsr.packages.RREPPkg;
import at.fhooe.mcm.dsr.packages.RREQPkg;
import at.fhooe.mcm.dsr.util.IITupel;
import at.fhooe.mcm.dsr.util.RequestTable;
import at.fhooe.mcm.dsr.util.RouteRecord;
import at.fhooe.mcm.dsr.util.RouteTable;
import com.sun.spot.io.j2me.radiogram.Radiogram;
import com.sun.spot.io.j2me.radiogram.RadiogramConnection;
import com.sun.spot.peripheral.NoRouteException;
import com.sun.spot.peripheral.radio.RadioFactory;
import com.sun.spot.sensorboard.EDemoBoard;
import com.sun.spot.sensorboard.peripheral.ITriColorLED;
import com.sun.spot.sensorboard.peripheral.LEDColor;
import com.sun.spot.sensorboard.peripheral.TriColorLED;
import com.sun.spot.util.IEEEAddress;
import java.io.IOException;
import java.util.Vector;
import javax.microedition.io.Connector;

/**
 * @class DSRClient
 * @author Florian Lettner, Lukas Bischof, Peter Riedl
 * @version 1.0
 *
 * @brief The class with all the logic required for Dynamic Source Routing
 *
 * The only public funtion "sendData" is used to send data to any host in the
 * network. In order to perform dynamic soure routing, the client listens to any
 * connections on port 66 all the time. The received packets are inspected and
 * treated according to their type ([RREQ], [RREP] and [DATA].
 */
public class DSRClient
{
    /** @brief the timeout after which the routing table is discarded and rebuilt */
    private static final int DISCOVERY_TIMEOUT = 10000;
    /** @brief the first 8 digits of all sunSPOTS could be used for reduction of
     packet overhead */
    private static final String ADDRESS_START = "0014.4F01.";
    /** @brief the timeout after which the data are sent after a route request
     had to be performed */
    private static final int ROUTE_REQUEST_TIMEOUT = 500;
    /** @brief the time the according LED is set on when something is indicated */
    private static final int BLINK_TIME = 500;

    /** @brief the ID of the last generated [RREQ] package */
    private int m_lastRRQID = 0;
    /**@brief a vector of strings containing the addresses of the surrounding
     sunSPOTS**/
    private Vector m_clientsInRange = new Vector();

    /**@brief here all received routing requests are stored @see RequestTable**/
    private RequestTable m_reqTable = new RequestTable();
    /**@brief here all discovered routes are stored @see RouteTable**/
    private RouteTable m_routeTable = new RouteTable();

    /**@brief an array containing all available LEDs**/
    private ITriColorLED m_leds[] = EDemoBoard.getInstance().getLEDs();


    /**@brief the index of the LED indicating sending data**/
    private static final int SEND_DATA_LED = 0;
    /**@brief the index of the LED indicating creating an RREQ*/
    private static final int CREATE_RREQ_LED = 1;
    /**@brief the index of the LED indicating forwarding an RREQ*/
    private static final int FORWARD_RREQ_LED = 2;
    /**@brief the index of the LED indicating forwarding an RREP*/
    private static final int FORWARD_RREP_LED = 3;
    /** @brief the index of the LED indicating receiving an RREP */
    private static final int RECEIVE_RREP_LED = 4;
    /** @brief the index of the LED indicating forwarding DATA */
    private static final int FORWARD_DATA_LED = 5;
    /** @brief the index of the LED indicating receiving DATA */
    private static final int RECEIVE_DATA_LED = 6;

    /** @brief color for indicating sending data */
    private static final LEDColor SEND_DATA_COL = LEDColor.BLUE;
    /** @brief color for indicating creating an RREQ */
    private static final LEDColor CREATE_RREQ_COL = LEDColor.GREEN;
    /** @brief color for indicating forwarding an RREQ */
    private static final LEDColor FORWARD_RREQ_COL = LEDColor.YELLOW;
    /** @brief color for indicating forwarding an RREP */
    private static final LEDColor FORWARD_RREP_COL = LEDColor.ORANGE;
    /** @brief color for indicating receiving an RREP */
    private static final LEDColor RECEIVE_RREP_COL = LEDColor.RED;
    /** @brief color for indicating forwarding data */
    private static final LEDColor FORWARD_DATA_COL = LEDColor.WHITE;
    /** @brief color for indicating receiving data. */
    private static final LEDColor RECEIVE_DATA_COL = LEDColor.GREEN;

    /** @brief base address of all connections indicating a radiogram connection*/
    private String m_baseAddr = "radiogram://";
    /**@brief the port of all connections*/
    private int m_port = 66;
    /**@brief the address for receiving data*/
    private String m_recvAddr = m_baseAddr+":"+m_port;
    /**@brief color for broadcasting data*/
    private String m_broadcastAddr = m_baseAddr+"broadcast:"+m_port;

    /**@brief receiving is stopped when this variable is set to <code>false</code> */
    private boolean m_execRCVLoop = true;
    /**@brief node discovery is stopped when this variable is set to <code>false</code> */
    private boolean m_execNodeDiscovery = true;
    /**@brief blinking is stopped when this variable is set to <code>false</code> */
    private boolean m_execBlink = true;

    /**
     * @brief starts all neccessary threads for the DSR client
     */
    public DSRClient()
    {
        new Thread() {
            public void run () {
                indicateProgramRunning();
            }
        }.start();

        new Thread(){
            public void run()
            {
                rcvLoop();
            }
        }.start();

        new Thread(){
            public void run()
            {
                //discoverNodesInRange();
            }
        }.start();
    }

    /**
     * @brief sends data to de specified target
     *
     * if neccessary the route to the target address is discovered and afterwards
     * the data are sent over the route
     * @param _data the data to be sent
     * @param _addr the address of the target
     */
    public synchronized void sendData(final String _data, final String _addr)
    {
        indicateSendData();
        new Thread()
        {
            public void run()
            {
                System.out.println("sending data");
                if(!m_routeTable.contains(_addr))
                {
                    System.out.println("route was not in table");
                    performRREQ(_addr);
                    try {
                        Thread.sleep(ROUTE_REQUEST_TIMEOUT);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }

                if(m_routeTable.contains(_addr))
                {
                    System.out.println("route is in table");
                    RouteRecord r = m_routeTable.getRouteToTarget(_addr);
                    sendDataOverRoute(new DataPkg(r, _data).toString(), r);
                }
                else
                {
                    System.out.println("route still not in table");
                }
            }
        }.start();

       
    }

    /**
     * @brief discovers all nodes in range by sending a ping
     */
    private void discoverNodesInRange()
    {
        while(m_execNodeDiscovery)
        {
            System.out.println("discovering spots in range...");
            m_clientsInRange.removeAllElements();
            broadcastPing();
            try
            {
                Thread.sleep(DISCOVERY_TIMEOUT);
            }
            catch (InterruptedException ex)
            {
                ex.printStackTrace();
            }
        }
    }

    /**
     * @brief listens for incomming connections
     *
     * here a radiogram connection to the receive address is established
     * afterwards whenever a package is received the received content is passed
     * to the processReceivedMessage method for further computation
     */
    private void rcvLoop()
    {
        RadiogramConnection rxConn = null;
        try
        {
            rxConn = (RadiogramConnection) Connector.open(m_recvAddr);
            rxConn.setMaxBroadcastHops(1);
            Radiogram rrg = (Radiogram) rxConn.newDatagram(rxConn.getMaximumLength());
            while(m_execRCVLoop)
            {
                rrg.reset();
                rxConn.receive(rrg);
                processReceivedMessage(rrg);
            }
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
        finally
        {
            if(null!=rxConn)
            {
                try
                {
                    rxConn.close();
                } 
                catch (IOException ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    }

    /**
     * decides wether the message contained in the passed radiogram was a ping,
     * acknowledge, RREQ, RREP or DATA package and initiates according actions
     * @param _rrg the radiogram containing the message to parse
     */
    private void processReceivedMessage(Radiogram _rrg)
    {
        String msg = "";
        try {
            msg = _rrg.readUTF();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if(msg.equals("ping"))
        {
            sendPingACK(m_baseAddr+_rrg.getAddress()+":"+m_port);
        }
        else if(msg.equals("ack"))
        {
            addClientInRange(_rrg.getAddress());
        }
        else if(msg.startsWith("[RREQ]"))
        {
            System.out.println("[RREQ] discovered");
            RREQPkg pkg = new RREQPkg(msg);
            IITupel tupel = pkg.getIITupel();
            if(m_reqTable.contains(tupel)||pkg.getRouteRecord().getInitiator().equals(getOwnAddress()))
            {
                System.out.println("[RREQ] ignored");
                return;
            }
            else if(pkg.getTargetAddress().equals(getOwnAddress()))
            {
                sendRREPTarget(pkg.getRouteRecord());
                m_reqTable.addTupel(tupel);
            }
            else if(m_routeTable.contains(pkg.getTargetAddress()))
            {
                sendRREPRouteToTarget(pkg.getRouteRecord(), pkg.getTargetAddress());
                m_reqTable.addTupel(tupel);
            }
            else
            {
                System.out.println("forwarding RREQ");
                m_reqTable.addTupel(tupel);
                forwardRREQ(pkg);
            }
            System.out.println("added tupel to list");
        }
        else if(msg.startsWith("[RREP]"))
        {
            RREPPkg pkg = new RREPPkg(msg);
            if(pkg.getRouteRecord().getInitiator().equals(getOwnAddress()))
            {
                indicateReceiveRREP();
                m_routeTable.addRoute(pkg.getRouteRecord().getTarget(), pkg.getRouteRecord());
                System.out.println("added route to table");
            }
            else
            {
                forwardRREP(pkg);
                System.out.println("[RREP] forwarded");
            }
            System.out.println(msg);
        }
        else if(msg.startsWith("[DATA]"))
        {
            DataPkg pkg = new DataPkg(msg);
            if(pkg.getRouteRecord().getTarget().equals(getOwnAddress()))
            {
                indicateReceiveData();
                System.out.println("###[DATA] received:"+msg.substring(msg.indexOf("]")+1));
            }
            else
            {
                forwardData(pkg);
                System.out.println("[DATA] forwarded");
            }
        }
    }

    /**
     * appends the own address to a newly creater RREP package and sends it over
     * the reversed route to the initiator of the RREQ
     * @param _rr the RouteRecord contained in the RREQ
     */
    private void sendRREPTarget(RouteRecord _rr)
    {
        System.out.println("sending RREP target");
        _rr.addNodeAddr(getOwnAddress());
        RouteRecord r = new RouteRecord(_rr.toString());

        sendDataOverRoute(new RREPPkg(_rr).toString(), r.reverse());
    }

    /**
     * appends the route to the intended target of a RREQ and transmits it to
     * the initiator of the RREQ
     * @param _rr the RouteRecord contained in the RREQ
     * @param _target the target of the RREQ
     */
    private void sendRREPRouteToTarget(RouteRecord _rr, String _target)
    {
        System.out.println("sending RREP route to target");
        RouteRecord r = _rr.concat(m_routeTable.getRouteToTarget(_target));
        _rr.addNodeAddr(getOwnAddress());
        sendDataOverRoute(new RREPPkg(r).toString(), _rr.reverse());
    }

    /**
     * extracts the next hop from the passed RouteRecord and sends the data to
     * that address
     * @param _data the data to be sent
     * @param _rr the RouteRecord containing the next hop
     */
    private void sendDataOverRoute(String _data, RouteRecord _rr)
    {
        String addr = _rr.getNextHop(getOwnAddress());
        sendDataToTarget(_data, m_baseAddr+addr+":"+m_port);
    }

    /**
     * acknowledges a ping
     * @param _addr the address of the sender of the ping
     */
    private void sendPingACK(String _addr)
    {
        sendDataToTarget("ack", _addr);
    }

    /**
     * sends a ping over the broadcast address
     */
    private void broadcastPing()
    {
        sendBroadcast("ping");
    }

    /**
     * creates a new RREQ package with the own address as initiator and the only
     * entry in the RouteRecord
     * @param _target the intended target of the RREQ
     */
    private void performRREQ(String _target)
    {
        indicateCreateRREQ();
        RouteRecord r = new RouteRecord();
        r.addNodeAddr(getOwnAddress());
        m_lastRRQID++;
        sendBroadcast(new RREQPkg(getOwnAddress(), _target, r, m_lastRRQID).toString());
    }

    /**
     * forwards a received RREQ
     * @param _pkg the package to forward
     */
    private void forwardRREQ(RREQPkg _pkg)
    {
        indicateForwardRREQ();
        _pkg.getRouteRecord().addNodeAddr(getOwnAddress());
        sendBroadcast(_pkg.toString());
    }

    /**
     * forwards received DATA
     * @param _pkg the data to forward
     */
    private void forwardData(DataPkg _pkg)
    {
        indicateForwardData();
        sendDataOverRoute(_pkg.toString(), _pkg.getRouteRecord());
    }

    /**
     * forwards a reveived RREP
     * @param _pkg the package to forward
     */
    private void forwardRREP(RREPPkg _pkg)
    {
        indicateForwardRREP();
        sendDataOverRoute(_pkg.toString(), _pkg.getRouteRecord().reverse());
    }

    /**
     * sends data over the broadcast address
     * @param _msg the data to send
     */
    private void sendBroadcast(String _msg)
    {
        sendDataToTarget(_msg, m_broadcastAddr);
    }

    /**
     * establishes a connection to the passed address and sends the data over it
     * @param _msg the message to be sent
     * @param _addr the address to send to
     */
    private synchronized void sendDataToTarget(final String _msg, final String _addr)
    {

       // new Thread()
         {
         //   public void run ()
            {
                RadiogramConnection txConn = null;
                try
                {
                    txConn = (RadiogramConnection) Connector.open(_addr);
                    txConn.setMaxBroadcastHops(1);
                    
                    Radiogram tdg = (Radiogram) txConn.newDatagram(txConn.getMaximumLength());
                    tdg.reset();
                    tdg.writeUTF(_msg);
                    txConn.send(tdg);
                }
                catch(NoRouteException _nae)
                {
                    m_routeTable.clear();
                    System.out.println("routing table was cleared after no ack");
                }
                catch (IOException _ioe)
                {
                    System.out.println("send exception (IO):"+_ioe.getMessage());

                    _ioe.printStackTrace();
                }
                catch(Exception _e)
                {
                    System.out.println("send exception:"+_e.getMessage());
                }
                finally
                {
                    try
                    {
                        if(txConn!=null)
                        {
                            txConn.close();
                        }
                    }
                    catch (IOException ex)
                    {
                        ex.printStackTrace();
                    }
                }
            }
        }//.start();
    }

    /**
     * sets the passed color for the led at the passed index, turns it on, sleeps
     * for the BLINK_TIME and turns it off again
     * @param _ledIdx the index of the LED to blink
     * @param _col the color in which the LED should blink
     */
    private synchronized void blink(final int _ledIdx, final LEDColor _col)
    {
        new Thread()
        {
            public void run()
            {
                m_leds[_ledIdx].setColor(_col);
                m_leds[_ledIdx].setOn();
                try {
                    Thread.sleep(BLINK_TIME);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                m_leds[_ledIdx].setOff();
            }
        }.start();
    }

    /**
     * lets the send LED blink
     */
    private void indicateSendData()
    {
        blink(SEND_DATA_LED, SEND_DATA_COL);
    }
    /**
     * lets the create RREQ LED blink
     */
    private void indicateCreateRREQ()
    {
        blink(CREATE_RREQ_LED, CREATE_RREQ_COL);
    }

    /**
     * lets the forward RREQ LED blink
     */
    private void indicateForwardRREQ()
    {
        blink(FORWARD_RREQ_LED, FORWARD_RREQ_COL);
    }

    /**
     * lets the forward RREP LED blink
     */
    private void indicateForwardRREP()
    {
        blink(FORWARD_RREP_LED, FORWARD_RREP_COL);
    }

    /**
     * lets the receive RREP LED blink
     */
    private void indicateReceiveRREP()
    {
        blink(RECEIVE_RREP_LED, RECEIVE_RREP_COL);
    }

    /**
     * lets the forward data LED blink
     */
    private void indicateForwardData()
    {
        blink(FORWARD_DATA_LED, FORWARD_DATA_COL);
    }

    /**
     * lets the receive data LED blink
     */
    private void indicateReceiveData()
    {
        blink(RECEIVE_DATA_LED, RECEIVE_DATA_COL);
    }

    /**
     * lets the rightmost LED blink with a frequency of 1Hz
     */
    private void indicateProgramRunning()
    {
        EDemoBoard demoBoard = EDemoBoard.getInstance();
        TriColorLED blinkLed = (TriColorLED) demoBoard.getLEDs()[7];
        blinkLed.setColor(LEDColor.ORANGE);
        while(m_execBlink)
        {
            blinkLed.setOn();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            blinkLed.setOff();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * adds the passed address to the list of clients in range
     * @param _addr the address to be added
     */
    private void addClientInRange(String _addr)
    {
        if(m_clientsInRange.contains(_addr))
        {
            System.out.println(_addr+" already in list");
        }
        else
        {
            m_clientsInRange.addElement(_addr);
            System.out.println("added "+_addr+" to list of in range nodes");
        }
    }

    /**
     * retrieves the address of the sunSpot and returns it
     * @return the string representation of the own address
     */
    private String getOwnAddress()
    {
        String addr = IEEEAddress.toDottedHex(  RadioFactory.
                                                getRadioPolicyManager().
                                                getIEEEAddress());
         return addr;
    }

    /**
     * stops all running threads
     */
    public void stop()
    {
        m_execRCVLoop = false;
        m_execNodeDiscovery = false;
        m_execBlink = false;
    }

    /**
     * @deprecated
     * used for testing purposes only
     */
    private void testSendData()
    {
        while(true)
        {
            sendData("geilo", ADDRESS_START+"0000.6EF0");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }
}
