import com.fasterxml.jackson.databind.SerializationFeature;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.StringUtils;
import org.jsmpp.bean.OptionalParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * @author swang
 * @date 2020/5/16 21:12
 **/
public class RestVertical extends AbstractVerticle {
    private static Logger log = LoggerFactory.getLogger(RestVertical.class);
    private static String queryId = "select * from ofMessage where message_id=?";
    private static String queryList = "select * from ofMessage where mix_id=? and id< ? and ( deleteById IS  NULL OR deleteById <> ? ) order by id desc limit 20";
    private static String queryLastList = "select * from ofMessage where mix_id=? and ( deleteById IS  NULL OR deleteById <> ? ) order by id desc limit 20 ";
    private static String queryUnRead  = "select count(*) from ofOffline  where username=? and mixId=?";
    private static String deleteUnRead = "delete from ofOffline where username=?  ";
    private static String deleteByMixIdUnRead = "delete from ofOffline where username=? and mixId=?  ";
    private static String queryLast = "select * from ofMessageLast where `from` = ? or  `to`= ? order by created desc";
    private static String updateFrom = "update ofMessage set `from`=? where mix_id=? and `from`=? ";
    private static String updateTo = "update ofMessage set `to`=? where mix_id=? and `to`=? ";
    private static String updateMixId = "update ofMessage set mix_id=? where mix_id=?";
    private static String queryMixId= "select * from ofMessageLast where mix_id=?";
    private static String updateLast = "update  ofMessageLast set mix_id=? ,`from`=?,`to`=? where mix_id=?";
    private static String retractMsg = "update ofMessage set media_type='retract' where message_id=?";

    private static String deleteMessage = "delete from ofMessage where mix_id=?";
    private static String deleteMessageLast = "delete from ofMessageLast where  mix_id=?";
    private static String deleteOffline = "delete from ofOffline where mixId=?";
    private static String deleteMsgById = "delete from ofMessage where message_id = ?";
    private static String updateMessageLast = "UPDATE ofMessageLast SET message_id = (SELECT message_id FROM ofMessage WHERE mix_id=? ORDER BY created DESC LIMIT 1) WHERE mix_id = ?";
    /**
     *  单向删除消息
     */
    private static String deleteMsgSigle = "update ofMessage set deleteById = ? where message_id=?";

    /**
     *  获取最后一个不是删除消息的消息
     */
    private static String queryLastUnDeleteMsg = "select * from ofMessage where ( deleteById IS  NULL OR deleteById <> ? )  and  mix_id=? order by id desc limit 1";

    HttpServer httpServer = null;
    private UserManager userManager;
    private SessionManager sessionManager;

