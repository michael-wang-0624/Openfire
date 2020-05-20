import lombok.Data;

/**
 * @author swang
 * @date 2020/5/16 21:52
 **/
@Data
public class AppResult<T> {

    private int code;
    private String message;
    private T data;

}
