package org.arl.unet.mac;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import org.arl.fjage.AgentID;
import org.arl.fjage.AgentLocalRandom;
import org.arl.fjage.Behavior;
import org.arl.fjage.Message;
import org.arl.fjage.OneShotBehavior;
import org.arl.fjage.Performative;
import org.arl.fjage.WakerBehavior;
import org.arl.fjage.param.Parameter;
import org.arl.unet.Capability;
import org.arl.unet.RefuseRsp;
import org.arl.unet.Services;
import org.arl.unet.UnetAgent;
import org.arl.unet.phy.PhysicalChannelParam;
import org.arl.unet.phy.PhysicalParam;
import org.arl.unet.phy.RxFrameStartNtf;
import org.arl.unet.phy.TxFrameStartNtf;



import org.arl.fjage.param.Parameter;

public enum CSMAParam implements Parameter 
{
    minBackoff, maxBackoff, reservationsPending, phy;
}



public class CSMA extends UnetAgent 
{
    public static final String title = "Carrier-sense multiple access";
    public static final String description = "Carrier-sense multiple access (MCSMA) medium access control (MAC) protocol.";
    private static final int MAX_FRAME_DURATION = 1500;
    private static final int GUARD_TIME = 500;
    private AgentLocalRandom rnd;
    private AgentID phy;
    private Queue<ReservationReq> incomingReqQ = new ArrayDeque<>();
    private boolean busy = false;
    private boolean processing = false;
    private ReservationReq current = null;
    private WakerBehavior timer = null;
    private long t0;
    private float minBackoff = 0.5F;
    private float maxBackoff = 30.0F;
    private float maxReservationDuration = 60.0F;
    private float recommendedReservationDuration = 15.0F;
    private int csbusy = 0;
    private long busyUntil = 0L;
  
    protected void setup() 
    {
        this.t0 = currentTimeMillis();
        this.rnd = AgentLocalRandom.current();
        register((Enum)Services.MAC);
        addCapability((Capability)MacCapability.TTL);
    }
  
    protected void startup() 
    {
        if (this.phy == null)
            this.phy = agentForService((Enum)Services.PHYSICAL); 
        if (this.phy == null)
            this.log.warning("No PHY found, carrier sensing disabled!"); 
        subscribe(topic(this.phy, "snoop"));
    }
  
    protected List<Parameter> getParameterList() 
    {
        return allOf(new Class[] { MacParam.class, MCSMAParam.class });
    }
  
    public boolean getChannelBusy() 
    {
        if (this.busy)
            return true; 
        if (phyBusy())
            return true; 
        return false;
    }
  
    public int getReservationsPending() 
    {
        return this.incomingReqQ.size();
    }
  
    public int getReservationPayloadSize() 
    {
        return 0;
    }
  
    public int getAckPayloadSize() 
    {
        return 0;
    }
  
    public float getMaxReservationDuration() 
    {
        return this.maxReservationDuration;
    }
  
    public void setMaxReservationDuration(float x) 
    {
        if (x < 0.0F)
            return; 
        this.maxReservationDuration = x;
    }
  
    public float getRecommendedReservationDuration() 
    {
        return this.recommendedReservationDuration;
    }
  
    public void setRecommendedReservationDuration(float x) 
    {
        if (x < 0.0F)
            return; 
        this.recommendedReservationDuration = x;
    }
  
    public float getMaxBackoff() 
    {
        return this.maxBackoff;
    }
  
    public void setMaxBackoff(float max) 
    {
        if (this.maxBackoff < 0.0F)
        return; 
        this.maxBackoff = max;
    }
  
    public float getMinBackoff() 
    {
        return this.minBackoff;
    }
  
    public void setMinBackoff(float min) 
    {
        if (this.minBackoff < 0.0F)
            return; 
        this.minBackoff = min;
    }
  
    public AgentID getPhy() 
    {
        return this.phy;
    }
  
    public AgentID setPhy(AgentID phy) 
    {
        this.phy = phy;
        return phy;
    }
  
    public String setPhy(String phy) 
    {
        if (phy == null) 
        {
            this.phy = null;
        } 
        else 
        {
            this.phy = new AgentID(phy);
        } 
        return phy;
    }
 
    protected Message processRequest(Message msg) 
    {
        if (msg instanceof ReservationReq) 
        {
            ReservationReq rmsg = (ReservationReq)msg;
            float duration = rmsg.getDuration();
            if (duration <= 0.0F || duration > this.maxReservationDuration)
            {
                return (Message)new RefuseRsp(msg, "Bad reservation duration"); 
            }
            if (rmsg.getStartTime() != null)
            {
                return (Message)new RefuseRsp(msg, "Timed reservations not supported"); 
            }
            float ttl = rmsg.getTtl();
            if (!Float.isNaN(ttl)) 
            {
                ttl += (float)(currentTimeMillis() - this.t0) / 1000.0F;
                rmsg.setTtl(ttl);
            } 
            this.log.fine("Reservation request " + rmsg.getMessageID() + " queued");
            this.incomingReqQ.add(rmsg);
            if (!this.processing)
            {
                processReservationReq(); 
            }
            return (Message)new ReservationRsp(msg);
        } 
        if (msg instanceof ReservationCancelReq) 
        {
            if (processReservationCancelReq((ReservationCancelReq)msg))
            {
                return new Message(msg, Performative.AGREE); 
            }
            return (Message)new RefuseRsp(msg, "No such reservation");
        } 
        return null;
    }
  
