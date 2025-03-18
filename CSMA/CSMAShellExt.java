package org.arl.unet.mac;

import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import groovy.transform.Generated;
import groovy.transform.Internal;
import java.lang.ref.SoftReference;
import org.arl.fjage.shell.ShellExtension;
import org.codehaus.groovy.reflection.ClassInfo;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;

public class CSMAShellExt implements ShellExtension//, GroovyObject 
{
    public static String __doc__;
  /* 
    @Generated
    public MCSMAShellExt() 
    {
        MetaClass metaClass = $getStaticMetaClass();
        this.metaClass = metaClass;
    }
*/
    static 
    {
        String str = "## MCSMA MAC parameters:\n\n### mac.phy - physical agent used for carrier sensing\n### mac.minBackoff - minimum backoff window (seconds)\n### mac.maxBackoff - maximum backoff window (seconds)\n### mac.reservationsPending - number of reservations in queue (read-only)\n";
        __doc__ = str;
    }
    
}
