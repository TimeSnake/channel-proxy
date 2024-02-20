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
import de.timesnake.channel.util.ChannelConfig;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.File;

@Plugin(id = "channel-proxy", name = "ChannelProxy", version = "4.0.0",
    url = "https://git.timesnake.de", authors = {"timesnake"})
public class ChannelProxy {

  public static void start() {
    Channel.setInstance(new ProxyChannel(Thread.currentThread(), config) {
      @Override
      public void runSync(Runnable runnable) {
        ChannelProxy.server.getScheduler().buildTask(getPlugin(), runnable).schedule();
      }
    });

    Configurator.setAllLevels(Channel.getInstance().getLogger().getName(), Level.WARN);
    Channel.getInstance().start();
    Channel.getInstance().selfInit();
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
  public ChannelProxy(ProxyServer server) {
    ChannelProxy.server = server;
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

  public static ChannelConfig getConfig() {
    return config;
  }
}
