package com.faforever.messagebroker.endpoint.connection;


import com.faforever.messagebroker.message.client.ProtocolMessage;
import lombok.extern.jbosslog.JBossLog;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@JBossLog
public abstract class LobbyConnection {

    private final BlockingQueue<ProtocolMessage.Outgoing> outgoingQueue = new LinkedBlockingQueue<>();

    private @Nullable Thread drainThread;

    public void startDrain() {
        if (drainThread != null && drainThread.isAlive()) {
            return;
        }

        drainThread = Thread.ofVirtual().start(this::doDrain);
    }

    public final void queue(ProtocolMessage.Outgoing message) {
        if (isClosed()) {
            LOG.warn("Connection already closed");
            return;
        }
        if (!outgoingQueue.offer(message)) {
            LOG.warn("Unable to add message to queue");
        }
    }

    private void doDrain() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                send(outgoingQueue.take());
            }
        } catch (InterruptedException _) {
            LOG.debug("Shutting down drain thread");
        }

        ProtocolMessage.Outgoing message;
        while ((message = outgoingQueue.poll()) != null) {
            send(message);
        }
        LOG.debug("Drain done");
    }

    protected abstract void send(ProtocolMessage.Outgoing message);

    public final void close() {
        if (drainThread != null && drainThread.isAlive()) {
            drainThread.interrupt();
            boolean drainFinished;
            try {
                drainFinished = drainThread.join(Duration.ofSeconds(10));
            } catch (InterruptedException e) {
                drainFinished = false;
            }
            if (!drainFinished) {
                LOG.warn("Interrupted while waiting for drain to finish may cause queued messages to not be sent");
            }
        }

        if (!isClosed()) {
            doClose();
        }
    }

    protected abstract void doClose();

    public abstract boolean isClosed();

    public abstract String remoteIp();

}
