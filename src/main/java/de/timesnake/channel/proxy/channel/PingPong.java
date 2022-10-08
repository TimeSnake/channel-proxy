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

import de.timesnake.channel.core.Host;
import de.timesnake.channel.core.NetworkChannel;
import de.timesnake.channel.proxy.listener.ChannelTimeOutListener;
import de.timesnake.channel.util.message.ChannelPingMessage;
import de.timesnake.channel.util.message.MessageType;
import de.timesnake.library.basic.util.Tuple;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class PingPong {

    protected final Collection<Tuple<String, Host>> pingedHosts = ConcurrentHashMap.newKeySet();

    public void ping(Collection<String> names) {
        for (String name : names) {
            Host host = ((Channel) NetworkChannel.getChannel()).getHost(name);

            if (host == null) {
                continue;
            }

            pingedHosts.add(new Tuple<>(name, host));
            NetworkChannel.getChannel().sendMessage(host, new ChannelPingMessage(name, MessageType.Ping.PING));
        }
    }

    public void checkPong() {
        for (Tuple<String, Host> server : this.pingedHosts) {
            ((Channel) NetworkChannel.getChannel()).handleServerUnregister(server.getA(), server.getB());
        }
        for (Tuple<String, Host> server : this.pingedHosts) {
            for (ChannelTimeOutListener listener : ((Channel) NetworkChannel.getChannel()).timeOutListeners) {
                listener.onServerTimeOut(server.getA());
            }
        }
        this.pingedHosts.clear();
    }
}
