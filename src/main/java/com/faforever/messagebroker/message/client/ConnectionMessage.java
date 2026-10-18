package com.faforever.messagebroker.message.client;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

sealed public interface ConnectionMessage {

    sealed interface Outgoing extends ConnectionMessage, ProtocolMessage.Outgoing {}

    sealed interface Incoming extends ConnectionMessage, ProtocolMessage.Incoming {}

    record Ping(String requestId) implements Outgoing, Incoming {
        public static final String COMMAND = "ping";

        public Ping(@Nullable String requestId) {
            this.requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
        }

        @Override
        public String command() {
            return COMMAND;
        }
    }

    record Pong(String requestId) implements Outgoing, Incoming {
        public static final String COMMAND = "pong";

        public Pong(@Nullable String requestId) {
            this.requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
        }

        @Override
        public String command() {
            return COMMAND;
        }
    }

    record SessionRequest(
            String requestId,
            @Nullable String userAgent,
            @Nullable String version
    ) implements Incoming {
        public static final String COMMAND = "ask_session";

        public SessionRequest(@Nullable String requestId, @Nullable String userAgent, @Nullable String version) {
            this.requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
            this.userAgent = userAgent;
            this.version = version;
        }

        @Override
        public String command() {
            return COMMAND;
        }
    }

    record AuthenticateRequest(
            String requestId,
            String token,
            String uniqueId
    ) implements Incoming {
        public static final String COMMAND = "auth";

        public AuthenticateRequest(@Nullable String requestId, String token, String uniqueId) {
            this.requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
            this.token = token;
            this.uniqueId = uniqueId;
        }

        @Override
        public String command() {
            return COMMAND;
        }
    }

    record SessionResponse(
            String requestId,
            long session
    ) implements Outgoing {
        public static final String COMMAND = "session";

        public SessionResponse(@Nullable String requestId, long session) {
            this.requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
            this.session = session;
        }

        @Override
        public String command() {
            return COMMAND;
        }
    }

    record LoginFailureResponse(
            String requestId,
            @Nullable String text
    ) implements Outgoing {
        public static final String COMMAND = "authentication_failure";

        public LoginFailureResponse(@Nullable String requestId, @Nullable String text) {
            this.requestId = requestId == null ? UUID.randomUUID().toString() : requestId;
            this.text = text;
        }

        @Override
        public String command() {
            return COMMAND;
        }
    }
}
