package de.timesnake.channel.proxy.channel;

import de.timesnake.channel.api.message.*;
import de.timesnake.channel.channel.ChannelInfo;
import de.timesnake.channel.channel.ChannelMap;
import de.timesnake.channel.proxy.listener.ChannelRegisterListener;
import de.timesnake.channel.proxy.listener.ChannelTimeOutListener;

import java.util.*;

public abstract class Channel extends de.timesnake.channel.channel.Channel {

    private final PingPong ping = new PingPong();

    //saves the server, there the user is
    protected final ChannelMap<UUID, Integer> userServers = new ChannelMap<>();

    //list of servers, that are listening to a specific server-port (key)
    protected final ChannelMap<Integer, Set<ChannelListenerMessage>> serverPortListenerMessagesByPort = new ChannelMap<>();

    //list of servers, that are listening to a specific server message type (key)
    protected final Collection<ChannelListenerMessage> serverMessageTypeListenerMessages = new HashSet<>();

    //online servers, with an active channel
    protected final Collection<Integer> registeredServers = new HashSet<>();


    // channel-register-listener (proxy only)
    protected final Collection<ChannelRegisterListener> registerListeners = new HashSet<>();

    protected final Collection<ChannelTimeOutListener> timeOutListeners = new HashSet<>();


    public Channel(Thread mainThread, Integer serverPort, Integer proxyPort) {
        super(mainThread, serverPort, proxyPort);
        super.serverMessageServersRegistered = true;
    }

    /**
     * Adds the register-channel-listener to the channel
     *
     * @param listener The {@link ChannelRegisterListener} to add
     */
    public void addRegisterListener(ChannelRegisterListener listener) {
        this.registerListeners.add(listener);
    }

    public void addTimeOutListener(ChannelTimeOutListener listener) {
        this.timeOutListeners.add(listener);
    }

