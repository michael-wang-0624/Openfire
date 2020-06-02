import lombok.Data;

/**
 * @author swang
 * @date 2020/5/17 10:00
 **/
@Data
public class MessageLast {
    String mixId;
    Integer from;
    Integer to;
    String messageId;
    Long created;
    String profile;
    Integer unReadCount;
    String avatar;
    String name;

}
