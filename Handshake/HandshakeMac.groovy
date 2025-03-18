import org.arl.fjage.* 
import org.arl.unet.*
import org.arl.unet.phy.*
import org.arl.unet.mac.*
import org.arl.unet.nodeinfo.*
import org.arl.fjage.param.Parameter

class HandshakeMac extends UnetAgent{
    ////// agent constants
    private AgentID phy
    private int addr

    ////// constants for count 
    private int txcount = 0 
    private int rxcount = 0
    private int datatxcount = 0 
    private int datarxcount = 0

    ////// read-only parameters
    final int reservationPayloadSize = 0           
    final int ackPayloadSize = 0
    final float maxReservationDuration = 65.535

    ////// protocol constants
    private final static int PROTOCOL           = Protocol.MAC
    private final static float  RTS_BACKOFF     = 2.seconds
    private final static float  CTS_TIMEOUT     = 5.seconds
    private final static float  BACKOFF_RANDOM  = 5.seconds
    private final static float  MAX_PROP_DELAY  = 2.seconds
    private final static int    MAX_RETRY       = 3

    ////// reservation request queue
    private Queue<ReservationReq> queue = new ArrayDeque<ReservationReq>()

    //////timeQueue should be supposed to work with reservationReqQueue,which is not needed in Aloha
    Queue<Long> timeQueue = new LinkedList<Long>()

    ////// PDU encoder/decoder
    private final static int RTS_PDU = 0x01
    private final static int CTS_PDU = 0x02
    private final static PDU pdu = PDU.withFormat{
        uint8('type')         // RTS_PDU/CTS_PDU
        uint16('duration')    // ms
    }

    ////// protocol FSM
    private enum State{
        IDLE, RTS, TX, RX, BACKOFF
    }

    private enum Event{
        RX_RTS, RX_CTS, SNOOP_RTS, SNOOP_CTS
    }

    private FSMBehavior fsm = FSMBuilder.build{

        int retryCount = 0
        float backoff = 0
        def rxInfo
        def rnd = AgentLocalRandom.current()  

        state(State.IDLE){
            action{
                if (!queue.isEmpty()){
                    after(rnd.nextDouble(0, BACKOFF_RANDOM)) 
                    {  
                        setNextState(State.RTS)
                    }
                }
                block()
            }
            onEvent(Event.RX_RTS){   info ->
                rxInfo = info
                setNextState(State.RX)
            }
            onEvent(Event.SNOOP_RTS){
                backoff = RTS_BACKOFF
                setNextState(State.BACKOFF)
            }
            onEvent(Event.SNOOP_CTS){   info ->
                backoff = info.duration + 2*MAX_PROP_DELAY
                setNextState(State.BACKOFF)
            }
        }

        state(State.RTS){
            onEnter{
                Message msg = queue.peek()
                def bytes = pdu.encode(
                    type: RTS_PDU,
                    duration: Math.ceil(msg.duration*1000))
                phy << new TxFrameReq(
                    to: msg.to,
                    type: Physical.CONTROL,
                    protocol: PROTOCOL,
                    data: bytes)
                after(CTS_TIMEOUT){
                    if (++retryCount >= MAX_RETRY){
                        def temp1 = timeQueue.poll()
                        sendReservationStatusNtf(queue.poll(), ReservationStatus.FAILURE)
                        retryCount = 0
                    }
                    setNextState(State.IDLE)
                }
            }
            onEvent(Event.RX_CTS){
                setNextState(State.TX)
            }
        }

        state(State.TX){
            onEnter{
                ReservationReq msg = queue.poll()
                retryCount = 0
                sendReservationStatusNtf(msg, ReservationStatus.START)
                after(msg.duration){
                    sendReservationStatusNtf(msg, ReservationStatus.END)
                    setNextState(State.IDLE)
                }
            }
        }

        state(State.RX){
            onEnter{
                def bytes = pdu.encode(
                    type: CTS_PDU,
                    duration: Math.round(rxInfo.duration*1000))
                phy << new TxFrameReq(
                    to: rxInfo.from,
                    type: Physical.CONTROL,
                    protocol: PROTOCOL,
                    data: bytes)
                after(rxInfo.duration + 2*MAX_PROP_DELAY){
                    setNextState(State.IDLE)
                }
                rxInfo = null
            }
        }

        state(State.BACKOFF){
            onEnter{
                after(backoff){
                    setNextState(State.IDLE)
                }
            }
            onEvent(Event.SNOOP_RTS){
                backoff = RTS_BACKOFF
                reenterState()
            }
            onEvent(Event.SNOOP_CTS){   info ->
                backoff = info.duration + 2*MAX_PROP_DELAY
                reenterState()
            }
        }

    } // end of FSMBuilder



