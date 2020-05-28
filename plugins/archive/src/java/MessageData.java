import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author swang
 * @date 2020/5/23 16:08
 **/
@Data
@AllArgsConstructor
public class MessageData {

        int fromUserId;
        String type;
        String body;
}
