package de.timesnake.channel.proxy.main;

import de.timesnake.channel.core.ChannelLogger;
import de.timesnake.channel.core.NetworkChannel;
import de.timesnake.channel.core.SyncRun;
import de.timesnake.channel.proxy.channel.Channel;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public class ChannelProxy extends Plugin {

    private static ChannelProxy plugin;

    @Override
    public void onEnable() {
        plugin = this;
        ChannelProxy.start(25565);
    }

    public static void start(Integer port) {
        NetworkChannel.start(new Channel(Thread.currentThread(), port, port, new ChannelLogger() {
            @Override
            public void printInfo(String msg) {
                ProxyServer.getInstance().getLogger().info("[Channel] " + msg);
            }

            @Override
            public void printWarning(String msg) {
                ProxyServer.getInstance().getLogger().warning("[Channel] " + msg);
            }
        }) {
            @Override
            public void runSync(SyncRun syncRun) {
                getPlugin().getProxy().getScheduler().schedule(getPlugin(), syncRun::run, 0, TimeUnit.MILLISECONDS);
            }
        });
    }

    public static ChannelProxy getPlugin() {
        return plugin;
    }
}
