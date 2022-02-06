package de.timesnake.channel.proxy.listener;

import de.timesnake.channel.listener.ChannelListener;

public interface ChannelRegisterListener extends ChannelListener {

    /**
     * Called then server register message received
     *
     * @param port          The port of the server send register message
     * @param isRegistering true if sender starts, else false
     */
    void onChannelRegisterMessage(int port, boolean isRegistering);
}
