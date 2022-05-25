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
    protected final ConcurrentHashMap<Integer, Set<ChannelListenerMessage<?>>> serverPortListenerMessagesByServerPort = new ConcurrentHashMap<>();
    //list of servers, that are listening to a specific server message type (key)
    protected final Collection<ChannelListenerMessage<?>> serverMessageTypeListenerMessages =
            ConcurrentHashMap.newKeySet();
    //online servers, with an active channel
    protected final Collection<Host> registeredServers = ConcurrentHashMap.newKeySet();
    protected final Collection<ChannelTimeOutListener> timeOutListeners = ConcurrentHashMap.newKeySet();
    private final PingPong ping = new PingPong();
    protected ConcurrentHashMap<Integer, Host> hostByServerPort = new ConcurrentHashMap<>();

    public Channel(Thread mainThread, Integer serverPort, Integer proxyPort, ChannelLogger logger) {
        super(mainThread, serverPort, proxyPort, logger);
        super.serverMessageServersRegistered = true;
    }

    public void addTimeOutListener(ChannelTimeOutListener listener) {
        this.timeOutListeners.add(listener);
    }

    public void sendMessageToServer(int port, ChannelMessage<?, ?> message) {
        Host host = this.hostByServerPort.get(port);

        if (host == null) {
            return;
        }

        this.sendMessage(host, message);
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
            Integer port = ((ChannelServerMessage<?>) message).getPort();
            Host host = this.hostByServerPort.get(port);

            // send msg to self
            if (this.isServerReceivable(host)) {
                super.sendMessage(host, message);
            }

            // prevent duplicate message send
            Set<Host> receiverHosts = ConcurrentHashMap.newKeySet();

            Set<ChannelListenerMessage<?>> listenerMsgs = this.serverPortListenerMessagesByServerPort.get(port);
            if (listenerMsgs != null) {
                for (ChannelListenerMessage<?> listenerMsg : listenerMsgs) {
                    if (!receiverHosts.contains(listenerMsg.getSenderHost()) && this.isServerReceivable(listenerMsg.getSenderHost())) {
                        super.sendMessage(listenerMsg.getSenderHost(), message);
                        receiverHosts.add(listenerMsg.getSenderHost());
                    }
                }
            }

            sendProxyServerMessage(message);

            for (ChannelListenerMessage<?> listenerMsg : this.serverMessageTypeListenerMessages) {
                if (!receiverHosts.contains(listenerMsg.getSenderHost()) && this.isServerReceivable(listenerMsg.getSenderHost())) {
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
            Integer port = ((ChannelServerMessage<?>) message).getPort();
            Host host = this.hostByServerPort.get(port);

            // send msg to self
            if (this.isServerReceivable(host)) {
                super.sendMessageSynchronized(host, message);
            }

            Set<ChannelListenerMessage<?>> listenerMsgs = this.serverPortListenerMessagesByServerPort.get(port);
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
            Integer port = ((ChannelSupportMessage<?>) message).getPort();
            Host host = this.hostByServerPort.get(port);

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

    public void setUserServer(UUID uuid, int serverPort) {
        this.userServers.put(uuid, this.hostByServerPort.get(serverPort));
    }

    @Override
    public void handleListenerMessage(ChannelListenerMessage<?> msg) {
        Host senderHost = msg.getSenderHost();

        this.handleMessage(msg);

        if (msg.getMessageType().equals(MessageType.Listener.SERVER_PORT)) {
            Integer receiverPort = ((Integer) msg.getValue());
            //port equals sender port
            if (receiverPort.equals(senderHost.getPort())) {
                return;
            }

            //port equals proxy port
            if (receiverPort.equals(this.proxyPort)) {
                this.addServerListener(msg);
                return;
            }

            Set<ChannelListenerMessage<?>> messageList =
                    this.serverPortListenerMessagesByServerPort.computeIfAbsent(receiverPort,
                            k -> ConcurrentHashMap.newKeySet());

            // check if message with sender port already exists
            if (messageList.stream().noneMatch(m -> m.getSenderHost().equals(senderHost))) {
                messageList.add(msg);
            }

            Host receiverHost = this.hostByServerPort.get(receiverPort);

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
            this.handleServerRegister(((Integer) msg.getValue()), senderHost);
        } else if (msg.getMessageType().equals(MessageType.Listener.UNREGISTER_SERVER)) {
            this.handleServerUnregister(((Integer) msg.getValue()), senderHost);
        } else if (msg.getMessageType().equals(MessageType.Listener.REGISTER_HOST)) {
            this.handleHostRegister(senderHost);
        } else if (msg.getMessageType().equals(MessageType.Listener.UNREGISTER_HOST)) {
            this.handleHostUnregister(senderHost);
        }
    }

    public void handleServerRegister(Integer serverPort, Host host) {

        this.hostByServerPort.put(serverPort, host);

        //get listener with new registered server port
        Set<ChannelListenerMessage<?>> listenerMessages = serverPortListenerMessagesByServerPort.get(serverPort);
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
                serverPort));
        this.logger.logInfo("Send listener to " + host + " finished");
        this.registeredServers.add(host);
    }

    public void handleServerUnregister(Integer serverPort, Host host) {

        if (host.equals(this.proxy)) {
            return;
        }

        // unregister from list
        this.registeredServers.remove(host);
        this.hostByServerPort.remove(serverPort);

        // remove port listeners by the server
        for (Set<ChannelListenerMessage<?>> messages : this.serverPortListenerMessagesByServerPort.values()) {
            messages.removeIf(listenerMessage -> listenerMessage.getSenderHost().equals(host));
        }

        // remove type listeners by the server
        this.serverMessageTypeListenerMessages.removeIf(listener -> host.equals(listener.getSenderHost()));

        // broadcast unregister to other servers
        ChannelListenerMessage<?> listenerMessage = new ChannelListenerMessage<>(host,
                MessageType.Listener.UNREGISTER_SERVER, serverPort);
        for (Host registeredHosts : this.registeredServers) {
            this.sendMessage(registeredHosts, listenerMessage);
        }

        this.logger.logInfo("Send unregister for " + host);

        this.disconnectHost(host);
    }


    public void handleHostRegister(Host host) {
        this.sendMessage(host, new ChannelListenerMessage<>(proxy, MessageType.Listener.REGISTER_SERVER, serverPort));
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
        for (Set<ChannelListenerMessage<?>> messages : this.serverPortListenerMessagesByServerPort.values()) {
            messages.removeIf(listenerMessage -> listenerMessage.getSenderHost().equals(host));
        }

        // remove type listeners by the server
        this.serverMessageTypeListenerMessages.removeIf(listener -> host.equals(listener.getSenderHost()));

        // broadcast unregister to other servers
        ChannelListenerMessage<?> listenerMessage = new ChannelListenerMessage<>(host,
                MessageType.Listener.UNREGISTER_SERVER, serverPort);
        for (Host registeredHosts : this.registeredServers) {
            this.sendMessage(registeredHosts, listenerMessage);
        }

        this.logger.logInfo("Removed host " + host);

        this.disconnectHost(host);
    }

    @Override
    protected void handlePingMessage(ChannelPingMessage message) {
        this.ping.pingedHosts.remove(new Tuple<>(message.getSenderPort(),
                this.hostByServerPort.get(message.getSenderPort())));
    }

    public PingPong getPingPong() {
        return ping;
    }

    public Host getHostByServerPort(int serverPort) {
        return this.hostByServerPort.get(serverPort);
    }
}
