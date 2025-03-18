import org.arl.fjage.*
import org.arl.unet.*
import org.arl.unet.phy.*
import org.arl.unet.sim.*
import org.arl.unet.sim.channels.*
import static org.arl.unet.Services.*
import static org.arl.unet.phy.Physical.*
import org.arl.fjage.Agent.*

println '''
simulation CWMac
===================
'''
String firstFilePath = "/home/xxxx/Documents/Data_analysis/CWMac/Latency.txt"
String secondFilePath = "/home/xxxx/Documents/Data_analysis/CWMac/CWMacTotalCount.txt"

modem.dataRate = [80, 80].bps // control and data
modem.frameLength = [2, 32].bytes // control and data
modem.preambleDuration = 0
modem.txDelay = 0
modem.clockOffset = 0.s
modem.headerLength = 0.s    

channel.model = ProtocolChannelModel    
channel.soundSpeed = 1500.mps      
channel.communicationRange = 5.km   
channel.interferenceRange = 5.km    
channel.detectionRange = 5.km       

def nodes = 1..20                    
def T = 16.minutes                 
trace.warmup = 0.minutes          
int CWEpoch = 400 
def load = 1.0
def nodeLocation = [:]      
nodes.each{ 
    myAddr ->      
    nodeLocation[myAddr] = [rnd(0.km, 0.5.km), rnd(0.km, 0.5.km), -10.m]
}

def title = '''
TX Count\tRX Count\tLoss %\t\tOffered Load\tThroughput\tLoad\tcw
--------\t--------\t------\t\t------------\t----------\t----------\t----------'''  
println(title)
textWrite(secondFilePath,title,false) 


////// simulate at various arrival rates
for (int cw = 1; cw <= CWEpoch; cw++){ 
    simulate T,{
        def node_list = []
        nodes.each{ 
            myAddr ->
            float loadPerNode = load/nodes.size()    
            if(myAddr == 1){
            node_list << node("${myAddr}", 
                                address: myAddr, 
                                location: nodeLocation[myAddr], 
                                shell : true,
                                stack : 
                                { 
                                    container ->   
                                    container.add 'mac', new CWMac(cw)
                                    container.add 'latency', new Latency()
                                    container.add 'load', new LoadGenerator(nodes-myAddr, loadPerNode)    
                                }
                             ) 

            }
            else{
            node_list << node("${myAddr}", 
                                address: myAddr, 
                                location: nodeLocation[myAddr], 
                                stack : 
                                { 
                                    container -> 
                                    container.add 'mac', new CWMac(cw)
                                    container.add 'latency', new Latency()
                                    container.add 'load', new LoadGenerator(nodes-myAddr, loadPerNode) 
                                }
                             )
            }

        } 
    } 

    float loss = trace.txCount ? 100*trace.dropCount/trace.txCount : 0
    def content = sprintf('%6d\t\t%6d\t\t%7.4f\t\t%7.4f\t\t%7.4f\t\t%7.4f\t\t%6d', [trace.txCount, trace.rxCount, loss, trace.offeredLoad, trace.throughput, load, cw])
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





