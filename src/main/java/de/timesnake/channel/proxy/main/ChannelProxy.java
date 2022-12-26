/*
 * Copyright (C) 2022 timesnake
 */

package de.timesnake.channel.proxy.main;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import de.timesnake.channel.core.Channel;
import de.timesnake.channel.core.SyncRun;
import de.timesnake.channel.proxy.channel.ProxyChannel;
import java.util.logging.Logger;

@Plugin(id = "channel-proxy", name = "ChannelProxy", version = "1.0-SNAPSHOT",
        url = "https://git.timesnake.de", authors = {"MarkusNils"})
public class ChannelProxy {

    public static void start(Integer port) {
        Channel.setInstance(new ProxyChannel(Thread.currentThread(), port, port) {
            @Override
            public void runSync(SyncRun syncRun) {
                ChannelProxy.server.getScheduler().buildTask(getPlugin(), syncRun::run).schedule();
            }
        });

        Channel.getInstance().start();
    }

    public static void stop() {
        if (Channel.getInstance() != null) {
            Channel.getInstance().stop();
        }
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

        de.timesnake.channel.util.Channel.LOGGER.setUseParentHandlers(false);
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        plugin = this;
        ChannelProxy.start(25565);
    }
}
