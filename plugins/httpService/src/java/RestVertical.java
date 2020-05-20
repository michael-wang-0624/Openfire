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
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.StringUtils;
import org.jsmpp.bean.OptionalParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author swang
 * @date 2020/5/16 21:12
 **/
public class RestVertical extends AbstractVerticle {
    private static Logger log = LoggerFactory.getLogger(RestVertical.class);
    private static String queryId = "select * from ofMessage where message_id=?";
    private static String queryList = "select * from ofMessage where mix_id=? and id< ? order by id desc limit 20";
    private static String queryLastList = "select * from ofMessage where mix_id=?  order by id desc limit 20 ";

    private static String queryLast = "select * from ofMessageLast where `from` = ? or  `to`= ? order by created desc";
    HttpServer httpServer = null;
    private UserManager userManager;

    @Override
    public void start() throws Exception {
        userManager =  XMPPServer.getInstance().getUserManager();
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
            String mixId = String.valueOf(getMixId(from, to));
            //String mixId = request.getParam("mixId");
            List messageList = getMessageList(mixId, msgId);
            HttpServerResponse response = re.response();
            response.setChunked(true);
            AppResult result = new AppResult<List>();
            result.setData(messageList);
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
            try {
                userManager.createUser(uid,password,name,"");
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
       httpServer.close();
    }
    private    Long getMixId(int from,int to) {
        long sum = from + to;
        return sum * (sum+1)/2 + Math.min(from,to);
    }

    private List getMessageLast(Integer uid) {

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
                messageLast.setFrom(rs.getInt("from"));
                messageLast.setTo(rs.getInt("to"));
                String msgId = rs.getString("message_id");
                messageLast.setMessageId(msgId);
                messageLast.setMixId(mixId);
                Message msg = getMessageById(msgId);
                String profile = "";
                if(msg != null) {
                    String body = msg.getBody();
                    String mediaType =msg.getMediaType();

                    if(mediaType != null && mediaType.length() > 0  && ! mediaType.equals("text")) {
                        profile = MediaType.getValue(mediaType);
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
                return  message;

            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return msg;
    }

    public List getMessageList(String mixId, String messageId) {

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
                    rs = pstmt.executeQuery();
                    RsToList(rs, results);
                }

            } else {
                //拉取最后20条
                pstmt = con.prepareStatement(queryLastList);
                pstmt.setString(1,mixId);
                rs = pstmt.executeQuery();
                RsToList(rs,results);
            }
        }
        catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        Collections.reverse(results);
        return results;
    }

    private void RsToList(ResultSet rs, ArrayList<Message> results) throws SQLException {
        while (rs.next()) {
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
            results.add(message);
        }
    }
}
