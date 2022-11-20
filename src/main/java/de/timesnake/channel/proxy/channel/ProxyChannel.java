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
