import lombok.Data;

import java.util.List;

/**
 * @author swang
 * @date 2020/5/29 21:24
 **/
@Data
public class History {
    List<Message> messages;
    String avatar;
    String name;
}

