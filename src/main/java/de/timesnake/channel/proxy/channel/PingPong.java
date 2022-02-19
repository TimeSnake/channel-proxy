package de.timesnake.channel.proxy.channel;

import de.timesnake.channel.core.NetworkChannel;
import de.timesnake.channel.proxy.listener.ChannelTimeOutListener;
import de.timesnake.channel.util.message.ChannelPingMessage;
import de.timesnake.channel.util.message.MessageType;

import java.util.Collection;
import java.util.HashSet;

public class PingPong {

    protected final Collection<Integer> pingedPorts = new HashSet<>();

    public void ping(Collection<Integer> ports) {
        for (Integer port : ports) {
            pingedPorts.add(port);
            NetworkChannel.getChannel().sendMessage(port, new ChannelPingMessage(port, MessageType.Ping.PING));
        }
    }

    public void checkPong() {
        for (Integer port : this.pingedPorts) {
            ((Channel) NetworkChannel.getChannel()).handleServerUnregister(port);
        }
        for (Integer port : this.pingedPorts) {
            for (ChannelTimeOutListener listener : ((Channel) NetworkChannel.getChannel()).timeOutListeners) {
                listener.onServerTimeOut(port);
            }
        }
        this.pingedPorts.clear();
    }
}
