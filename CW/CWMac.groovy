/**
CWMac protocol 
**/
import org.arl.fjage.*
import org.arl.unet.*
import org.arl.unet.mac.*
import org.arl.unet.phy.*
import org.arl.unet.nodeinfo.*
import org.arl.fjage.param.Parameter

class CWMac extends UnetAgent{
    ////// agent constants
    def rnd = AgentLocalRandom.current()
    private int cw 
    private float slot = 0.2
    private AgentID phy
    private int addr
    private def info
    private def locationX
    private def locationY 

    ////// constants for count 
    private int txcount = 0 
    private int rxcount = 0
    private int datatxcount = 0  
    private int datarxcount = 0

    ////// expose parameters that are expected of a MAC service,read-only parameters
    final boolean channelBusy = false                  
    final int reservationPayloadSize = 0                
    final int ackPayloadSize = 0
    final float maxReservationDuration = 65.535

    ////// reservation request queue
    private Queue<ReservationReq> queue = new ArrayDeque<ReservationReq>()

    //////timeQueue should be supposed to work with reservationReqQueue,which is not needed in Aloha
    Queue<Long> timeQueue = new LinkedList<Long>()
  
    CWMac(int cw){
        this.cw = cw
    }

    ////// PDU encoder/decoder
    private final static int BROADCAST_PDU = 0x01
    private final static int ACK_PDU = 0x02
    private final static int ORDER_PDU = 0x03
    private final static int DATA_PDU = 0x04
    private final static PDU pdu = PDU.withFormat{
        uint8('type')
        uint32('duration')   
    }

    private enum State{
        IDLE, WAIT_1,WAIT_2, TX
    }

    /////// Carrier Sense
    private enum Event{
        CS  
    }

    ////// Protocol FSM
    private FSMBehavior fsm = FSMBuilder.build{
        long sendTime 
        def msg

        state(State.IDLE){
            action{
                if (!queue.isEmpty()){
                    sendTime = phy.time + rnd.nextInt(cw) * slot * 1000000
                    setNextState(State.WAIT_1)
                }
                block()
            }
        }

        state(State.WAIT_1){
            onEnter{
                if ( phy.time > sendTime ){
                    setNextState(State.TX)
                }
                else{
                    after(0.1.seconds){
                        setNextState(State.WAIT_2)
                    }
                }
            }
            onEvent(Event.CS){
                msg = queue.peek()
                sendTime += msg.duration * 1000000
            }
        }


        state(State.WAIT_2){
            onEnter{
                if ( phy.time > sendTime ){
                    setNextState(State.TX)
                }
                else{
                    after(0.1.seconds){
                        setNextState(State.WAIT_1)
                    }
                }
            }
            onEvent(Event.CS){
                msg = queue.peek()
                sendTime += msg.duration * 1000000
            }
        }

        state(State.TX){
            onEnter{
                msg = queue.poll()
                sendReservationStatusNtf(msg, ReservationStatus.START)
                after(msg.duration){
                    sendReservationStatusNtf(msg, ReservationStatus.END)
                    setNextState(State.IDLE)
                }
            }

        }

    } 

    @Override
    void setup(){
        register Services.MAC
    }

    @Override
    void startup(){
        phy = agentForService Services.PHYSICAL
        subscribe(phy)

        add new OneShotBehavior(
            {
                def nodeInfo = agentForService Services.NODE_INFO
                addr = get(nodeInfo, NodeInfoParam.address)
                def location = get(nodeInfo, NodeInfoParam.location)
                locationX = location[0].intValue()
                locationY = location[1].intValue()
            }
        )
        add(fsm)
    }

    ////// process MAC service requests
    @Override
    Message processRequest(Message msg){
        switch (msg){
            case ReservationReq:
                if (msg.to == Address.BROADCAST || msg.to == addr)
                    return new RefuseRsp(msg, 'Reservation must have a destination node')
                if (msg.duration <= 0 || msg.duration > maxReservationDuration)
                    return new RefuseRsp(msg, 'Bad reservation duration')
                queue.add(msg)
                fsm.restart()    
                return new ReservationRsp(msg)
            case ReservationCancelReq:
            case ReservationAcceptReq:
            case TxAckReq:
                return new RefuseRsp(msg, 'Not supported')
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
        if( msg instanceof RxFrameStartNtf ){
            fsm.trigger(Event.CS) 
        }
        if ( msg instanceof RxFrameNtf ){
            def rx = pdu.decode(msg.data)
            info = [from: msg.from, to: msg.to]

            if( msg.type==Physical.DATA ){
                datarxcount += 1
            }
            rxcount += 1
        } 
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

    private void sendReservationStatusNtf(ReservationReq msg, ReservationStatus status){
        send new ReservationStatusNtf(
            recipient: msg.sender,
            inReplyTo: msg.msgID,
            to: msg.to,
            //from: addr
            status: status)
    }

    ////// get & set
    private void textWrite(String fileName, Object content, boolean overwrite){
        def file = new File(fileName)
        if (!file.exists()){
            file.createNewFile()
        }
        def fileWriter = new FileWriter(file, !overwrite)
        
        if (content instanceof List){
            content.each{
                fileWriter.write(it.toString() + "\n")
            }
        } 
        else{
            fileWriter.write(content.toString() + "\n")
        }
        
        fileWriter.close()
    }

    private def stringToBytes(String str){
        return str.getBytes() 
    }

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


}