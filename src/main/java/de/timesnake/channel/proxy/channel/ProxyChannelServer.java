/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.channel.proxy.channel;

import de.timesnake.channel.core.Channel;
import de.timesnake.channel.util.message.ChannelHeartbeatMessage;

public class ProxyChannelServer extends Channel.ServerChannelServer {

  protected ProxyChannelServer(Channel manager) {
    super(manager);
  }

  @Override
  protected void handlePingMessage(ChannelHeartbeatMessage<?> msg) {
    super.handlePingMessage(msg);
    ((ProxyChannel) this.manager).handlePingMessage(msg);
  }
}
