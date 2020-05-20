/**
 * @Author swang
 * @description
 * @date 15:48 2019/11/18
 **/
public enum MediaType {

    audio("音频"),

    video("视频"),

    file("文件"),

    picture("图片");
//
//    private String name;
//
//    private String getName(){
//        return this.name;
//    }
    private String value;
    MediaType(String value){
        this.value=value;
    }


    public static boolean contains(String name) {
        MediaType[] types = values();
        for (MediaType s : types) {
            if (s.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static String getValue(String mediaType) {
        MediaType[] types = values();
        for (MediaType s : types) {
            if(s.name().equals(mediaType)) {
                return s.value;
            }
        }
        return "文本";
    }



}
