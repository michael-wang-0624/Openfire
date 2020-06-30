import io.vertx.core.Vertx;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;

import java.io.File;

/**
 * @author swang
 * @date 2020/5/16 20:59
 **/
public class HttpServicePlugin implements Plugin {
    public Vertx vertx;

    String result ;
    @Override
    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        vertx = Vertx.vertx();
        vertx.deployVerticle(RestVertical.class.getName(),re->{
            if(re.succeeded()){
                 result = re.result();
            }
        });
    }

    @Override
    public void destroyPlugin() {
        try{
            vertx.undeploy(result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
