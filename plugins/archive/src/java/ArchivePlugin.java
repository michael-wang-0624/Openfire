import org.directwebremoting.json.types.JsonObject;
import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.concurrent.Future;

/**
 * @Author swang
 * @description
 * @date 15:03 2020/5/7
 **/
public class ArchivePlugin implements Plugin,PacketInterceptor {
    private static Logger log = LoggerFactory.getLogger(ArchivePlugin.class);
    private static String SAVE_DB= "insert into ofMessage (`message_id`,`from`,`to`,`mix_id`,`media_type`,`body`,`link`,`filename`,`len`,`created`,`data`)" +
        " values (?,?,?,?,?,?,?,?,?,?,? )";
    private static String SAVE_LAST = "replace into ofMessageLast (`mix_id`,`from`,`to`,`message_id`,`created`) values (?,?,?,?,?)";
    private InterceptorManager interceptoerManager;
    private SessionManager sessionManager;
    private Client client ;
    private static String DFEALT_URL = "https://api.xms.zblzm.xyz/home/users/{userTo}/push-notification";
    private static String url= JiveGlobals.getProperty("push.url",DFEALT_URL);
    boolean debug = JiveGlobals.getBooleanProperty("plugin.isDebug", false);

    @Override
    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        interceptoerManager = InterceptorManager.getInstance();
        interceptoerManager.addInterceptor(this);
        sessionManager = SessionManager.getInstance();
        client = ClientBuilder.newClient();

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
            Element delay = msg.getChildElement("delay", "urn:xmpp:delay");
            if(delay != null) {
                return;
            }
            log.info("存储消息：{}",msg.toXML());

            Element mediaEl = msg.getChildElement("media", "");
            String  media = "text";
            if(mediaEl != null) {
                media = mediaEl.getTextTrim();
            }
             Integer toId;
            Integer fromId = null;
            Element fromEl = msg.getChildElement("fromUserId","");

            try {
                if(fromEl!=null) {
                    fromId = Integer.valueOf(fromEl.getTextTrim());
                } else {
                    fromId = Integer.valueOf(msg.getFrom().getNode());
                }
                toId = Integer.valueOf(msg.getTo().getNode());
            } catch (Exception e) {
                log.error("发送人不是数字类型",e.getMessage());
                return;
            }


            if (msg.getBody() == null && media == null) {
                return;
            }
            Element el =  msg.getChildElement("sendtime", "");
            long created =0 ;
            if(el!=null) {
                String textTrim = el.getTextTrim();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
                try {
                    created = format.parse(textTrim).getTime();

                } catch (ParseException e) {
                    e.printStackTrace();
                }
            } else {
                created = System.currentTimeMillis();
            }
            String messageId = msg.getID();
            String body = msg.getBody();
            String link = null;
            String data = null;
            int len = 0;
            String fileName = null;


            if(media != null ) {
                Element linkEl = msg.getChildElement("link", "");
                link = linkEl== null? "" :linkEl.getTextTrim();
                Element dataEl = msg.getChildElement("data", "urn:xmpp:bob");
                data = dataEl == null? "":dataEl.getTextTrim();
                Element lenEl = msg.getChildElement("len", "");
                len = lenEl == null ?  0 :  Integer.valueOf(lenEl.getTextTrim());
                Element fileNameEl = msg.getChildElement("filename", "");
                fileName = fileNameEl == null ?  "" : fileNameEl.getTextTrim();
            }
            String mixId = String.valueOf(getMixId(fromId,toId));
            saveMessage(mixId,fromId,toId,messageId,media,body,link,fileName,len,created,data);
            saveMessageLast(mixId,messageId,fromId,toId,created);
            //判断是否需要离线推送

            //boolean isPush = sessionManager.getActiveSessionCount(packet.getTo().getNode())>0 ;
            //if(!isPush) {
                WebTarget target = client.target(url).resolveTemplate("userTo",packet.getTo().getNode());
                MessageData messageData = new MessageData(fromId, media, body);
                log.info("msg:{},url:{}",body,target.getUri().toString());
                /*Future<Response> responseFuture  = */
                target.request()
                    .header("Content-Type","application/json")

                    .header("Authorization","Bearer IM_1E0ADC3949BA59ABBE56E057F20F883E")
                    .async()
                    .post(Entity.json(new MessagePush(messageData)))
                ;
               /* if (debug) {
                    try {
                        Response response = responseFuture.get();
                        log.info("got response status url='{}' status='{}'", target, response.getStatus());
                    } catch (Exception e) {
                        log.info("can't get response status url='{}'", target, e);
                    }
                }*/
            //}
        }
    }
    
    

    private void saveMessageLast(String mixId,String messageId,int from,int to,long created) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SAVE_LAST);
            pstmt.setString(1, mixId);
            pstmt.setInt(2,from );
            pstmt.setInt(3, to);
            pstmt.setString(4,messageId);
            pstmt.setLong(5,created);
            pstmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }



    private void saveMessage(String mixId,int from,int to,String messageId,String media,String body,String link,String fileName,int len,long created,String data) {

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {

           // (`message_id,`from`,`to` ,`mix_id`,`media_type`,`body`,`link`,`filename`,`len`,`created`)
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SAVE_DB);
            pstmt.setString(1, messageId);
            pstmt.setInt(2, from);
            pstmt.setInt(3, to);
            pstmt.setString(4, mixId);
            pstmt.setString(5, media);
            pstmt.setString(6, body);
            pstmt.setString(7,link);
            pstmt.setString(8,fileName);
            pstmt.setInt(9,len);
            pstmt.setLong(10,created);
            pstmt.setString(11,data);
            pstmt.execute();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    public  static  Long getMixId(int from,int to) {
        long sum = from + to;
        return sum * (sum+1)/2 + Math.min(from,to);
    }
}
