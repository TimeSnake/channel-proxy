/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.channel.proxy.channel;

import de.timesnake.channel.core.Channel;
import de.timesnake.channel.core.ChannelClient;
import de.timesnake.channel.core.ChannelType;
import de.timesnake.channel.core.Host;
import de.timesnake.channel.util.message.ChannelGroupMessage;
import de.timesnake.channel.util.message.ChannelListenerMessage;
import de.timesnake.channel.util.message.ChannelMessage;
import de.timesnake.channel.util.message.ChannelServerMessage;
import de.timesnake.channel.util.message.ChannelSupportMessage;
import de.timesnake.channel.util.message.ChannelUserMessage;
import de.timesnake.channel.util.message.MessageType;
import de.timesnake.library.basic.util.Loggers;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ProxyChannelClient extends Channel.ServerChannelClient {

  //saves the server, there the user is
  protected final ConcurrentHashMap<UUID, Host> userServers = new ConcurrentHashMap<>();

  protected final Collection<Host> registeredHosts = ConcurrentHashMap.newKeySet();
  protected final ConcurrentHashMap<ChannelType<?>, ConcurrentHashMap<Object, Set<ChannelListenerMessage<?>>>>
      channelListenerByIdentifierByChannelType = new ConcurrentHashMap<>();
  protected final ConcurrentHashMap<ChannelType<?>, ConcurrentHashMap<MessageType<?>, Set<ChannelListenerMessage<?>>>>
      channelListenerByMessageTypeByChannelType = new ConcurrentHashMap<>();
  protected ConcurrentHashMap<String, Host> serverHostByName = new ConcurrentHashMap<>();

  public ProxyChannelClient(Channel manager) {
    super(manager);
    this.listenerLoaded = true;

    for (ChannelType<?> type : ChannelType.TYPES) {
      this.channelListenerByIdentifierByChannelType.put(type, new ConcurrentHashMap<>());
      this.channelListenerByMessageTypeByChannelType.put(type, new ConcurrentHashMap<>());
    }
  }

  @Override
  public void sendMessageToProxy(ChannelMessage<?, ?> message) {
    this.manager.getServer().handleMessage(message);
  }

  private boolean isHostReceivable(Host host) {
    return host != null && (this.registeredHosts.contains(host) || host.equals(
        this.manager.getProxy()));
  }

  @Override
  public void sendMessage(ChannelMessage<?, ?> message) {
    new Thread(() -> this.sendMessageSynchronized(message)).start();
  }

  @Override
  public void sendMessageSynchronized(ChannelMessage<?, ?> message) {
    if (message instanceof ChannelGroupMessage) {
      for (Host host : registeredHosts) {
        if (this.isHostReceivable(host)) {
          super.sendMessageSynchronized(host, message);
        }
      }
    } else if (message instanceof ChannelUserMessage) {
      UUID uuid = ((ChannelUserMessage<?>) message).getUniqueId();
      if (this.userServers.containsKey(uuid)) {
        Host host = this.userServers.get(uuid);
        if (this.isHostReceivable(host)) {
          super.sendMessageSynchronized(host, message);
        }
      }
    } else if (message instanceof ChannelServerMessage) {
      String name = ((ChannelServerMessage<?>) message).getName();
      Host host = this.serverHostByName.get(name);

      // send msg to server it self
      if (this.isHostReceivable(host)) {
        super.sendMessageSynchronized(host, message);
      }
    } else if (message instanceof ChannelSupportMessage) {
      String name = ((ChannelSupportMessage<?>) message).getName();
      Host host = this.serverHostByName.get(name);

      // send to server with given name
      if (this.isHostReceivable(host)) {
        super.sendMessageSynchronized(host, message);
      }
    }

    super.sendMessageSynchronized(message);
  }

  public void setUserServer(UUID uuid, String serverName) {
    if (uuid == null || serverName == null || this.serverHostByName.get(serverName) == null) {
      return;
    }
    this.userServers.put(uuid, this.serverHostByName.get(serverName));
  }

  @Override
  public void handleRemoteListenerMessage(ChannelListenerMessage<?> msg) {
    Host senderHost = msg.getSenderHost();

    // call own listeners
    this.manager.getServer().handleMessage(msg);

    if (msg.getMessageType().equals(MessageType.Listener.IDENTIFIER_LISTENER)) {
      ChannelType<?> channelType = ((MessageType.MessageIdentifierListener<?>) msg.getValue()).getChannelType();
      Object identifier = ((MessageType.MessageIdentifierListener<?>) msg.getValue()).getIdentifier();

      this.addRemoteListener(msg);

      // if register only for proxy then done
      if (channelType.equals(ChannelType.SERVER) && ((String) identifier).equalsIgnoreCase(
          Channel.PROXY_NAME)) {
        return;
      }

      // cache for other servers
      Set<ChannelListenerMessage<?>> messageList = this.channelListenerByIdentifierByChannelType
          .get(channelType)
          .computeIfAbsent(identifier, k -> ConcurrentHashMap.newKeySet());

      if (messageList.stream().noneMatch(m -> m.getSenderHost().equals(senderHost))) {
        messageList.add(msg);
      }

      this.broadcastListenerMessage(msg);
    } else if (msg.getMessageType().equals(MessageType.Listener.MESSAGE_TYPE_LISTENER)) {
      ChannelType<?> channelType = ((MessageType.MessageTypeListener) msg.getValue()).getChannelType();
      MessageType<?> messageType = ((MessageType.MessageTypeListener) msg.getValue()).getMessageType();

      this.addRemoteListener(msg);

      Set<ChannelListenerMessage<?>> messageList;
      if (messageType != null) {
        messageList = this.channelListenerByMessageTypeByChannelType
            .get(channelType)
            .computeIfAbsent(messageType, k -> ConcurrentHashMap.newKeySet());
        if (messageList.stream().noneMatch(m -> m.getSenderHost().equals(senderHost))) {
          messageList.add(msg);
        }
      } else {
        for (MessageType<?> type : channelType.getMessageTypes()) {
          messageList = this.channelListenerByMessageTypeByChannelType
              .get(channelType)
              .computeIfAbsent(type, k -> ConcurrentHashMap.newKeySet());
          if (messageList.stream().noneMatch(m -> m.getSenderHost().equals(senderHost))) {
            messageList.add(msg);
          }
        }
      }

      this.broadcastListenerMessage(msg);
    } else if (msg.getMessageType().equals(MessageType.Listener.REGISTER_SERVER)) {
      this.handleServerRegister(((String) msg.getValue()), senderHost);
    } else if (msg.getMessageType().equals(MessageType.Listener.UNREGISTER_SERVER)) {
      this.handleServerUnregister(((String) msg.getValue()), senderHost);
    } else if (msg.getMessageType().equals(MessageType.Listener.REGISTER_HOST)) {
      this.handleHostRegister(senderHost);
    } else if (msg.getMessageType().equals(MessageType.Listener.UNREGISTER_HOST)) {
      this.handleHostUnregister(senderHost);
    }
  }


  private void broadcastListenerMessage(ChannelListenerMessage<?> msg) {
    for (Host host : this.registeredHosts) {
      this.sendMessage(host, msg);
    }
  }

  /**
   * Handles server register
   * <p>
   * Sends cached listeners to server if interesting for server
   * </p>
   *
   * @param serverName
   * @param host
   */
  public void handleServerRegister(String serverName, Host host) {

    this.serverHostByName.put(serverName, host);

    Set<ChannelListenerMessage<?>> listenerMessages = this.channelListenerByIdentifierByChannelType.values()
        .stream()
        .map(Map::values).flatMap(Collection::stream).flatMap(Collection::stream)
        .filter(msg -> ChannelClient.isInterestingForServer(host, serverName, msg))
        .collect(Collectors.toSet());

    listenerMessages.addAll(
        this.channelListenerByMessageTypeByChannelType.values().stream().map(Map::values)
            .flatMap(Collection::stream).flatMap(Collection::stream)
            .filter(msg -> ChannelClient.isInterestingForServer(host, serverName, msg))
            .collect(Collectors.toSet()));

    for (ChannelListenerMessage<?> listenerMessage : listenerMessages) {
      this.sendMessageSynchronized(host, listenerMessage);
    }

    // send message proxy register to tell that all listeners are sent
    this.sendMessageSynchronized(host, new ChannelListenerMessage<>(this.manager.getProxy(),
        MessageType.Listener.REGISTER_SERVER, serverName));

    Loggers.CHANNEL.info("Send listener to " + host + " finished");
    this.registeredHosts.add(host);
  }

  public void handleServerUnregister(String serverName, Host host) {
    // broadcast unregister to all registered hosts
    this.broadcastListenerMessage(new ChannelListenerMessage<>(host,
        MessageType.Listener.UNREGISTER_SERVER, serverName));
    Loggers.CHANNEL.info("Send unregister for " + host);

    // nothing to clean up, because proxy is shutting down
    if (host.equals(this.manager.getProxy())) {
      return;
    }

    this.serverHostByName.remove(serverName);
    this.cleanupAndDisconnectHost(host);
  }


  public void handleHostRegister(Host host) {
    this.sendMessage(host, new ChannelListenerMessage<>(this.manager.getProxy(),
        MessageType.Listener.REGISTER_SERVER, Channel.PROXY_NAME));
    Loggers.CHANNEL.info("Added host " + host);
    this.registeredHosts.add(host);
  }

  public void handleHostUnregister(Host host) {

    if (host.equals(this.manager.getProxy())) {
      return;
    }

    // broadcast unregister to all registered hosts
    this.broadcastListenerMessage(
        new ChannelListenerMessage<>(host, MessageType.Listener.UNREGISTER_HOST));
    Loggers.CHANNEL.info("Send unregister for " + host);

    this.cleanupAndDisconnectHost(host);
  }

  private void cleanupAndDisconnectHost(Host host) {
    this.registeredHosts.remove(host);

    // remove cached listener messages
    this.channelListenerByIdentifierByChannelType.values().forEach(map -> map.values()
        .forEach(set -> set.removeIf(msg -> msg.getSenderHost().equals(host)))
    );

    this.channelListenerByMessageTypeByChannelType.values().forEach(map -> map.values()
        .forEach(set -> set.removeIf(msg -> msg.getSenderHost().equals(host)))
    );

    this.disconnectHost(host);
  }

  public Host getHostOfServer(String name) {
    return this.serverHostByName.get(name);
  }
}
