import lombok.Data;

/**
 * @author swang
 * @date 2020/5/17 8:36
 **/
@Data
public class Message {

    String messageId;
    Integer from;
    Integer to;
    String mixId;
    String mediaType="text";
    String body;
    String link="";
    String fileName="";
    Integer len;
    Long created;
    String data="";
    Integer deleteById = 0;

}