    @Override
    public void start() throws Exception {
        userManager =  XMPPServer.getInstance().getUserManager();
        sessionManager = XMPPServer.getInstance().getSessionManager();
        httpServer = vertx.createHttpServer();
        Router reAtapi =  Router.router(vertx);
        //解决跨域问题
        reAtapi.route().handler(CorsHandler.create("*")
            .allowedMethod(HttpMethod.GET)
            .allowedMethod(HttpMethod.POST)
            .allowedMethod(HttpMethod.PUT)
            .allowedMethod(HttpMethod.DELETE)
            .allowedHeader("X-PINGARUNER").allowedHeader("Content-Type"));
        reAtapi.route().handler(BodyHandler.create());

        reAtapi.route("/history").method(HttpMethod.GET).handler(re->{
            HttpServerRequest request = re.request();
            String msgId = request.getParam("messageId");
            int from = Integer.valueOf(request.getParam("from"));
            int to = Integer.valueOf(request.getParam("to"));
            //String mixId = request.getParam("mixId");
            History history= getMessageList(from,to, msgId);
            HttpServerResponse response = re.response();
            response.setChunked(true);
            AppResult result = new AppResult<List>();
            result.setData(history);
            result.setCode(200);
            result.setMessage("操作成功");
            response.write(Json.encodePrettily(result)).setStatusCode(200).putHeader("content-type", "application/json;charset=UTF-8").end();
        });

        reAtapi.route("/msgLast").method(HttpMethod.GET).handler(re->{
            HttpServerRequest request = re.request();
            String uid = request.getParam("uid");
            HttpServerResponse response = re.response();
            response.setChunked(true);
            AppResult result = new AppResult<List>();
            List messageLast = getMessageLast(Integer.valueOf(uid));
            result.setData(messageLast);
            result.setCode(200);
            result.setMessage("操作成功");
            response.write(Json.encodePrettily(result)).setStatusCode(200).putHeader("content-type", "application/json;charset=UTF-8").end();
        });

        reAtapi.route("/createUser").method(HttpMethod.POST).handler(re->{
            AppResult result = new AppResult<>();
            HttpServerRequest request = re.request();
            String uid = request.getParam("uid");
            String password = request.getParam("password");
            String name = request.getParam("name");
            String avatar = request.getParam("avatar");
            try {
                userManager.createUser(uid,password,name,avatar);
                result.setCode(200);
                result.setMessage("请求成功");
            } catch (UserAlreadyExistsException e) {
                e.printStackTrace();
                result.setCode(400);
                result.setMessage("用户已经存在");
            }
            HttpServerResponse response = re.response();
            response.setStatusCode(200).setChunked(true).putHeader("content-type", "application/json;charset=UTF-8").end(Json.encodePrettily(result));

        });

        reAtapi.route("/deleteMsg").method(HttpMethod.POST).handler(re->{
            AppResult result = new AppResult<>();
            HttpServerRequest request = re.request();
            String uid = request.getParam("uid");
            String msgId = request.getParam("msgId");
            Message msg = getMessageById(msgId);
            if(!org.apache.commons.lang3.StringUtils.equalsAny(uid,msg.getFrom().toString(),msg.getTo().toString())) {
                result.setCode(201);
                result.setMessage("此消息不能被该用户删除");
            }

            if(msg.getDeleteById() != 0 && !msg.getDeleteById().toString().equals(uid)) {
                //删除消息
                deleteMsgById(msg);
            } else {
                //设置deletebyid
                deleteMsgSigle(uid,msgId);
            }


            result.setCode(200);
            result.setMessage("请求成功");

            HttpServerResponse response = re.response();
            response.setStatusCode(200).setChunked(true).putHeader("content-type", "application/json;charset=UTF-8").end(Json.encodePrettily(result));

        });

        reAtapi.route("/updateAvatar").method(HttpMethod.POST).handler(re->{
            AppResult result = new AppResult<>();
            HttpServerRequest request = re.request();
            String uid = request.getParam("uid");
            String avatar = request.getParam("avatar");
            try {
                userManager.setEmail(uid,avatar);
                result.setCode(200);
                result.setMessage("请求成功");
            } catch (UserNotFoundException e) {
                e.printStackTrace();
                result.setCode(400);
                result.setMessage("用户不存在");
            }
            HttpServerResponse response = re.response();
            response.setStatusCode(200).setChunked(true).putHeader("content-type", "application/json;charset=UTF-8").end(Json.encodePrettily(result));

        });

        reAtapi.route("/updateChatting").method(HttpMethod.POST).handler(re->{
            AppResult result = new AppResult<>();
            HttpServerRequest request = re.request();
            int uid = Integer.valueOf(request.getParam("uid"));//当前的uid
            int wihtUid = Integer.valueOf(request.getParam("withUid"));//客户的uid
            int updateUid = Integer.valueOf(request.getParam("updateUid"));//需要修改客服的uid
            String orginMixId = String.valueOf(getMixId(wihtUid, updateUid));
            String nowMixId = String.valueOf(getMixId(uid,wihtUid));
            updateMixIdAndChat(updateUid,uid,orginMixId,nowMixId);
            HttpServerResponse response = re.response();
            result.setCode(200);
            result.setMessage("请求成功");
            response.setStatusCode(200).setChunked(true).putHeader("content-type", "application/json;charset=UTF-8").end(Json.encodePrettily(result));

        });
        reAtapi.route("/deleteUnReadByMixId").method(HttpMethod.POST).handler(re->{
            AppResult result = new AppResult<>();
            HttpServerRequest request = re.request();
            String mixId = request.getParam("mixId");
            String uid = request.getParam("uid");
            deleteUnreadByMixId(uid,mixId);
            result.setCode(200);
            result.setMessage("请求成功");

            HttpServerResponse response = re.response();
            response.setStatusCode(200).setChunked(true).putHeader("content-type", "application/json;charset=UTF-8").end(Json.encodePrettily(result));

        });

        reAtapi.route("/deleteChatting").method(HttpMethod.POST).handler(re->{
            AppResult result = new AppResult<>();
            HttpServerRequest request = re.request();
            int uid = Integer.valueOf(request.getParam("uid"));//当前的uid
            int targetUid = Integer.valueOf(request.getParam("targetUid"));//对方的uid
            String mixId = String.valueOf(getMixId(uid, targetUid));
            deleteChatting(mixId);
            Collection<ClientSession> sessions = sessionManager.getSessions(String.valueOf(targetUid));
            if(!sessions.isEmpty()) {
                sessions.forEach(session ->{
                    IQ iq = new IQ();
                    iq.setFrom(String.valueOf(targetUid).concat("@ai"));
                    iq.setType(IQ.Type.set);
                    iq.setID(String.valueOf(System.currentTimeMillis())+new Random().nextInt(100));
                    iq.setChildElement("extendtype","").addText("deleteChating");
                    session.process(iq);
                });
            }

            HttpServerResponse response = re.response();
            result.setCode(200);
            result.setMessage("请求成功");
            response.setStatusCode(200).setChunked(true).putHeader("content-type", "application/json;charset=UTF-8").end(Json.encodePrettily(result));



        });

        reAtapi.route("/updateName").method(HttpMethod.POST).handler(re->{
            AppResult result = new AppResult<>();
            HttpServerRequest request = re.request();
            String uid = request.getParam("uid");
            String name = request.getParam("name");
            try {
                userManager.setName(uid,name);
                result.setCode(200);
                result.setMessage("请求成功");
            } catch (UserNotFoundException e) {
                e.printStackTrace();
                result.setCode(400);
                result.setMessage("用户不存在");
            }
            HttpServerResponse response = re.response();
            response.setStatusCode(200).setChunked(true).putHeader("content-type", "application/json;charset=UTF-8").end(Json.encodePrettily(result));

        });

        reAtapi.route("/retract").method(HttpMethod.POST).handler(re->{
            AppResult result = new AppResult<>();
            HttpServerRequest request = re.request();
            String uid = request.getParam("uid");

            String msgId = request.getParam("msgId");

            Message message = getMessageById(msgId);
            if(message == null) {
                result.setCode(400);
                result.setMessage("消息不存在");
            }
            if(message != null) {
                if(message.getFrom().intValue() !=  Integer.valueOf(uid).intValue() ) {
                    result.setMessage("非消息发送人无法撤回");
                    result.setCode(400);

                } else {
                    Long created = message.getCreated();
                    if(created- System.currentTimeMillis() > 180000) {
                        result.setMessage("超过3分钟，无法撤回");
                        result.setCode(400);
                    } else {
                        retractMsg(message.getMessageId());
                        message.setMediaType("retract");
                        int to = message.getTo();
                        Collection<ClientSession> sessions = sessionManager.getSessions(String.valueOf(to));
                        if(sessions != null) {
                            sessions.forEach(o->{
                                IQ iq = new IQ();
                                iq.setFrom(String.valueOf(uid).concat("@ai"));
                                iq.setType(IQ.Type.set);
                                iq.setID(String.valueOf(System.currentTimeMillis())+new Random().nextInt(100));
                                iq.setChildElement("extendtype","").addText("retract");
                                o.process(iq);
                            });

                        }

                        message.setBody("");
                        result.setCode(200);
                        result.setMessage("请求成功");
                    }

                }
            }
            HttpServerResponse response = re.response();
            response.setStatusCode(200).setChunked(true).putHeader("content-type", "application/json;charset=UTF-8").end(Json.encodePrettily(result));

        }) ;



        reAtapi.route().failureHandler(re->{
            AppResult result = new AppResult<>();
            result.setMessage("请求失败");
            HttpServerResponse response = re.response();
            response.setStatusCode(400).putHeader("content-type", "application/json;charset=UTF-8").end(Json.encodePrettily(result));
        });

        httpServer.requestHandler(reAtapi::accept).listen(8078);
     }

