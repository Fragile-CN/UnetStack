import org.arl.fjage.*
import org.arl.unet.*
import org.arl.unet.mac.*
import org.arl.unet.phy.*
import org.arl.unet.nodeinfo.*
import org.arl.fjage.param.Parameter

class TDMA extends UnetAgent{
    private int nodeNum    
    private int sendSlot 
    private float slotLength   

    ////// agent constants
    private AgentID phy
    private int addr

    ////// expose parameters that are expected of a MAC service,read-only parameters
    final float PROP_DELAY  = nodeNum * slotLength
    final int MAX_QUEUE_LEN = 3000
    final int ackPayloadSize  = 0   
    final boolean channelBusy = false               
    final int reservationPayloadSize   = 0                
    final float maxReservationDuration = 65.535

    ////// reservation request queue
    private Queue<ReservationReq> queue = new ArrayDeque<ReservationReq>(MAX_QUEUE_LEN)
    //////timeQueue should be supposed to work with reservationReqQueue,which is not needed in Aloha
    public Queue<Long> timeQueue = new LinkedList<Long>()

    TDMA(int nodeNum, int sendSlot, float slotLength){
        this.nodeNum    = nodeNum
        this.sendSlot   = sendSlot
        this.slotLength = slotLength
    }

    ////// protocol FSM
    private enum State{
        IDLE, TX
    }

    private enum Event{

    }

    private FSMBehavior fsm = FSMBuilder.build{
        def rxInfo
        def rnd = AgentLocalRandom.current()

        state(State.IDLE) 
        {
            action {
                if (!queue.isEmpty()){
                    setNextState(State.TX) 
                }
                block()
            }
        }

        state(State.TX){
            onEnter{
                ReservationReq msg = queue.poll()
                double doubleTime_second = phy.time / 1000000
                int intTime_second =  Math.ceil(doubleTime_second)
                def offset = slotLength*sendSlot - intTime_second%(slotLength*nodeNum)
                if(offset<0){
                    offset +=slotLength*nodeNum
                }
                def slotTime = intTime_second-doubleTime_second+offset
                after(slotTime){
                    sendReservationStatusNtf(msg, ReservationStatus.START) 
                    add new WakerBehavior(  (long)Math.round(1000*msg.duration),{ 
                            sendReservationStatusNtf(msg, ReservationStatus.END) 
                            setNextState(State.IDLE)
                        }
                    )
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

        add new OneShotBehavior({
                def nodeInfo = agentForService Services.NODE_INFO
                addr = get(nodeInfo, NodeInfoParam.address)
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
                if (queue.size() >= MAX_QUEUE_LEN)
                    return new Message(msg, Performative.FAILURE)
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
            status: status)
    }


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

}