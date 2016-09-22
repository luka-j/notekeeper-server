import com.google.inject.AbstractModule;

/**
 * Created by luka on 8.2.16..
 */
public class OnStartModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Global.class).asEagerSingleton();
    }
}
