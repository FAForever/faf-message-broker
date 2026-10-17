package com.faforever.server.message.client;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

sealed public interface ConnectionMessage {

    sealed interface Outgoing extends ConnectionMessage, ProtocolMessage.Outgoing {}

    sealed interface Incoming extends ConnectionMessage, ProtocolMessage.Incoming {}

    record Ping(String requestId) implements Outgoing, Incoming {
        public Ping(@Nullable String requestId) {
            this.requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
        }
    }

    record Pong(String requestId) implements Outgoing, Incoming {
        public Pong(@Nullable String requestId) {
            this.requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
        }
    }

    record SessionRequest(
            String requestId,
            @Nullable String userAgent,
            @Nullable String version
    ) implements Incoming {
        public SessionRequest(@Nullable String requestId, @Nullable String userAgent, @Nullable String version) {
            this.requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
            this.userAgent = userAgent;
            this.version = version;
        }
    }

    record AuthenticateRequest(
            String requestId,
            String token,
            String uniqueId
    ) implements Incoming {
        public AuthenticateRequest(@Nullable String requestId, String token, String uniqueId) {
            this.requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
            this.token = token;
            this.uniqueId = uniqueId;
        }
    }

    record SessionResponse(
            String requestId,
            long session
    ) implements Outgoing {
        public SessionResponse(@Nullable String requestId, long session) {
            this.requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
            this.session = session;
        }
    }

    record LoginFailureResponse(
            String requestId,
            @Nullable String text
    ) implements Outgoing {
        public LoginFailureResponse(@Nullable String requestId, @Nullable String text) {
            this.requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
            this.text = text;
        }
    }
}
