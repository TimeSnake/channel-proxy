package de.timesnake.channel.proxy.channel;

import de.timesnake.channel.core.Host;
import de.timesnake.channel.core.NetworkChannel;
import de.timesnake.channel.proxy.listener.ChannelTimeOutListener;
import de.timesnake.channel.util.message.ChannelPingMessage;
import de.timesnake.channel.util.message.MessageType;
import de.timesnake.library.basic.util.Tuple;

import java.util.Collection;
import java.util.HashSet;

public class PingPong {

    protected final Collection<Tuple<Integer, Host>> pingedHosts = new HashSet<>();

    public void ping(Collection<Integer> ports) {
        for (Integer port : ports) {
            Host host = ((Channel) NetworkChannel.getChannel()).getHostByServerPort(port);

            if (host == null) {
                continue;
            }

            pingedHosts.add(new Tuple<>(port, host));
            NetworkChannel.getChannel().sendMessage(host, new ChannelPingMessage(port, MessageType.Ping.PING));
        }
    }

    public void checkPong() {
        for (Tuple<Integer, Host> server : this.pingedHosts) {
            ((Channel) NetworkChannel.getChannel()).handleServerUnregister(server.getA(), server.getB());
        }
        for (Tuple<Integer, Host> server : this.pingedHosts) {
            for (ChannelTimeOutListener listener : ((Channel) NetworkChannel.getChannel()).timeOutListeners) {
                listener.onServerTimeOut(server.getA());
            }
        }
        this.pingedHosts.clear();
    }
}
