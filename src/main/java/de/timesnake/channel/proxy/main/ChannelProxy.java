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

package de.timesnake.channel.proxy.main;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import de.timesnake.channel.core.ChannelLogger;
import de.timesnake.channel.core.NetworkChannel;
import de.timesnake.channel.core.SyncRun;
import de.timesnake.channel.proxy.channel.Channel;

import java.util.logging.Logger;

@Plugin(id = "channel-proxy", name = "ChannelProxy", version = "1.0-SNAPSHOT",
        url = "https://git.timesnake.de", authors = {"MarkusNils"})
public class ChannelProxy {

    public static void start(Integer port) {
        NetworkChannel.start(new Channel(Thread.currentThread(), port, port, new ChannelLogger() {
            @Override
            public void printInfo(String msg) {
                logger.info("[Channel] " + msg);
            }

            @Override
            public void printWarning(String msg) {
                logger.warning("[Channel] " + msg);
            }
        }) {
            @Override
            public void runSync(SyncRun syncRun) {
                server.getScheduler().buildTask(getPlugin(), syncRun::run).schedule();
            }
        });
    }

    public static ChannelProxy getPlugin() {
        return plugin;
    }

    private static ChannelProxy plugin;
    private static ProxyServer server;
    private static Logger logger;

    @Inject
    public ChannelProxy(ProxyServer server, Logger logger) {
        ChannelProxy.server = server;
        ChannelProxy.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        plugin = this;
        ChannelProxy.start(25565);
    }
}
