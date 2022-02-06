package de.timesnake.channel.proxy.channel;

import de.timesnake.channel.api.message.ChannelPingMessage;
import de.timesnake.channel.main.NetworkChannel;
import de.timesnake.channel.proxy.listener.ChannelTimeOutListener;

import java.util.Collection;
import java.util.HashSet;

public class PingPong {

    protected final Collection<Integer> pingedPorts = new HashSet<>();

    public void ping(Collection<Integer> ports) {
        for (Integer port : ports) {
            pingedPorts.add(port);
            NetworkChannel.getChannel().sendMessage(port, ChannelPingMessage.getPingMessage(port));
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
