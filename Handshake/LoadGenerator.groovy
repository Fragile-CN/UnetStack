/**
this agent will generate offeredLoad
**/
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
        if (mac?.hasProperty('timeQueue')){
            mac.timeQueue.clear()
        }
        //PoissonBehavior
        add new PoissonBehavior  (  (long)(1000/rate), 
                                    {
                                        
                                        mac << new ReservationReq(to: rnditem(destNodes), duration: dataPktDuration)
                                        mac.timeQueue << phy.time
                                        if (mac?.hasProperty('timeQueue')) 
                                        {
                                            mac.timeQueue << phy.time
                                        }
                                    }
                                ) 
        //add new WakerBehavior   ((1000*LoadDuration), { container.kill load })

    }

    @Override
    void processMessage(Message msg){
        if (msg instanceof ReservationStatusNtf && msg.status == ReservationStatus.START){
            String time = ""
            if (mac?.hasProperty('timeQueue')){
                time = "${mac.timeQueue.poll()}" 
            }
            else{
                time = "${phy.time}"
            }
            time = "${mac.timeQueue.poll()}" 
            def transData = stringToBytes(time)
            phy << new ClearReq()                                  
            phy << new TxFrameReq(to: msg.to, type: Physical.DATA, data: transData)
        }
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
    
    private def stringToBytes(String str){
        return str.getBytes() 
    }

}