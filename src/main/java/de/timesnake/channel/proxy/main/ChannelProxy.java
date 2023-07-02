/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.channel.proxy.main;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import de.timesnake.channel.core.Channel;
import de.timesnake.channel.core.SyncRun;
import de.timesnake.channel.proxy.channel.ProxyChannel;
import de.timesnake.channel.util.ChannelConfig;
import de.timesnake.library.basic.util.Loggers;

import java.io.File;
import java.util.logging.Logger;

@Plugin(id = "channel-proxy", name = "ChannelProxy", version = "2.0-SNAPSHOT",
    url = "https://git.timesnake.de", authors = {"MarkusNils"})
public class ChannelProxy {
  public static final int PORT = 25565;

  public static void start() {
    Channel.setInstance(new ProxyChannel(Thread.currentThread(), config, config.getProxyServerName(), PORT) {
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
  private static ChannelConfig config;

  @Inject
  public ChannelProxy(ProxyServer server, Logger logger) {
    ChannelProxy.server = server;

    Loggers.CHANNEL.setUseParentHandlers(false);
  }

  @Subscribe
  public void onProxyInitialization(ProxyInitializeEvent event) {
    plugin = this;

    Toml toml = new Toml().read(new File("plugins/channel/config.toml"));

    config = new ChannelConfig() {
      @Override
      public String getServerHostName() {
        return toml.getString("host_name");
      }

      @Override
      public String getListenHostName() {
        return toml.getString("listen_host_name");
      }

      @Override
      public String getProxyHostName() {
        return toml.getString("proxy.host_name");
      }

      @Override
      public String getProxyServerName() {
        return toml.getString("proxy.server_name");
      }

      @Override
      public int getPortOffset() {
        return toml.getLong("port_offset").intValue();
      }

      @Override
      public int getProxyPort() {
        return toml.getLong("proxy.port").intValue();
      }
    };

    ChannelProxy.start();
  }
}
