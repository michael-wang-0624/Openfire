import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author swang
 * @date 2020/8/29 9:24
 **/
public class ReceiptPlugin implements Plugin, PacketInterceptor {

    private static final Logger Log = LoggerFactory.getLogger(ReceiptPlugin.class);
    private InterceptorManager interceptoerManager = null;
    private SessionManager sessionManager = null;
    @Override
    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        interceptoerManager = InterceptorManager.getInstance();
        interceptoerManager.addInterceptor(this);
        sessionManager = SessionManager.getInstance();

    }

    @Override
    public void destroyPlugin() {
        interceptoerManager.removeInterceptor(this);
    }

    @Override
    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException {
        if (!processed
            && incoming
            && packet instanceof Message
            && packet.getTo() != null
            ) {

            Message msg = (Message) packet;
            //media 改位置
            Element mediaEl = msg.getChildElement("media", "");
            String  media = null;
            if(mediaEl != null) {
                media = mediaEl.getTextTrim();
            }

            Element extendtype = msg.getElement().element("extendtype");
            if(extendtype != null) {
                return;
            }

            Element sendTime = msg.getElement().element("sendtime");
            if(sendTime == null ){
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
                msg.addChildElement("sendtime","").addText(format.format(new Date()));
            }


            JID from = packet.getFrom();

            //消息回执

            if (msg.getBody() != null || media != null) {
                Message message = new Message();
                message.setFrom(XMPPServer.getInstance().getServerInfo().getXMPPDomain());
                message.setTo(from);
                Element xEl = message.addChildElement("x", "urn:xmpp:receive");
                xEl.addText(packet.getID());


                ClientSession fromClinet = sessionManager.getSession(from);
                if(fromClinet != null)
                    fromClinet.process(message);

                Log.warn("服务器回执"+message.toXML());


                return;
            }


        }

    }
}
