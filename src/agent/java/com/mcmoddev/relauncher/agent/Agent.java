/*
 * ReLauncher - https://github.com/MinecraftModDevelopment/ReLauncher
 * Copyright (C) 2016-2024 <MMD - MinecraftModDevelopment>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * Specifically version 2.1 of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 * https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 */
package com.mcmoddev.relauncher.agent;

import com.mcmoddev.relauncher.agent.logback.DiscordLogbackAppender;
import com.mcmoddev.relauncher.api.StatusListener;
import com.mcmoddev.relauncher.api.connector.ProcessConnector;

import java.lang.instrument.Instrumentation;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ServiceLoader;
import java.util.function.Consumer;

public final class Agent {
    public static final String VERSION;

    static {
        var version = Agent.class.getPackage().getImplementationVersion();
        if (version == null) {
            version = "DEV " + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.UTC));
        }
        VERSION = version;
    }

    // Keep a "strong" reference to the connector and its registry, to avoid GC picking it up
    private static Registry registry;
    private static ProcessConnector server;

    public static void premain(String args, Instrumentation inst) {
        final var split = args.split("/;/");
        final var name = split[0];
        if (split.length > 1) {
            final var webhookInfo = split[1].split("%%");
            try {
                DiscordLogbackAppender.setup(webhookInfo[0], webhookInfo[1]);
                System.out.println(colour("Discord Logging has been setup using webhook with ID " + webhookInfo[0]));
            } catch (ClassNotFoundException | ClassCastException e) {
                System.err.println("Cannot setup Discord webhook logging as Logback is not found on the classpath!");
            }
        }

        System.out.println(colour("ReLauncher Agent v" + VERSION + " installed."));
        executeOnListeners(l -> l.premain(inst));

        System.out.println(colour("Starting RMI on port " + ProcessConnector.PORT + " with name '" + name + "'"));
        try {
            System.setProperty("java.rmi.server.hostname", "127.0.0.1");
            registry = LocateRegistry.createRegistry(ProcessConnector.PORT);
            server = new ProcessConnectorServer();

            final ProcessConnector stub = (ProcessConnector) UnicastRemoteObject.exportObject(server, ProcessConnector.PORT);
            registry.rebind(name, stub);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    registry.unbind(name);
                } catch (RemoteException | NotBoundException e) {
                    e.printStackTrace();
                }
            }));
            System.out.println(colour("Process Connector ready!"));

            executeOnListeners(StatusListener::onStartup);
        } catch (Exception e) {
            System.err.println("Exception starting RMI server: " + e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }

    public static String colour(String text) {
        return "\033[94;1m==== \033[36;1m" + text
                + " \033[94;1m====\033[0m";
    }

    public static void executeOnListeners(Consumer<? super StatusListener> consumer) {
        final ServiceLoader<StatusListener> loader = ServiceLoader.load(StatusListener.class);
        for (final StatusListener listener : loader) {
            consumer.accept(listener);
        }
    }

    private Agent() {
        throw new UnsupportedOperationException("Cannot instantiate an agent!");
    }

}
