/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.channel.proxy.channel;

import de.timesnake.channel.core.Host;
import de.timesnake.channel.core.ServerChannel;
import de.timesnake.channel.proxy.listener.ChannelTimeOutListener;
import de.timesnake.channel.util.ChannelConfig;
import de.timesnake.channel.util.message.ChannelHeartbeatMessage;
import de.timesnake.channel.util.message.MessageType.Heartbeat;
import de.timesnake.library.basic.util.Tuple;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ProxyChannel extends ServerChannel {

  public static ProxyChannel getInstance() {
    return (ProxyChannel) ServerChannel.getInstance();
  }

  protected final Collection<Tuple<String, Host>> pingedHosts = ConcurrentHashMap.newKeySet();
  protected final Collection<ChannelTimeOutListener> timeOutListeners = ConcurrentHashMap.newKeySet();

  public ProxyChannel(Thread mainThread, ChannelConfig config, String serverName, int serverPort) {
    super(mainThread, config, serverName, serverPort);
  }

  @Override
  protected void loadChannelClient() {
    this.client = new ProxyChannelClient(this);
  }

  @Override
  public void onHeartBeatMessage(ChannelHeartbeatMessage<?> msg) {
    super.onHeartBeatMessage(msg);
    if (msg.getMessageType().equals(Heartbeat.SERVER_PONG)) {
      this.pingedHosts.remove(new Tuple<>(((String) msg.getValue()), msg.getIdentifier()));
    }
  }

  public void addTimeOutListener(ChannelTimeOutListener listener) {
    this.timeOutListeners.add(listener);
  }

  public void ping(Collection<String> names) {
    for (String name : names) {
      Host host = this.getClient().getHostOfServer(name);

      if (host == null) {
        continue;
      }

      pingedHosts.add(new Tuple<>(name, host));
      this.client.sendMessageToHost(host, new ChannelHeartbeatMessage<>(host, Heartbeat.SERVER_PING));
    }
  }

  public void checkServerPong() {
    for (Tuple<String, Host> server : this.pingedHosts) {
      this.getClient().handleServerUnregister(server.getA(), server.getB());
    }
    for (Tuple<String, Host> server : this.pingedHosts) {
      for (ChannelTimeOutListener listener : this.timeOutListeners) {
        listener.onServerTimeOut(server.getA());
      }
    }
    this.pingedHosts.clear();
  }

  @Override
  public ProxyChannelClient getClient() {
    return (ProxyChannelClient) super.getClient();
  }

  public void setUserServer(UUID uniqueId, String server) {
    this.getClient().setUserServer(uniqueId, server);
  }
}
