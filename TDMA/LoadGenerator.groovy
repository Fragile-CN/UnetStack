import org.arl.fjage.*
import org.arl.unet.*
import org.arl.unet.phy.*
import org.arl.unet.mac.*

class LoadGenerator extends UnetAgent{
    private List<Integer> destNodes                     // list of possible destination nodes
    private float load                                  // normalized load to generate
    private AgentID mac, phy

    LoadGenerator(List<Integer> destNodes, float load ){
        this.destNodes = destNodes                        
        this.load = load                                 
    }

    @Override
    void startup(){
        phy = agentForService Services.PHYSICAL
        subscribe phy
        mac = agentForService Services.MAC
   
        float dataPktDuration = get(phy, Physical.DATA, PhysicalChannelParam.frameDuration) 
        float rate = load/dataPktDuration   
        mac.timeQueue.clear()
  
        add new PoissonBehavior (  (long)(1000/rate), {
                                        mac << new ReservationReq(to: rnditem(destNodes), duration: dataPktDuration)
                                        mac.timeQueue << phy.time
                                    }
                                ) 
  
    }

    @Override
    void processMessage(Message msg){
        if (msg instanceof ReservationStatusNtf && msg.status == ReservationStatus.START){
            String time = "${mac.timeQueue.poll()}" 
            def transData = stringToBytes(time)
            phy << new ClearReq()                                  
            phy << new TxFrameReq(to: msg.to, type: Physical.DATA, data: transData)
        }
    }

    private def stringToBytes(String str){
        return str.getBytes() 
    }

}