    @Override
    public void stop() throws Exception {
      // httpServer.close();
    }
    private    Long getMixId(int from,int to) {
        long sum = from + to;
        return sum * (sum+1)/2 + Math.min(from,to);
    }

    private void updateMixIdAndChat(int oldUid,int nowUid,String originMixId,String nowMixId) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        boolean abortTransaction = false;

        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt  =con.prepareStatement(updateFrom);
            pstmt.setInt(1,nowUid);
            pstmt.setString(2,originMixId);
            pstmt.setInt(3,oldUid);
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            pstmt = con.prepareStatement(updateTo);
            pstmt.setInt(1,nowUid);
            pstmt.setString(2,originMixId);
            pstmt.setInt(3,oldUid);
            pstmt.executeUpdate();

            DbConnectionManager.fastcloseStmt(pstmt);
            pstmt = con.prepareStatement(updateMixId);
            pstmt.setString(1,nowMixId);
            pstmt.setString(2,originMixId);
            pstmt.executeUpdate();

            DbConnectionManager.fastcloseStmt(pstmt);
            pstmt = con.prepareStatement(queryMixId);
            pstmt.setString(1,originMixId);
            rs = pstmt.executeQuery();
            if(rs.next()){
                String mix_id = rs.getString("mix_id");
                int from = rs.getInt("from");
                int to = rs.getInt("to");
                DbConnectionManager.fastcloseStmt(pstmt);
                pstmt = con.prepareStatement(updateLast);
                pstmt.setString(1,nowMixId);
                pstmt.setInt(2,from==oldUid? nowUid:from);
                pstmt.setInt(3,to==oldUid? nowUid:to);
                pstmt.setString(4,mix_id);
                pstmt.executeUpdate();
            }

        }catch (SQLException e) {
            log.error(e.getMessage(), e);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);

        }

    }

    private void deleteChatting(String mixId) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt  =con.prepareStatement(deleteMessage);
            pstmt.setString(1,mixId);
            pstmt.executeUpdate();
            pstmt.clearParameters();
            pstmt = con.prepareStatement(deleteMessageLast);
            pstmt.setString(1,mixId);
            pstmt.executeUpdate();
            pstmt.clearParameters();
            pstmt = con.prepareStatement(deleteOffline);
            pstmt.setString(1,mixId);
            pstmt.executeUpdate();
        }catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection( pstmt, con);
        }
    }

    private void retractMsg(String messageId) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt  =con.prepareStatement(retractMsg);
            pstmt.setString(1,messageId);
            pstmt.executeUpdate();

        }catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection( pstmt, con);
        }
    }

    private Integer getCountByMixId(String username,String mixId){
        int size=0;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt  =con.prepareStatement(queryUnRead);
            pstmt.setString(1,username);
            pstmt.setString(2,mixId);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                size = rs.getInt(1);
            }
         }catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return size;
    }

    private void deleteMsgSigle(String uid,String msgId) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt  =con.prepareStatement(deleteMsgSigle);
            pstmt.setString(1,uid);
            pstmt.setString(2,msgId);
            pstmt.executeUpdate();

        }catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection( pstmt, con);
        }
    }


    private void deleteMsgById(Message msg) {
        Connection con = null;
        PreparedStatement pstmt = null;
         try {
            con = DbConnectionManager.getConnection();
            pstmt  =con.prepareStatement(deleteMsgById);
            pstmt.setString(1,msg.getMessageId());
            String mixId = msg.getMixId();
            pstmt.clearParameters();
            pstmt = con.prepareStatement(updateMessageLast);
            pstmt.setString(1,mixId);
            pstmt.setString(2,mixId);
            pstmt.executeUpdate();
        }catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection( pstmt, con);
        }
    }
    private void deleteUnreadByMixId(String uid,String mixId) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt  =con.prepareStatement(deleteByMixIdUnRead);
            pstmt.setString(1,uid);
            pstmt.setString(2,mixId);
            pstmt.executeUpdate();
        }catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection( pstmt, con);
        }
    }




    private List getMessageLast(Integer uid)  {

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        ArrayList<MessageLast> messageLasts = new ArrayList<>();
        try {
            con = DbConnectionManager.getConnection();
            pstmt  =con.prepareStatement(queryLast);
            pstmt.setInt(1,uid);
            pstmt.setInt(2,uid);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                MessageLast messageLast = new MessageLast();
                String mixId = rs.getString("mix_id");
                messageLast.setCreated(rs.getLong("created"));
                int from = rs.getInt("from");
                messageLast.setFrom(from);
                int to = rs.getInt("to");
                messageLast.setTo(to);
                String msgId = rs.getString("message_id");
                messageLast.setMessageId(msgId);
                messageLast.setMixId(mixId);
                int unReadCount = getCountByMixId(String.valueOf(uid), mixId);
                messageLast.setUnReadCount(unReadCount);
                String avatar ="";
                String name = "";
                try {
                    User user = userManager.getUser(String.valueOf(uid.intValue() == from ? to : from));
                    avatar = user.getEmail();
                    name = user.getName();

                } catch (UserNotFoundException e) {
                    e.printStackTrace();
                }
                messageLast.setName(name);
                messageLast.setAvatar(avatar);
                Message msg = getMessageById(msgId);


                String profile = "";
                if(msg != null) {
                    if(uid.intValue() == msg.getDeleteById()) {
                        //过滤此消息
                        msg = queryLastUndeleteMsg(msg);
                    }
                    String body = msg.getBody();
                    String mediaType =msg.getMediaType();

                    if(mediaType != null && mediaType.length() > 0  && ! mediaType.equals("text")) {
                        if("retract".equals(mediaType)){
                            if(uid.equals(msg.getFrom()) ) {
                                profile = "คุณถอนข้อความ";
                            } else  {
                                profile = "อีกฝ่ายถอนข้อความ";
                            }
                        } else {
                            profile = MediaType.getValue(mediaType);
                        }
                    } else if(body.length()>0) {
                        String trim = body.trim();
                        if(trim.length() >20 ){
                            profile = trim.substring(0,20);
                        }else {
                            profile = trim;
                        }
                    }
                    messageLast.setProfile(profile);
                }


                messageLasts.add(messageLast);
            }
             //pstmt = con.prepareStatement(deleteUnRead);
             //pstmt.setString(1,String.valueOf(uid));
            //pstmt.execute();
         }catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
        finally {
                DbConnectionManager.closeConnection(rs, pstmt, con);
            }

            return messageLasts;
    }



    private Message getMessageById(String messageId) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Message msg = null;
         try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(queryId);
            pstmt.setString(1, messageId);
             rs = pstmt.executeQuery();
            if (rs.next()) {
                Message message = new Message();
                message.setBody(rs.getString("body"));
                message.setCreated(rs.getLong("created"));
                message.setFrom(rs.getInt("from"));
                message.setTo(rs.getInt("to"));
                message.setMediaType(rs.getString("media_type"));
                message.setMessageId(rs.getString("message_id"));
                message.setMixId(rs.getString("mix_id"));
                message.setFileName(rs.getString("filename"));
                message.setData(rs.getString("data"));
                message.setLen(rs.getInt("len"));
                message.setLink(rs.getString("link"));
                int deleteById = rs.getInt("deleteById");
                message.setDeleteById(deleteById);
                return  message;

            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return msg;
    }

    private Message queryLastUndeleteMsg(Message msg) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(queryLastUnDeleteMsg);
            pstmt.setInt(1, msg.getDeleteById());
            pstmt.setString(2,msg.getMixId());
            rs = pstmt.executeQuery();
            if (rs.next()) {
                Message message = new Message();
                message.setBody(rs.getString("body"));
                message.setCreated(rs.getLong("created"));
                message.setFrom(rs.getInt("from"));
                message.setTo(rs.getInt("to"));
                message.setMediaType(rs.getString("media_type"));
                message.setMessageId(rs.getString("message_id"));
                message.setMixId(rs.getString("mix_id"));
                message.setFileName(rs.getString("filename"));
                message.setData(rs.getString("data"));
                message.setLen(rs.getInt("len"));
                message.setLink(rs.getString("link"));
                int deleteById = rs.getInt("deleteById");
                message.setDeleteById(deleteById);
                return  message;

            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return msg;
    }

    public History getMessageList(int from,int to, String messageId) {
        String mixId = String.valueOf(getMixId(from, to));

        History history = new History();
        User user = null;
        try {
            user = userManager.getUser(String.valueOf(to));
        } catch (UserNotFoundException e) {
            e.printStackTrace();
        }
        if (user != null) {
            history.setAvatar(user.getEmail());
            history.setName(user.getName());
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Integer id ;
        ArrayList<Message> results = new ArrayList<>();
        try {
            con = DbConnectionManager.getConnection();
            if(messageId != null) {
                pstmt = con.prepareStatement(queryId);
                pstmt.setString(1,messageId);
                rs = pstmt.executeQuery();
                if (rs.next()) {
                    id = rs.getInt("id");
                    DbConnectionManager.closeResultSet(rs);
                    DbConnectionManager.fastcloseStmt(pstmt);
                    pstmt = con.prepareStatement(queryList);
                    pstmt.setString(1,mixId);
                    pstmt.setInt(2,id);
                    pstmt.setInt(3,from);
                    rs = pstmt.executeQuery();
                    RsToList(rs, results,from);
                }

            } else {
                //拉取最后20条
                pstmt = con.prepareStatement(queryLastList);
                pstmt.setString(1,mixId);
                pstmt.setInt(2,from);
                rs = pstmt.executeQuery();
                RsToList(rs,results,from);
            }
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        Collections.reverse(results);
        history.setMessages(results);
        return history;
    }

    private void RsToList(ResultSet rs, ArrayList<Message> results,int from) throws SQLException {
        while (rs.next()) {
            Message message = new Message();
            String mediaType = rs.getString("media_type");

            message.setBody(rs.getString("body"));
            message.setCreated(rs.getLong("created"));
            message.setFrom(rs.getInt("from"));
            message.setTo(rs.getInt("to"));
            message.setMediaType(rs.getString("media_type"));
            message.setMessageId(rs.getString("message_id"));
            message.setMixId(rs.getString("mix_id"));
            message.setFileName(rs.getString("filename"));
            message.setData(rs.getString("data"));
            message.setLen(rs.getInt("len"));
            message.setLink(rs.getString("link"));
            if("retract".equals(mediaType)) {
                if(from == message.getFrom()) {
                    message.setBody("您撤回了一条消息");
                } else  {
                    message.setBody("对方撤回了一条消息");
                }
            }
            results.add(message);
        }
    }
}
