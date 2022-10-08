/*
 * channel-proxy.main
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

import de.timesnake.channel.core.ChannelLogger;
import de.timesnake.channel.core.Host;
import de.timesnake.channel.proxy.listener.ChannelTimeOutListener;
import de.timesnake.channel.util.message.*;
import de.timesnake.library.basic.util.Tuple;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Channel extends de.timesnake.channel.core.Channel {

    //saves the server, there the user is
    protected final ConcurrentHashMap<UUID, Host> userServers = new ConcurrentHashMap<>();
    //list of servers, that are listening to a specific server-port (key)
    protected final ConcurrentHashMap<String, Set<ChannelListenerMessage<?>>> serverPortListenerMessagesByName = new ConcurrentHashMap<>();
    //list of servers, that are listening to a specific server message type (key)
    protected final Collection<ChannelListenerMessage<?>> serverMessageTypeListenerMessages =
            ConcurrentHashMap.newKeySet();
    //online servers, with an active channel
    protected final Collection<Host> registeredServers = ConcurrentHashMap.newKeySet();
    protected final Collection<ChannelTimeOutListener> timeOutListeners = ConcurrentHashMap.newKeySet();
    private final PingPong ping = new PingPong();
    protected ConcurrentHashMap<String, Host> hostByName = new ConcurrentHashMap<>();

    public Channel(Thread mainThread, Integer serverPort, Integer proxyPort, ChannelLogger logger) {
        super(mainThread, PROXY_NAME, serverPort, proxyPort, logger);
        super.serverMessageServersRegistered = true;
    }

    public void addTimeOutListener(ChannelTimeOutListener listener) {
        this.timeOutListeners.add(listener);
    }

    @Deprecated
    public void sendMessageToServer(int port, ChannelMessage<?, ?> message) {

    }

    @Override
    public void sendMessageToProxy(ChannelMessage<?, ?> message) {
        this.handleMessage(message.toStream().split(ChannelMessage.DIVIDER));
    }

    @Override
    public void sendMessage(ChannelMessage<?, ?> message) {
        if (message instanceof ChannelGroupMessage) {
            for (Host host : this.registeredServers) {
                if (this.isServerReceivable(host)) {
                    super.sendMessage(host, message);
                }
            }
        } else if (message instanceof ChannelUserMessage) {
            UUID uuid = ((ChannelUserMessage<?>) message).getUniqueId();
            if (this.userServers.containsKey(uuid) && this.userServers.get(uuid) != null) {
                Host host = this.userServers.get(uuid);
                if (this.isServerReceivable(host)) {
                    super.sendMessage(host, message);
                }
            }
        } else if (message instanceof ChannelServerMessage) {
            String name = ((ChannelServerMessage<?>) message).getName();
            Host host = this.hostByName.get(name);

            // send msg to self
            if (this.isServerReceivable(host)) {
                super.sendMessage(host, message);
            }

            // prevent duplicate message send
            Set<Host> receiverHosts = ConcurrentHashMap.newKeySet();

            Set<ChannelListenerMessage<?>> listenerMsgs = this.serverPortListenerMessagesByName.get(name);
            if (listenerMsgs != null) {
                for (ChannelListenerMessage<?> listenerMsg : listenerMsgs) {
                    if (!receiverHosts.contains(listenerMsg.getSenderHost())
                            && this.isServerReceivable(listenerMsg.getSenderHost())) {
                        super.sendMessage(listenerMsg.getSenderHost(), message);
                        receiverHosts.add(listenerMsg.getSenderHost());
                    }
                }
            }

            sendProxyServerMessage(message);

            for (ChannelListenerMessage<?> listenerMsg : this.serverMessageTypeListenerMessages) {
                if (!receiverHosts.contains(listenerMsg.getSenderHost())
                        && this.isServerReceivable(listenerMsg.getSenderHost())) {
                    super.sendMessage(listenerMsg.getSenderHost(), message);
                    receiverHosts.add(listenerMsg.getSenderHost());
                }
            }
        } else if (message instanceof ChannelSupportMessage) {
            sendProxyServerMessage(message);
        } else if (message instanceof ChannelDiscordMessage) {
            this.handleMessage(message);
        }
    }

    private boolean isServerReceivable(Host host) {
        return host != null && (this.registeredServers.contains(host) || host.equals(this.proxy));
    }

    @Override
    public void sendMessageSynchronized(ChannelMessage<?, ?> message) {
        if (message instanceof ChannelGroupMessage) {
            for (Host host : registeredServers) {
                if (this.isServerReceivable(host)) {
                    super.sendMessageSynchronized(host, message);
                }
            }
        } else if (message instanceof ChannelUserMessage) {
            UUID uuid = ((ChannelUserMessage<?>) message).getUniqueId();
            if (this.userServers.containsKey(uuid)) {
                Host host = this.userServers.get(uuid);
                if (this.isServerReceivable(host)) {
                    super.sendMessageSynchronized(host, message);
                }
            }
        } else if (message instanceof ChannelServerMessage) {
            String name = ((ChannelServerMessage<?>) message).getName();
            Host host = this.hostByName.get(name);

            // send msg to self
            if (this.isServerReceivable(host)) {
                super.sendMessageSynchronized(host, message);
            }

            Set<ChannelListenerMessage<?>> listenerMsgs = this.serverPortListenerMessagesByName.get(name);
            if (listenerMsgs != null) {
                for (ChannelListenerMessage<?> listenerMsg : listenerMsgs) {
                    if (this.isServerReceivable(listenerMsg.getSenderHost())) {
                        super.sendMessageSynchronized(listenerMsg.getSenderHost(), message);
                    }
                }
            }

            sendProxyServerMessage(message);

            for (ChannelListenerMessage<?> listenerMsg : this.serverMessageTypeListenerMessages) {
                if (this.isServerReceivable(listenerMsg.getSenderHost())) {
                    super.sendMessageSynchronized(listenerMsg.getSenderHost(), message);
                }
            }
        } else if (message instanceof ChannelSupportMessage) {
            String name = ((ChannelSupportMessage<?>) message).getName();
            Host host = this.hostByName.get(name);

            // send to server with given port
            if (this.isServerReceivable(host)) {
                super.sendMessageSynchronized(host, message);
            }
        }
    }

    private void sendProxyServerMessage(ChannelMessage<?, ?> message) {
        Host host;
        for (Map.Entry<Host, Set<MessageType<?>>> entry : receiverServerListeners.entrySet()) {

            host = entry.getKey();
            if (this.isServerReceivable(host)) {
                Set<MessageType<?>> typeSet = entry.getValue();
                if (typeSet == null || typeSet.isEmpty()) {
                    this.sendMessageSynchronized(host, message);
                } else if (typeSet.contains(((ChannelServerMessage<?>) message).getMessageType())) {
                    this.sendMessageSynchronized(host, message);
                }
            }
        }
    }

    public void setUserServer(UUID uuid, String serverName) {
        this.userServers.put(uuid, this.hostByName.get(serverName));
    }

    @Override
    public void handleListenerMessage(ChannelListenerMessage<?> msg) {
        Host senderHost = msg.getSenderHost();

        this.handleMessage(msg);

        if (msg.getMessageType().equals(MessageType.Listener.SERVER_NAME)) {
            String receiverName = ((String) msg.getValue());

            //port equals proxy port
            if (receiverName.equals(this.getProxyName())) {
                this.addServerListener(msg);
                return;
            }

            Set<ChannelListenerMessage<?>> messageList =
                    this.serverPortListenerMessagesByName.computeIfAbsent(receiverName,
                            k -> ConcurrentHashMap.newKeySet());

            // check if message with sender port already exists
            if (messageList.stream().noneMatch(m -> m.getSenderHost().equals(senderHost))) {
                messageList.add(msg);
            }

            Host receiverHost = this.hostByName.get(receiverName);

            //if receiver server online, send message
            if (this.isServerReceivable(receiverHost)) {
                this.sendMessage(receiverHost, msg);
            }

        } else if (msg.getMessageType().equals(MessageType.Listener.SERVER_MESSAGE_TYPE)) {
            //listener/<sender>/server/<type>
            //check message type
            MessageType<?> valueMessageType = ((MessageType<?>) msg.getValue());

            if (valueMessageType != null) {

                // check if list contains port
                boolean contains = false;
                for (ChannelListenerMessage<?> listener : this.serverMessageTypeListenerMessages) {
                    if (listener.getSenderHost().equals(senderHost)) {
                        contains = true;
                        break;
                    }
                }

                // add and broadcast if not already in list
                if (!contains) {
                    this.serverMessageTypeListenerMessages.add(msg);

                    //send listener to all servers
                    for (Host registeredServer : this.registeredServers) {
                        if (!registeredServer.equals(msg.getSenderHost())) {
                            this.sendMessage(registeredServer, msg);
                        }
                    }
                }

            }
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

    public void handleServerRegister(String serverName, Host host) {

        this.hostByName.put(serverName, host);

        //get listener with new registered server port
        Set<ChannelListenerMessage<?>> listenerMessages = serverPortListenerMessagesByName.get(serverName);
        if (listenerMessages != null) {
            //send listener messages to new registered server
            for (ChannelListenerMessage<?> listenerMessage : listenerMessages) {
                if (!host.equals(listenerMessage.getSenderHost())) {
                    this.sendMessageSynchronized(host, listenerMessage);
                }
            }
        }

        for (ChannelListenerMessage<?> listenerMessage : this.serverMessageTypeListenerMessages) {
            if (!host.equals(listenerMessage.getSenderHost())) {
                this.sendMessageSynchronized(host, listenerMessage);
            }
        }

        this.sendMessageSynchronized(host, new ChannelListenerMessage<>(proxy, MessageType.Listener.REGISTER_SERVER,
                serverName));
        this.logger.logInfo("Send listener to " + host + " finished");
        this.registeredServers.add(host);
    }

    public void handleServerUnregister(String serverName, Host host) {

        if (host.equals(this.proxy)) {
            ChannelListenerMessage<?> listenerMessage = new ChannelListenerMessage<>(host,
                    MessageType.Listener.UNREGISTER_SERVER, serverName);
            for (Host registeredHosts : this.registeredServers) {
                this.sendMessage(registeredHosts, listenerMessage);
            }
            // nothing to clean up, because proxy is shutting down
            return;
        }

        // unregister from list
        this.registeredServers.remove(host);
        this.hostByName.remove(serverName);

        // remove port listeners by the server
        for (Set<ChannelListenerMessage<?>> messages : this.serverPortListenerMessagesByName.values()) {
            messages.removeIf(listenerMessage -> listenerMessage.getSenderHost().equals(host));
        }

        // remove type listeners by the server
        this.serverMessageTypeListenerMessages.removeIf(listener -> host.equals(listener.getSenderHost()));

        // broadcast unregister to other servers
        ChannelListenerMessage<?> listenerMessage = new ChannelListenerMessage<>(host,
                MessageType.Listener.UNREGISTER_SERVER, serverName);
        for (Host registeredHosts : this.registeredServers) {
            this.sendMessage(registeredHosts, listenerMessage);
        }

        this.logger.logInfo("Send unregister for " + host);

        this.disconnectHost(host);
    }


    public void handleHostRegister(Host host) {
        this.sendMessage(host, new ChannelListenerMessage<>(proxy, MessageType.Listener.REGISTER_SERVER, this.getProxyName()));
        this.logger.logInfo("Added host " + host);
        this.registeredServers.add(host);
    }

    public void handleHostUnregister(Host host) {

        if (host.equals(this.proxy)) {
            return;
        }

        // unregister from list
        this.registeredServers.remove(host);

        // remove port listeners by the server
        for (Set<ChannelListenerMessage<?>> messages : this.serverPortListenerMessagesByName.values()) {
            messages.removeIf(listenerMessage -> listenerMessage.getSenderHost().equals(host));
        }

        // remove type listeners by the server
        this.serverMessageTypeListenerMessages.removeIf(listener -> host.equals(listener.getSenderHost()));

        // broadcast unregister to other servers
        ChannelListenerMessage<?> listenerMessage = new ChannelListenerMessage<>(host,
                MessageType.Listener.UNREGISTER_SERVER, this.getProxyName());
        for (Host registeredHosts : this.registeredServers) {
            this.sendMessage(registeredHosts, listenerMessage);
        }

        this.logger.logInfo("Removed host " + host);

        this.disconnectHost(host);
    }

    @Override
    protected void handlePingMessage(ChannelPingMessage message) {
        this.ping.pingedHosts.remove(new Tuple<>(message.getSenderName(),
                this.hostByName.get(message.getSenderName())));
    }

    public PingPong getPingPong() {
        return ping;
    }

    public Host getHost(String serverName) {
        return this.hostByName.get(serverName);
    }
}
