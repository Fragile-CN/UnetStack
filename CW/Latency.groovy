/**
this agent will save the reservation time and the reception time
**/
import org.arl.fjage.*
import org.arl.unet.*
import org.arl.unet.mac.*
import org.arl.unet.phy.*
import org.arl.unet.nodeinfo.*
import org.arl.fjage.param.Parameter

class Latency extends UnetAgent{
    //////Parameters supposed to be modified
    private String mainFilePath = "/home/xxxx/Documents/Data_analysis/HandshakeMac/Latency.txt"

    ////// agent constants
    private AgentID mac, phy
    private int addr

    @Override
    void startup(){
        phy = agentForService Services.PHYSICAL
        subscribe phy 
        add new OneShotBehavior
        (
            {
                def nodeInfo = agentForService Services.NODE_INFO
                addr = get(nodeInfo, NodeInfoParam.address)
            }
        )
    }

    @Override
    void processMessage(Message msg){
        if(msg.type!=Physical.DATA){
            return null
        }
        if(msg instanceof BadFrameNtf){
            String content = "BadFrameNtf"
            textWrite(mainFilePath,content,false)  
        }
        if( msg instanceof RxFrameNtf ){
            String content =String.format("%-16s", bytesToString(msg.data)) + "  " +
                            String.format("%-16s", "${phy.time}") + "  " + 
                            String.format("%-16s", "${addr}") + "  " + 
                            String.format("%-16s", "${msg.from}") + "  "
            textWrite(mainFilePath,content,false)
        }
    }

    //////function for reading&fixing Parameters
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
        else 
        {
            fileWriter.write(content.toString() + "\n")
        }
        
        fileWriter.close()
    }

    def bytesToString(byte[] data){
        return new String(data)
    }

}