    @Override
    void setup(){
        register Services.MAC
    }

    @Override
    void startup(){
        phy = agentForService Services.PHYSICAL
        subscribe(phy)
        subscribe(topic(phy, Physical.SNOOP))
        add new OneShotBehavior(
            {def nodeInfo = agentForService Services.NODE_INFO
            addr = get(nodeInfo, NodeInfoParam.address)}
            )
        add(fsm)
    }

    ////// process MAC service requests
    @Override
    Message processRequest(Message msg){
        switch (msg){
            case ReservationReq:
                if (msg.to == Address.BROADCAST || msg.to == addr)
                    return new Message(msg, Performative.REFUSE)
                if (msg.duration <= 0 || msg.duration > maxReservationDuration)
                    return new Message(msg, Performative.REFUSE)
                queue.add(msg)
                fsm.restart()    // tell fsm to check queue, as it may block if empty
                return new ReservationRsp(msg)
            case ReservationCancelReq:
            case ReservationAcceptReq:
            case TxAckReq:
                return new Message(msg, Performative.REFUSE)
        }
        return null
    }

    ////// handle incoming MAC packets
    @Override
    void processMessage(Message msg){

        if( msg instanceof TxFrameStartNtf ){
            if( msg.type==Physical.DATA ){
                datatxcount += 1
            }
            txcount += 1
            return null
        }

        else if (msg instanceof RxFrameNtf ){
            if( msg.type==Physical.DATA ){
                datarxcount += 1
            }
            rxcount += 1

            def rx = pdu.decode(msg.data)
            def info = [from: msg.from, to: msg.to, duration: rx.duration/1000.0]
            if (rx.type == RTS_PDU){
                fsm.trigger(info.to == addr ? Event.RX_RTS : Event.SNOOP_RTS, info)
            }
            else if (rx.type == CTS_PDU){
                fsm.trigger(info.to == addr ? Event.RX_CTS : Event.SNOOP_CTS, info)
            }
            
        }
    }

    private void sendReservationStatusNtf(ReservationReq msg, ReservationStatus status){
    send new ReservationStatusNtf(
        recipient: msg.sender,
        inReplyTo: msg.msgID,
        to: msg.to,
        from: addr,
        status: status)
    }

    ////// get & set
    void setTxcount(int value){
        this.txcount = value
    }
    int getTxcount(){
        return this.txcount
    }

    void setRxcount(int value){
        this.rxcount = value
    }
    int getRxcount(){
        return this.rxcount
    }

    void setDatatxcount(int value){
        this.datatxcount = value
    }
    int getDatatxcount(){
        return this.datatxcount
    }

    void setDatarxcount(int value){
        this.datarxcount = value
    }
    int getDatarxcount(){
        return this.datarxcount
    }

    @Override
    List<Parameter> getParameterList(){            
        return allOf(MacParam)
    }

    boolean getChannelBusy(){                      
        return fsm.currentState.name != State.IDLE
    }

    float getRecommendedReservationDuration(){    
        return get(phy, Physical.DATA, PhysicalChannelParam.frameDuration)
    }

}