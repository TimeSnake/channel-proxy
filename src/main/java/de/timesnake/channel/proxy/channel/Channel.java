package de.timesnake.channel.proxy.channel;

import de.timesnake.channel.core.ChannelInfo;
import de.timesnake.channel.proxy.listener.ChannelTimeOutListener;
import de.timesnake.channel.util.message.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Channel extends de.timesnake.channel.core.Channel {

    private final PingPong ping = new PingPong();

    //saves the server, there the user is
    protected final ConcurrentHashMap<UUID, Integer> userServers = new ConcurrentHashMap<>();

    //list of servers, that are listening to a specific server-port (key)
    protected final ConcurrentHashMap<Integer, Set<ChannelListenerMessage<?>>> serverPortListenerMessagesByPort = new ConcurrentHashMap<>();

    //list of servers, that are listening to a specific server message type (key)
    protected final Collection<ChannelListenerMessage<?>> serverMessageTypeListenerMessages = new HashSet<>();

    //online servers, with an active channel
    protected final Collection<Integer> registeredServers = new HashSet<>();

    protected final Collection<ChannelTimeOutListener> timeOutListeners = new HashSet<>();


    public Channel(Thread mainThread, Integer serverPort, Integer proxyPort) {
        super(mainThread, serverPort, proxyPort);
        super.serverMessageServersRegistered = true;
    }

    public void addTimeOutListener(ChannelTimeOutListener listener) {
        this.timeOutListeners.add(listener);
    }

    @Override
    public void sendMessage(ChannelMessage<?, ?> message) {
        if (message instanceof ChannelGroupMessage) {
            for (Integer port : this.registeredServers) {
                if (this.isServerReceivable(port)) {
                    super.sendMessage(port, message);
                }
            }
        } else if (message instanceof ChannelUserMessage) {
            UUID uuid = ((ChannelUserMessage<?>) message).getUniqueId();
            if (this.userServers.containsKey(uuid) && this.userServers.get(uuid) != null) {
                Integer port = this.userServers.get(uuid);
                if (this.isServerReceivable(port)) {
                    super.sendMessage(port, message);
                }
            }
        } else if (message instanceof ChannelServerMessage) {
            Integer port = ((ChannelServerMessage<?>) message).getPort();
            // send msg to self
            if (this.isServerReceivable(port)) {
                super.sendMessage(port, message);
            }

            // prevent duplicate message send
            Set<Integer> receiverPorts = new HashSet<>();

            Set<ChannelListenerMessage<?>> listenerMsgs = this.serverPortListenerMessagesByPort.get(port);
            if (listenerMsgs != null) {
                for (ChannelListenerMessage<?> listenerMsg : listenerMsgs) {
                    if (!receiverPorts.contains(listenerMsg.getSenderPort()) && this.isServerReceivable(listenerMsg.getSenderPort())) {
                        super.sendMessage(listenerMsg.getSenderPort(), message);
                        receiverPorts.add(listenerMsg.getSenderPort());
                    }
                }
            }

            sendProxyServerMessage(message);

            for (ChannelListenerMessage<?> listenerMsg : this.serverMessageTypeListenerMessages) {
                if (!receiverPorts.contains(listenerMsg.getSenderPort()) && this.isServerReceivable(listenerMsg.getSenderPort())) {
                    super.sendMessage(listenerMsg.getSenderPort(), message);
                    receiverPorts.add(listenerMsg.getSenderPort());
                }
            }
        } else if (message instanceof ChannelSupportMessage) {
            sendProxyServerMessage(message);
        }
    }

    private boolean isServerReceivable(Integer port) {
        return port != null && (this.registeredServers.contains(port) || port.equals(this.proxyPort));
    }

    @Override
    public void sendMessageSynchronized(ChannelMessage<?, ?> message) {
        if (message instanceof ChannelGroupMessage) {
            for (Integer port : registeredServers) {
                if (this.isServerReceivable(port)) {
                    super.sendMessageSynchronized(port, message);
                }
            }
        } else if (message instanceof ChannelUserMessage) {
            UUID uuid = ((ChannelUserMessage<?>) message).getUniqueId();
            if (this.userServers.containsKey(uuid)) {
                Integer port = this.userServers.get(uuid);
                if (this.isServerReceivable(port)) {
                    super.sendMessageSynchronized(port, message);
                }
            }
        } else if (message instanceof ChannelServerMessage) {
            Integer port = ((ChannelServerMessage<?>) message).getPort();
            // send msg to self
            if (this.isServerReceivable(port)) {
                super.sendMessageSynchronized(port, message);
            }

            Set<ChannelListenerMessage<?>> listenerMsgs = this.serverPortListenerMessagesByPort.get(port);
            if (listenerMsgs != null) {
                for (ChannelListenerMessage<?> listenerMsg : listenerMsgs) {
                    if (this.isServerReceivable(listenerMsg.getSenderPort())) {
                        super.sendMessageSynchronized(listenerMsg.getSenderPort(), message);
                    }
                }
            }

            sendProxyServerMessage(message);

            for (ChannelListenerMessage<?> listenerMsg : this.serverMessageTypeListenerMessages) {
                if (this.isServerReceivable(listenerMsg.getSenderPort())) {
                    super.sendMessageSynchronized(listenerMsg.getSenderPort(), message);
                }
            }
        } else if (message instanceof ChannelSupportMessage) {
            Integer port = ((ChannelSupportMessage<?>) message).getPort();

            // send to server with given port
            if (this.isServerReceivable(port)) {
                super.sendMessageSynchronized(port, message);
            }
        }
    }

    private void sendProxyServerMessage(ChannelMessage<?, ?> message) {
        Integer port;
        for (Map.Entry<Integer, Set<MessageType<?>>> entry : receiverServerListeners.entrySet()) {

            port = entry.getKey();
            if (this.isServerReceivable(port)) {
                Set<MessageType<?>> typeSet = entry.getValue();
                if (typeSet == null || typeSet.isEmpty()) {
                    this.sendMessageSynchronized(port, message);
                } else if (typeSet.contains(((ChannelServerMessage<?>) message).getMessageType())) {
                    this.sendMessageSynchronized(port, message);
                }
            }
        }
    }

    public void setUserServer(UUID uuid, Integer port) {
        this.userServers.put(uuid, port);
    }

    @Override
    public void handleListenerMessage(ChannelListenerMessage<?> msg) {
        Integer senderPort = msg.getSenderPort();

        this.handleMessage(msg);

        if (msg.getMessageType().equals(MessageType.Listener.SERVER_PORT)) {
            Integer receiverPort = ((Integer) msg.getValue());
            //port equals sender port
            if (receiverPort.equals(senderPort)) {
                return;
            }

            //port equals proxy port
            if (receiverPort.equals(this.proxyPort)) {
                this.addServerListener(msg);
                return;
            }

            Set<ChannelListenerMessage<?>> messageList = this.serverPortListenerMessagesByPort.computeIfAbsent(receiverPort, k -> new HashSet<>());

            // check if message with sender port already exists
            if (messageList.stream().noneMatch(m -> m.getSenderPort().equals(senderPort))) {
                messageList.add(msg);
            }

            //if receiver server online, send message
            if (this.isServerReceivable(receiverPort)) {
                this.sendMessage(receiverPort, msg);
            }

        } else if (msg.getMessageType().equals(MessageType.Listener.SERVER_MESSAGE_TYPE)) {
            //listener/<sender>/server/<type>
            //check message type
            MessageType<?> valueMessageType = ((MessageType<?>) msg.getValue());

            if (valueMessageType != null) {

                // check if list contains port
                boolean contains = false;
                for (ChannelListenerMessage<?> listener : this.serverMessageTypeListenerMessages) {
                    if (listener.getSenderPort().equals(senderPort)) {
                        contains = true;
                        break;
                    }
                }

                // add and broadcast if not already in list
                if (!contains) {
                    this.serverMessageTypeListenerMessages.add(msg);

                    //send listener to all servers
                    for (Integer registeredServer : this.registeredServers) {
                        if (!registeredServer.equals(msg.getSenderPort())) {
                            this.sendMessage(registeredServer, msg);
                        }
                    }
                }

            }
        } else if (msg.getMessageType().equals(MessageType.Listener.REGISTER)) {
            this.handleServerRegister(senderPort);
        } else if (msg.getMessageType().equals(MessageType.Listener.UNREGISTER)) {
            this.handleServerUnregister(senderPort);
        }
    }

    public void handleServerRegister(Integer senderPort) {

        //get listener with new registered server port
        Set<ChannelListenerMessage<?>> listenerMessages = serverPortListenerMessagesByPort.get(senderPort);
        if (listenerMessages != null) {
            //send listener messages to new registered server
            for (ChannelListenerMessage<?> listenerMessage : listenerMessages) {
                if (!senderPort.equals(listenerMessage.getSenderPort())) {
                    this.sendMessage(senderPort, listenerMessage);
                }
            }
        }

        for (ChannelListenerMessage<?> listenerMessage : this.serverMessageTypeListenerMessages) {
            if (!senderPort.equals(listenerMessage.getSenderPort())) {
                this.sendMessage(senderPort, listenerMessage);
            }
        }

        new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.sendMessage(senderPort, new ChannelListenerMessage<>(proxyPort, MessageType.Listener.REGISTER));
            ChannelInfo.broadcastMessage("Send listener to " + senderPort + " finished");
            this.registeredServers.add(senderPort);
        }).start();
    }

    public void handleServerUnregister(Integer senderPort) {

        if (senderPort.equals(this.proxyPort)) {
            return;
        }

        // unregister from list
        this.registeredServers.remove(senderPort);

        // remove port listeners by the server
        for (Set<ChannelListenerMessage<?>> messages : this.serverPortListenerMessagesByPort.values()) {
            messages.removeIf(listenerMessage -> listenerMessage.getSenderPort().equals(senderPort));
        }

        // remove type listeners by the server
        this.serverMessageTypeListenerMessages.removeIf(listener -> senderPort.equals(listener.getSenderPort()));

        // broadcast unregister to other servers
        ChannelListenerMessage<?> listenerMessage = new ChannelListenerMessage<>(senderPort, MessageType.Listener.UNREGISTER);
        for (Integer port : this.registeredServers) {
            System.out.println(port);
            this.sendMessage(port, listenerMessage);
        }

        ChannelInfo.broadcastMessage("Send unregister for " + senderPort);
    }

    @Override
    protected void handlePingMessage(ChannelPingMessage message) {
        this.ping.pingedPorts.remove(message.getSenderPort());
    }

    public PingPong getPingPong() {
        return ping;
    }
}
