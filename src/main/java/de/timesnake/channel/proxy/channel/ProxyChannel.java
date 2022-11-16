/*
 * workspace.channel-proxy.main
 * Copyright (C) 2022 timesnake
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; If not, see <http://www.gnu.org/licenses/>.
 */

package de.timesnake.channel.proxy.channel;

import de.timesnake.channel.core.Channel;
import de.timesnake.channel.core.ChannelType;
import de.timesnake.channel.core.Host;
import de.timesnake.channel.proxy.listener.ChannelTimeOutListener;
import de.timesnake.channel.util.message.*;
import de.timesnake.library.basic.util.Tuple;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public abstract class ProxyChannel extends de.timesnake.channel.core.Channel {

    //saves the server, there the user is
    protected final ConcurrentHashMap<UUID, Host> userServers = new ConcurrentHashMap<>();

    protected final ConcurrentHashMap<ChannelType<?>, ConcurrentHashMap<Object, Set<ChannelListenerMessage<?>>>>
            channelListenerByIdentifierByChannelType = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<ChannelType<?>, ConcurrentHashMap<MessageType<?>, Set<ChannelListenerMessage<?>>>>
            channelListenerByMessageTypeByChannelType = new ConcurrentHashMap<>();

    //online servers, with an active channel
    protected final Collection<Host> registeredHosts = ConcurrentHashMap.newKeySet();

    protected final Collection<ChannelTimeOutListener> timeOutListeners = ConcurrentHashMap.newKeySet();
    private final PingPong ping = new PingPong();

    protected ConcurrentHashMap<String, Host> serverHostByName = new ConcurrentHashMap<>();

    public ProxyChannel(Thread mainThread, Integer serverPort, Integer proxyPort) {
        super(mainThread, PROXY_NAME, serverPort, proxyPort);
        super.listenerLoaded = true;

        for (ChannelType<?> type : ChannelType.TYPES) {
            this.channelListenerByIdentifierByChannelType.put(type, new ConcurrentHashMap<>());
            this.channelListenerByMessageTypeByChannelType.put(type, new ConcurrentHashMap<>());
        }
    }

    public void addTimeOutListener(ChannelTimeOutListener listener) {
        this.timeOutListeners.add(listener);
    }

    @Override
    public void sendMessageToProxy(ChannelMessage<?, ?> message) {
        this.handleMessage(message.toStream().split(ChannelMessage.DIVIDER));
    }

    private boolean isHostReceivable(Host host) {
        return host != null && (this.registeredHosts.contains(host) || host.equals(this.proxy));
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
        this.userServers.put(uuid, this.serverHostByName.get(serverName));
    }

    @Override
    public void handleListenerMessage(ChannelListenerMessage<?> msg) {
        Host senderHost = msg.getSenderHost();

        this.handleMessage(msg);

        if (msg.getMessageType().equals(MessageType.Listener.IDENTIFIER_LISTENER)) {
            ChannelType<?> channelType = ((MessageType.MessageIdentifierListener<?>) msg.getValue()).getChannelType();
            Object identifier = ((MessageType.MessageIdentifierListener<?>) msg.getValue()).getIdentifier();

            this.addListener(msg);

            // if register only for proxy then done
            if (channelType.equals(ChannelType.SERVER) && ((String) identifier).equalsIgnoreCase(this.getProxyName())) {
                return;
            }

            Set<ChannelListenerMessage<?>> messageList = this.channelListenerByIdentifierByChannelType
                    .get(channelType).computeIfAbsent(identifier, k -> ConcurrentHashMap.newKeySet());

            if (messageList.stream().noneMatch(m -> m.getSenderHost().equals(senderHost))) {
                messageList.add(msg);
            }

            this.broadcastListenerMessage(msg);
        } else if (msg.getMessageType().equals(MessageType.Listener.MESSAGE_TYPE_LISTENER)) {
            ChannelType<?> channelType = ((MessageType.MessageTypeListener) msg.getValue()).getChannelType();
            MessageType<?> messageType = ((MessageType.MessageTypeListener) msg.getValue()).getMessageType();

            this.addListener(msg);

            Set<ChannelListenerMessage<?>> messageList = this.channelListenerByMessageTypeByChannelType
                    .get(channelType).computeIfAbsent(messageType, k -> ConcurrentHashMap.newKeySet());

            if (messageList.stream().noneMatch(m -> m.getSenderHost().equals(senderHost))) {
                messageList.add(msg);
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

    public void handleServerRegister(String serverName, Host host) {

        this.serverHostByName.put(serverName, host);

        Set<ChannelListenerMessage<?>> listenerMessages = this.channelListenerByIdentifierByChannelType.values().stream()
                .map(Map::values).flatMap(Collection::stream).flatMap(Collection::stream)
                .filter(msg -> Channel.isInterestingForServer(host, serverName, msg)).collect(Collectors.toSet());

        listenerMessages.addAll(this.channelListenerByMessageTypeByChannelType.values().stream().map(Map::values)
                .flatMap(Collection::stream).flatMap(Collection::stream)
                .filter(msg -> Channel.isInterestingForServer(host, serverName, msg)).collect(Collectors.toSet()));

        for (ChannelListenerMessage<?> listenerMessage : listenerMessages) {
            this.sendMessageSynchronized(host, listenerMessage);
        }

        this.sendMessageSynchronized(host, new ChannelListenerMessage<>(proxy, MessageType.Listener.REGISTER_SERVER,
                serverName));
        Channel.LOGGER.info("Send listener to " + host + " finished");
        this.registeredHosts.add(host);
    }

    public void handleServerUnregister(String serverName, Host host) {

        if (host.equals(this.proxy)) {
            ChannelListenerMessage<?> listenerMessage = new ChannelListenerMessage<>(host,
                    MessageType.Listener.UNREGISTER_SERVER, serverName);
            for (Host registeredHosts : this.registeredHosts) {
                this.sendMessage(registeredHosts, listenerMessage);
            }
            // nothing to clean up, because proxy is shutting down
            return;
        }

        // unregister from list
        this.registeredHosts.remove(host);
        this.serverHostByName.remove(serverName);

        // removed saved listener messages
        this.channelListenerByIdentifierByChannelType.values().forEach(map -> map.values()
                .forEach(set -> set.removeIf(msg -> msg.getSenderHost().equals(host)))
        );

        this.channelListenerByMessageTypeByChannelType.values().forEach(map -> map.values()
                .forEach(set -> set.removeIf(msg -> msg.getSenderHost().equals(host)))
        );

        // broadcast unregister to other servers
        ChannelListenerMessage<?> listenerMessage = new ChannelListenerMessage<>(host,
                MessageType.Listener.UNREGISTER_SERVER, serverName);
        for (Host registeredHosts : this.registeredHosts) {
            this.sendMessage(registeredHosts, listenerMessage);
        }

        Channel.LOGGER.info("Send unregister for " + host);

        this.disconnectHost(host);
    }


    public void handleHostRegister(Host host) {
        this.sendMessage(host, new ChannelListenerMessage<>(proxy, MessageType.Listener.REGISTER_SERVER, this.getProxyName()));
        Channel.LOGGER.info("Added host " + host);
        this.registeredHosts.add(host);
    }

    public void handleHostUnregister(Host host) {

        if (host.equals(this.proxy)) {
            return;
        }

        // unregister from list
        this.registeredHosts.remove(host);

        // removed saved listener messages
        this.channelListenerByIdentifierByChannelType.values().forEach(map -> map.values()
                .forEach(set -> set.removeIf(msg -> msg.getSenderHost().equals(host)))
        );

        this.channelListenerByMessageTypeByChannelType.values().forEach(map -> map.values()
                .forEach(set -> set.removeIf(msg -> msg.getSenderHost().equals(host)))
        );

        // broadcast unregister to other servers
        ChannelListenerMessage<?> listenerMessage = new ChannelListenerMessage<>(host,
                MessageType.Listener.UNREGISTER_SERVER, this.getProxyName());
        for (Host registeredHosts : this.registeredHosts) {
            this.sendMessage(registeredHosts, listenerMessage);
        }

        Channel.LOGGER.info("Removed host " + host);

        this.disconnectHost(host);
    }

    @Override
    protected void handlePingMessage(ChannelPingMessage message) {
        this.ping.pingedHosts.remove(new Tuple<>(message.getSenderName(),
                this.serverHostByName.get(message.getSenderName())));
    }

    public PingPong getPingPong() {
        return ping;
    }

    public Host getHost(String serverName) {
        return this.serverHostByName.get(serverName);
    }
}
