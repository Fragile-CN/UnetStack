import org.arl.fjage.*
import org.arl.unet.*
import org.arl.unet.phy.*
import org.arl.unet.sim.*
import org.arl.unet.sim.channels.*
import static org.arl.unet.Services.*
import static org.arl.unet.phy.Physical.*
import org.arl.fjage.Agent.*

//////Parameters supposed to modify
String firstFilePath = "/home/xxxx/Documents/Data_analysis/TDMA/TDMALatency.txt"
String secondFilePath = "/home/xxxx/Documents/Data_analysis/TDMA/TDMAPacketCount.txt"
modem.dataRate = [300, 300].bps // DataRate for control and data packet
modem.frameLength = [32, 32].bytes 
def nodes = 1..6  
def loadRange = [0.1, 1.0, 0.1]               
def deployRange = 1.0   //In km
def slotLength  = 3.0  // In Seconds
def T = 120.minutes   
trace.warmup = 0.minutes   
//////    //////    //////


modem.preambleDuration = 0
modem.txDelay = 0
modem.clockOffset = 0.s
modem.headerLength = 0.s    
channel.model = ProtocolChannelModel    
channel.soundSpeed = 1500.mps      
channel.communicationRange = 5.km   
channel.interferenceRange = 5.km    
channel.detectionRange = 5.km       

def nodeLocation = [:]      
nodes.each{ 
    myAddr ->      
    nodeLocation[myAddr] = [rnd(0.km, deployRange.km), rnd(0.km, deployRange.km), -10.m]
}


println '''
simulation TDMAMac
===================
'''

def title = '''
TX Count\tRX Count\tLoss %\t\tOffered Load\tThroughput\tLoad
--------\t--------\t------\t\t------------\t----------\t----------'''  
println(title)
textWrite(secondFilePath,title,false) 


////// simulate at various arrival rates
for (def load = loadRange[0]; load <= loadRange[1]; load += loadRange[2]){ 
    textWrite(firstFilePath,"    ",false) 
    String subTitle = "Simulation        " + "Load:" + "${load} \n" +"txTime            " + "rxTime            " + "txAddr    " + "rxAddr    "
    textWrite(firstFilePath,subTitle,false)  
    simulate T, {
        def node_list = []
        nodes.each{ 
            myAddr ->
            float loadPerNode = load/nodes.size()    
            def newNode =   node("${myAddr}", 
                                address: myAddr, 
                                location: nodeLocation[myAddr] , 
                                shell : true,
                                mobility: false,
                                stack :{ 
                                    container ->   
                                    container.add 'mac', new TDMA(nodes.size(), myAddr-1,  slotLength)
                                    container.add 'latency', new Latency(firstFilePath)
                                    container.add 'load', new LoadGenerator(nodes-myAddr, loadPerNode)    
                                }
                            ) 
            node_list << newNode
            
        } 
    } 

    float loss = trace.txCount ? 100*trace.dropCount/trace.txCount : 0
    def content = sprintf('%6d\t\t%6d\t\t%7.4f\t\t%7.4f\t\t%7.4f\t\t%7.4f', [trace.txCount , trace.rxCount, loss, trace.offeredLoad, trace.throughput, load])
    println(content)
    textWrite(secondFilePath,content,false)

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