    protected void processMessage(Message msg) 
    {
        if (msg instanceof RxFrameStartNtf) 
        {
            Integer duration = ((RxFrameStartNtf)msg).getRxDuration();
            if (duration == null)
            {
                duration = Integer.valueOf(1500); 
            }
            this.busyUntil = currentTimeMillis() + duration.intValue() + 500L;
        } 
        if (msg instanceof org.arl.unet.phy.RxFrameNtf) 
        {
            this.busyUntil = currentTimeMillis() + 500L;
        } 
        else if (msg instanceof TxFrameStartNtf) 
        {
            Integer duration = ((TxFrameStartNtf)msg).getTxDuration();
            if (duration == null)
            {
                duration = Integer.valueOf(1500); 
            }
            this.busyUntil = currentTimeMillis() + duration.intValue() + 500L;
        } 
    }
  
    private void processReservationReq() 
    {
        if (this.incomingReqQ.isEmpty())
        {
            return; 
        }
        long backoff = computeBackoff();
        if (backoff == 0L) 
        {
            this.processing = true;
            add((Behavior)new OneShotBehavior() 
            {
                public void action() 
                {
                    MCSMA.this.grantReservationReq();
                }
            });
        } 
        else 
        {
            this.processing = true;
            add((Behavior)new WakerBehavior(backoff) 
            {
                public void onWake() 
                {
                    MCSMA.this.grantReservationReq();
                }
            });
        } 
    }
  
    private long computeBackoff() 
    {
        double backoff = (phyFrameDuration() * ((1 << this.csbusy) - 1));
        if (backoff < this.minBackoff) 
        {
            backoff = this.minBackoff;
        } 
        else if (backoff > this.maxBackoff) 
        {
            backoff = this.maxBackoff;
        } 
        if (backoff == 0.0D)
        {
            return 0L; 
        }
        return Math.round(1000.0D * this.rnd.nextDouble(0.0D, backoff));
    }

  private boolean phyBusy() 
  {
    if (this.busyUntil > currentTimeMillis())
    {
        return true; 
    }
    if (this.phy == null)
    {
        return false; 
    }
    Object rsp = get(this.phy, (Parameter)PhysicalParam.busy);
    if (rsp != null && ((Boolean)rsp).booleanValue())
    {
        return true; 
    }
    return false;
  }
  
    private float phyFrameDuration() 
    {
        if (this.phy == null)
        {
            return 1.0F; 
        }
        Object rsp = get(this.phy, 2, (Parameter)PhysicalChannelParam.frameDuration);
        if (rsp != null)
        {
            return ((Number)rsp).floatValue(); 
        }
        return 1.0F;
    }
  
    void grantReservationReq() 
    {
        if (this.incomingReqQ.isEmpty()) 
        {
            this.processing = false;
            return;
        } 
        if (phyBusy()) 
        {
            this.csbusy++;
            this.log.fine("Carrier sense BUSY: " + this.csbusy);
            this.processing = false;
            processReservationReq();
            return;
        } 
        this.csbusy = 0;
        final ReservationReq req = this.incomingReqQ.poll();
        float ttl = req.getTtl();
        if (!Float.isNaN(ttl) && ttl < (float)(currentTimeMillis() - this.t0) / 1000.0F) 
        {
            this.log.fine("Reservation request " + req.getMessageID() + " ttl expired");
            this.processing = false;
            send((Message)createNtfMsg(req, ReservationStatus.FAILURE));
            processReservationReq();
            return;
        } 
        this.log.fine("Reservation request " + req.getMessageID() + " granted");
        this.current = req;
        this.busy = true;
        send((Message)createNtfMsg(req, ReservationStatus.START));
        this.timer = new WakerBehavior((long)Math.ceil((req.getDuration() * 1000.0F))) 
        {
            public void onWake() 
            {
                this.log.fine("Reservation request " + req.getMessageID() + " completed");
                MCSMA.this.send((Message)MCSMA.this.createNtfMsg(req, ReservationStatus.END));
                MCSMA.this.current = null;
                MCSMA.this.busy = false;
                MCSMA.this.processing = false;
                MCSMA.this.timer = null;
                MCSMA.this.processReservationReq();
            }
        };
        add((Behavior)this.timer);
    }
  
    private boolean processReservationCancelReq(ReservationCancelReq msg) 
    {
    String id = msg.getId();
    if (this.current != null && (id == null || id.equals(this.current.getMessageID()))) 
    {
        if (this.timer != null)
        {
            this.timer.stop(); 
        }
        add((Behavior)new OneShotBehavior() 
        {
            public void action() 
            {
                this.log.fine("Ongoing reservation request cancelled");
                MCSMA.this.send((Message)MCSMA.this.createNtfMsg(MCSMA.this.current, ReservationStatus.END));
                MCSMA.this.current = null;
                MCSMA.this.busy = false;
                MCSMA.this.processing = false;
                MCSMA.this.timer = null;
                MCSMA.this.processReservationReq();
            }
        });
        return true;
    } 
    if (this.current != null)
    {
        return false; 
    }
    Iterator<ReservationReq> iter = this.incomingReqQ.iterator();
    while (iter.hasNext()) 
    {
        ReservationReq req = iter.next();
        if (req.getMessageID().equals(id)) 
        {
            this.log.fine("Reservation request " + id + " cancelled");
            iter.remove();
            send((Message)createNtfMsg(req, ReservationStatus.CANCEL));
            return true;
        } 
    } 
    return false;
    }
  
    private ReservationStatusNtf createNtfMsg(ReservationReq req, ReservationStatus status) 
    {
        ReservationStatusNtf ntfMsg = new ReservationStatusNtf(req);
        ntfMsg.setStatus(status);
        trace((Message)req, (Message)ntfMsg);
        return ntfMsg;
    }
}
