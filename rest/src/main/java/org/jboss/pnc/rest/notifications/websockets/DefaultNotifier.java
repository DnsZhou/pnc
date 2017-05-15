/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.rest.notifications.websockets;

import org.jboss.pnc.spi.notifications.AttachedClient;
import org.jboss.pnc.spi.notifications.MessageCallback;
import org.jboss.pnc.spi.notifications.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Notification mechanism for Web Sockets. All implementation details should be placed in AttachedClient.
 */
@ApplicationScoped
public class DefaultNotifier implements Notifier {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private Set<AttachedClient> attachedClients = Collections.synchronizedSet(new HashSet<>());

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final MessageCallback messageCallback = new MessageCallback() {

        @Override
        public void successful(AttachedClient attachedClient) {
            // logger.debug("Successfully sent message to client ", attachedClient);
        }

        @Override
        public void failed(AttachedClient attachedClient, Throwable throwable) {
            logger.error("Notification client threw an error, removing it", throwable);
            detachClient(attachedClient);
        }
    };

    @PostConstruct
    public void init() {
        scheduler.scheduleAtFixedRate(this::cleanUp, 1, 1, TimeUnit.HOURS);
    }

    @Override
    public void attachClient(AttachedClient attachedClient) {
        synchronized (attachedClients) {
            attachedClients.add(attachedClient);
        }
    }

    @Override
    public void detachClient(AttachedClient attachedClient) {
        try {
            synchronized (attachedClients) {
                attachedClients.remove(attachedClient);
            }
        } catch (ConcurrentModificationException cme) {
            logger.error("Error while removing attached client: ", cme);
        }
    }

    @Override
    public int getAttachedClientsCount() {
        return attachedClients.size();
    }

    @Override
    public Optional<AttachedClient> getAttachedClient(String sessionId) {
        return attachedClients.stream()
                .filter(client -> client.getSessionId().equals(sessionId))
                .findAny();
    }

    @Override
    public MessageCallback getCallback() {
        return messageCallback;
    }

    @Override
    public void sendMessage(Object message) {
        try {
            for (Iterator<AttachedClient> attachedClientIterator = attachedClients.iterator(); attachedClientIterator
                    .hasNext();) {
                AttachedClient client = attachedClientIterator.next();
                if (client.isEnabled()) {
                    try {
                        client.sendMessage(message, messageCallback);
                    } catch (Exception e) {
                        logger.error("Unable to send message, detaching client.", e);
                        detachClient(client);
                    }
                }
            }
        } catch (ConcurrentModificationException cme) {
            logger.warn("Error while removing attached client: ", cme);
        }
    }

    @Override
    public void sendToSubscribers(Object message, String topic, String qualifier) {
        try {
            for (Iterator<AttachedClient> attachedClientIterator = attachedClients.iterator(); attachedClientIterator
                    .hasNext();) {
                AttachedClient client = attachedClientIterator.next();
                if (client.isEnabled()) {
                    if (client.isSubscribed(topic, qualifier))
                        try {
                            client.sendMessage(message, messageCallback);
                        } catch (Exception e) {
                            logger.error("Unable to send message, detaching client.", e);
                            detachClient(client);
                        }
                }
            }
        } catch (ConcurrentModificationException cme) {
            logger.warn("Error while removing attached client: ", cme);
        }
    }

    public void cleanUp() {
        synchronized (attachedClients) {
            for (Iterator<AttachedClient> attachedClientIterator = attachedClients.iterator(); attachedClientIterator
                    .hasNext();) {
                AttachedClient client = attachedClientIterator.next();
                if (!client.isEnabled()) {
                    attachedClientIterator.remove();
                }
            }
        }
    }

}