    @Override
    public void sendMessage(ChannelMessage message) {
        if (message instanceof ChannelGroupMessage) {
            for (Integer port : this.registeredServers) {
                if (this.isServerReceivable(port)) {
                    super.sendMessage(port, message);
                }
            }
        } else if (message instanceof ChannelUserMessage) {
            UUID uuid = ((ChannelUserMessage) message).getUniqueId();
            if (this.userServers.containsKey(uuid) && this.userServers.get(uuid) != null) {
                Integer port = this.userServers.get(uuid);
                if (this.isServerReceivable(port)) {
                    super.sendMessage(port, message);
                }
            }
        } else if (message instanceof ChannelServerMessage) {
            Integer port = ((ChannelServerMessage) message).getPort();
            // send msg to self
            if (this.isServerReceivable(port)) {
                super.sendMessage(port, message);
            }

            // prevent duplicate message send
            Set<Integer> receiverPorts = new HashSet<>();

            Set<ChannelListenerMessage> listenerMsgs = this.serverPortListenerMessagesByPort.get(port);
            if (listenerMsgs != null) {
                for (ChannelListenerMessage listenerMsg : listenerMsgs) {
                    if (!receiverPorts.contains(listenerMsg.getPort()) && this.isServerReceivable(listenerMsg.getPort())) {
                        super.sendMessage(listenerMsg.getPort(), message);
                        receiverPorts.add(listenerMsg.getPort());
                    }
                }
            }

            sendProxyServerMessage(message);

            for (ChannelListenerMessage listenerMsg : this.serverMessageTypeListenerMessages) {
                if (!receiverPorts.contains(listenerMsg.getPort()) && this.isServerReceivable(listenerMsg.getPort())) {
                    super.sendMessage(listenerMsg.getPort(), message);
                    receiverPorts.add(listenerMsg.getPort());
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
    public void sendMessageSynchronized(ChannelMessage message) {
        if (message instanceof ChannelGroupMessage) {
            for (Integer port : registeredServers) {
                if (this.isServerReceivable(port)) {
                    super.sendMessageSynchronized(port, message);
                }
            }
        } else if (message instanceof ChannelUserMessage) {
            UUID uuid = ((ChannelUserMessage) message).getUniqueId();
            if (this.userServers.containsKey(uuid)) {
                Integer port = this.userServers.get(uuid);
                if (this.isServerReceivable(port)) {
                    super.sendMessageSynchronized(port, message);
                }
            }
        } else if (message instanceof ChannelServerMessage) {
            Integer port = ((ChannelServerMessage) message).getPort();
            // send msg to self
            if (this.isServerReceivable(port)) {
                super.sendMessageSynchronized(port, message);
            }

            Set<ChannelListenerMessage> listenerMsgs = this.serverPortListenerMessagesByPort.get(port);
            if (listenerMsgs != null) {
                for (ChannelListenerMessage listenerMsg : listenerMsgs) {
                    if (this.isServerReceivable(listenerMsg.getPort())) {
                        super.sendMessageSynchronized(listenerMsg.getPort(), message);
                    }
                }
            }

            sendProxyServerMessage(message);

            for (ChannelListenerMessage listenerMsg : this.serverMessageTypeListenerMessages) {
                if (this.isServerReceivable(listenerMsg.getPort())) {
                    super.sendMessageSynchronized(listenerMsg.getPort(), message);
                }
            }
        } else if (message instanceof ChannelSupportMessage) {
            Integer port = ((ChannelSupportMessage) message).getPort();

            // send to server with given port
            if (this.isServerReceivable(port)) {
                super.sendMessageSynchronized(port, message);
            }
        }
    }

    private void sendProxyServerMessage(ChannelMessage message) {
        Integer port;
        for (Map.Entry<Integer, Set<ChannelServerMessage.MessageType>> entry : receiverServerListeners.entrySet()) {

            port = entry.getKey();
            if (this.isServerReceivable(port)) {
                Set<ChannelServerMessage.MessageType> typeSet = entry.getValue();
                if (typeSet == null || typeSet.isEmpty()) {
                    this.sendMessageSynchronized(port, message);
                } else if (typeSet.contains(((ChannelServerMessage) message).getType())) {
                    this.sendMessageSynchronized(port, message);
                }
            }
        }
    }

    public void setUserServer(UUID uuid, Integer port) {
        this.userServers.put(uuid, port);
    }

    @Override
    public void handleListenerMessage(ChannelListenerMessage msg) {
        Integer senderPort = msg.getPort();

        if (msg.getType().equals(ChannelListenerMessage.MessageType.SERVER)) {
            Integer receiverPort = null;
            try {
                receiverPort = Integer.valueOf(msg.getValue());
            } catch (NumberFormatException ignored) {
            }


            // listener/<sender>/server/<receiver>
            if (receiverPort != null) {

                //port equals sender port
                if (receiverPort.equals(senderPort)) {
                    return;
                }

                //port equals proxy port
                if (receiverPort.equals(this.proxyPort)) {
                    this.addServerListener(msg);
                    return;
                }

                //create new set, if not exists
                Set<ChannelListenerMessage> messageList = this.serverPortListenerMessagesByPort.get(receiverPort);

                if (messageList == null) {
                    messageList = new HashSet<>();
                    this.serverPortListenerMessagesByPort.put(receiverPort, messageList);
                }


                // check if message with sender port already exists
                boolean contains = false;
                for (ChannelListenerMessage listener : messageList) {
                    if (listener.getPort().equals(senderPort)) {
                        contains = true;
                        break;
                    }
                }

                // add message if sender port not contained
                if (!contains) {
                    messageList.add(msg);
                }

                //if receiver server online, send message
                if (this.isServerReceivable(receiverPort)) {
                    this.sendMessage(receiverPort, msg);
                }

            } else {
                //listener/<sender>/server/<type>
                //check message type
                ChannelServerMessage.MessageType valueMessageType;
                try {
                    valueMessageType = ChannelServerMessage.MessageType.valueOf(msg.getValue());
                } catch (IllegalArgumentException ignored) {
                    return;
                }

                if (valueMessageType != null) {

                    // chek if list contains port
                    boolean contains = false;
                    for (ChannelListenerMessage listener : this.serverMessageTypeListenerMessages) {
                        if (listener.getPort().equals(senderPort)) {
                            contains = true;
                            break;
                        }
                    }

                    // add and broadcast if not already in list
                    if (!contains) {
                        this.serverMessageTypeListenerMessages.add(msg);

                        //send listener to all servers
                        for (Integer registeredServer : this.registeredServers) {
                            if (!registeredServer.equals(msg.getPort())) {
                                this.sendMessage(registeredServer, msg);
                            }
                        }
                    }

                }
            }
        }
        // bukkit server register receiver
        else if (msg.getType().equals(ChannelListenerMessage.MessageType.CHANNEL)) {

            // send/remove listeners
            if (!this.registeredServers.contains(serverPort)) {
                this.handleServerRegister(senderPort);
            } else {
                this.handleServerUnregister(serverPort);
            }

        }
    }

    public void handleServerRegister(Integer port) {

        //get listener with new registered server port
        Set<ChannelListenerMessage> listenerMessages = serverPortListenerMessagesByPort.get(port);
        if (listenerMessages != null) {
            //send listener messages to new registered server
            for (ChannelListenerMessage listenerMessage : listenerMessages) {
                if (!port.equals(listenerMessage.getPort())) {
                    this.sendMessage(port, listenerMessage);
                }
            }
        }

        for (ChannelListenerMessage listenerMessage : this.serverMessageTypeListenerMessages) {
            if (!port.equals(listenerMessage.getPort())) {
                this.sendMessage(port, listenerMessage);
            }
        }

        new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.sendMessage(port, ChannelListenerMessage.getChannelMessage(proxyPort));
            ChannelInfo.broadcastMessage("Send listener to " + port + " finished");
            this.registeredServers.add(port);
        }).start();

        for (ChannelRegisterListener listener : this.registerListeners) {
            listener.onChannelRegisterMessage(port, true);
        }
    }

    public void handleServerUnregister(Integer serverPort) {
        // unregister from list
        this.registeredServers.remove(serverPort);

        // remove port listeners by the server
        this.serverPortListenerMessagesByPort.iterateValues((set -> set.removeIf(listenerMessage -> listenerMessage.getPort().equals(serverPort))));

        // remove type listeners by the server
        this.serverMessageTypeListenerMessages.removeIf(listener -> serverPort.equals(listener.getPort()));

        // broadcast unregister to other servers
        ChannelListenerMessage listenerMessage = ChannelListenerMessage.getChannelMessage(proxyPort, serverPort);
        for (Integer port : this.registeredServers) {
            if (!port.equals(serverPort)) {
                this.sendMessage(port, listenerMessage);
            }
        }

        for (ChannelRegisterListener listener : this.registerListeners) {
            listener.onChannelRegisterMessage(serverPort, false);
        }

        ChannelInfo.broadcastMessage("Send unregister for " + serverPort);
    }

    @Override
    protected void handlePingMessage(ChannelPingMessage message) {
        this.ping.pingedPorts.remove(message.getSenderPort());
    }

    public PingPong getPingPong() {
        return ping;
    }
}
