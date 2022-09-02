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
