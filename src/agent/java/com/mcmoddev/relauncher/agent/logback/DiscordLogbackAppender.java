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
package com.mcmoddev.relauncher.agent.logback;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Layout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;

public class DiscordLogbackAppender extends AppenderBase<ILoggingEvent> {

    public static final Logger LOG = LoggerFactory.getLogger("DiscordLogbackAppender");

    public static final String POST_URL = "https://discord.com/api/v9/webhooks/%s/%s";

    public static void setup(String webhookId, String webhookToken) throws ClassNotFoundException, ClassCastException {
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        final DiscordLogbackAppender appender = new DiscordLogbackAppender();
        appender.setContext(context);

        final DiscordLogbackLayout layout = new DiscordLogbackLayout();
        layout.setContext(context);
        layout.start();
        appender.setLayout(layout);

        appender.login(webhookId, webhookToken);
        appender.start();

        final ch.qos.logback.classic.Logger rootLogger = context.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(appender);
    }

    /**
     * The Layout.
     */
    private Layout<ILoggingEvent> layout;

    private final HttpClient client = HttpClient.newBuilder()
            .executor(Executors.newSingleThreadExecutor(r -> {
                final Thread thread = new Thread(r);
                thread.setName("DiscordLoggingAppender");
                thread.setDaemon(true);
                return thread;
            }))
            .build();

    private URI uri;

    public void login(String webhookId, String webhookToken) {
        this.uri = URI.create(POST_URL.formatted(webhookId, webhookToken));
    }

    /**
     * Sets the inner {@link Layout}, used for formatting the message to be sent.
     *
     * @param layoutIn The layout
     */
    public void setLayout(final Layout<ILoggingEvent> layoutIn) {
        this.layout = layoutIn;
    }

    @Override
    protected void append(final ILoggingEvent eventObject) {
        if (uri == null) return;
        try {
            final StringBuffer contentBuf = new StringBuffer();
            escape(getMessageContent(eventObject), contentBuf);
            final String body = '{' +
                    "\"content\":\"" + contentBuf + "\"," +
                    "\"allowed_mentions\":{\"parse\": []}" +
                    '}';
            client.send(
                    HttpRequest.newBuilder(uri)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            ).body();
        } catch (IOException | InterruptedException e) {
            LOG.error("Error trying to send webhook message: ", e);
        }
    }

    protected String getMessageContent(final ILoggingEvent event) {
        return layout != null ? layout.doLayout(event) : event.getFormattedMessage();
    }

    private static void escape(String s, StringBuffer sb) {
        final int len = s.length();
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '/':
                    sb.append("\\/");
                    break;
                default:
                    //Reference: http://www.unicode.org/versions/Unicode5.1.0/
                    if (ch <= '\u001F' || ch >= '\u007F' && ch <= '\u009F' || ch >= '\u2000' && ch <= '\u20FF') {
                        String ss = Integer.toHexString(ch);
                        sb.append("\\u");
                        sb.append("0".repeat(4 - ss.length()));
                        sb.append(ss.toUpperCase());
                    } else {
                        sb.append(ch);
                    }
            }
        }
    }
}
