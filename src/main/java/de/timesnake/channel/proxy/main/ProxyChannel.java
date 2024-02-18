/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.channel.proxy.main;

import de.timesnake.channel.core.Channel;
import de.timesnake.channel.core.ChannelParticipant;
import de.timesnake.channel.util.ChannelConfig;
import de.timesnake.channel.util.listener.ChannelHandler;
import de.timesnake.channel.util.listener.ChannelListener;
import de.timesnake.channel.util.listener.ListenerType;
import de.timesnake.channel.util.message.ChannelServerMessage;
import de.timesnake.channel.util.message.MessageType;
import de.timesnake.channel.util.message.VoidMessage;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ProxyChannel extends Channel implements ChannelListener {

  public static ProxyChannel getInstance() {
    return (ProxyChannel) Channel.getInstance();
  }

  protected final Collection<String> pingedServers = ConcurrentHashMap.newKeySet();
  protected final Collection<ChannelTimeOutListener> timeOutListeners = ConcurrentHashMap.newKeySet();

  public ProxyChannel(Thread mainThread, ChannelConfig config) {
    super(mainThread, new ChannelParticipant(config.getServerHostName(), config.getProxyPort()),
        config.getListenHostName());

    this.addListener(this);
  }

  public void addTimeOutListener(ChannelTimeOutListener listener) {
    this.timeOutListeners.add(listener);
  }

  @ChannelHandler(type = ListenerType.SERVER_PONG)
  public void onServerPong(ChannelServerMessage<VoidMessage> msg) {
    this.pingedServers.remove(msg.getIdentifier());
  }

  public void ping(Collection<String> names) {
    pingedServers.addAll(names);
    for (String name : names) {
      this.sendMessage(new ChannelServerMessage<>(name, MessageType.Server.PING));
    }
  }

  public void checkServerPong() {
    for (String name : this.pingedServers) {
      for (ChannelTimeOutListener listener : this.timeOutListeners) {
        listener.onServerTimeOut(name);
      }
    }
    this.pingedServers.clear();
  }
}
