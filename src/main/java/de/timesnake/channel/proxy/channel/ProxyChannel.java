/*
 * Copyright (C) 2023 timesnake
 */

package de.timesnake.channel.proxy.channel;

import de.timesnake.channel.core.Channel;
import de.timesnake.channel.core.Host;
import de.timesnake.channel.proxy.listener.ChannelTimeOutListener;
import de.timesnake.channel.util.message.ChannelPingMessage;
import de.timesnake.channel.util.message.MessageType;
import de.timesnake.library.basic.util.Tuple;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ProxyChannel extends Channel {

    public static ProxyChannel getInstance() {
        return (ProxyChannel) Channel.getInstance();
    }

    protected final Collection<Tuple<String, Host>> pingedHosts = ConcurrentHashMap.newKeySet();
    protected final Collection<ChannelTimeOutListener> timeOutListeners = ConcurrentHashMap.newKeySet();

    public ProxyChannel(Thread mainThread, Integer serverPort, Integer proxyPort) {
        super(mainThread, PROXY_NAME, serverPort, proxyPort);
    }

    @Override
    protected void loadChannelClient() {
        this.client = new ProxyChannelClient(this);
    }

    @Override
    protected void handlePingMessage(ChannelPingMessage msg) {
        this.pingedHosts.remove(new Tuple<>(msg.getSenderName(),
                this.getClient().getHostOfServer(msg.getSenderName())));
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
            this.client.sendMessage(host, new ChannelPingMessage(name, MessageType.Ping.PING));
        }
    }

    public void checkPong() {
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
