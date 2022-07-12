package de.timesnake.channel.proxy.main;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import de.timesnake.channel.core.ChannelLogger;
import de.timesnake.channel.core.NetworkChannel;
import de.timesnake.channel.core.SyncRun;
import de.timesnake.channel.proxy.channel.Channel;

import java.util.logging.Logger;

@Plugin(id = "channel-proxy", name = "ChannelProxy", version = "1.0-SNAPSHOT",
        url = "https://git.timesnake.de", authors = {"MarkusNils"})
public class ChannelProxy {

    public static void start(Integer port) {
        NetworkChannel.start(new Channel(Thread.currentThread(), port, port, new ChannelLogger() {
            @Override
            public void printInfo(String msg) {
                logger.info("[Channel] " + msg);
            }

            @Override
            public void printWarning(String msg) {
                logger.warning("[Channel] " + msg);
            }
        }) {
            @Override
            public void runSync(SyncRun syncRun) {
                server.getScheduler().buildTask(getPlugin(), syncRun::run).schedule();
            }
        });
    }

    public static ChannelProxy getPlugin() {
        return plugin;
    }

    private static ChannelProxy plugin;
    private static ProxyServer server;
    private static Logger logger;

    @Inject
    public ChannelProxy(ProxyServer server, Logger logger) {
        ChannelProxy.server = server;
        ChannelProxy.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        plugin = this;
        ChannelProxy.start(25565);
    }
}
