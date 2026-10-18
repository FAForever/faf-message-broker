package com.faforever.messagebroker.interceptor.cdi;

import com.faforever.messagebroker.endpoint.session.SessionHandler;
import com.faforever.messagebroker.message.client.ProtocolMessage;
import com.faforever.messagebroker.message.service.InboundPassthroughMessage;
import com.faforever.messagebroker.message.service.OutboundPassthroughMessage;
import com.faforever.messagebroker.message.service.OutboundTarget;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.slf4j.MDC;

@MDCPopulated
@Priority(0)
@Interceptor
public class MDCInterceptor {

    private static final String COMMAND = "command";
    private static final String REQUEST_ID = "requestId";
    private static final String PLAYER_ID = "playerId";
    private static final String SESSION_ID = "sessionId";

    @AroundInvoke
    Object logInvocation(InvocationContext context) throws Exception {
        boolean hasMessage = false;
        boolean hasSession = false;
        boolean hasPlayer = false;
        if (context.getTarget() instanceof SessionHandler sessionHandler) {
            MDC.put(SESSION_ID, Long.toString(sessionHandler.sessionId()));
            hasSession = true;
            Integer playerId = sessionHandler.playerId();
            if (playerId != null) {
                MDC.put(PLAYER_ID, Integer.toString(playerId));
                hasPlayer = true;
            }
        }

        Object[] parameters = context.getParameters();
        if (parameters.length == 1) {
            switch (parameters[0]) {
                case ProtocolMessage protocolMessage -> {
                    MDC.put(REQUEST_ID, protocolMessage.requestId());
                    MDC.put(COMMAND, protocolMessage.command());
                    hasMessage = true;
                }
                case InboundPassthroughMessage inboundPassthroughMessage -> {
                    MDC.put(REQUEST_ID, inboundPassthroughMessage.message().requestId());
                    MDC.put(COMMAND, inboundPassthroughMessage.message().command());
                    hasMessage = true;
                    if (!hasSession) {
                        MDC.put(SESSION_ID, String.valueOf(inboundPassthroughMessage.sessionId()));
                        hasSession = true;
                    }

                    if (!hasPlayer) {
                        MDC.put(PLAYER_ID, String.valueOf(inboundPassthroughMessage.playerId()));
                        hasPlayer = true;
                    }
                }
                case OutboundPassthroughMessage outboundPassthroughMessage -> {
                    MDC.put(REQUEST_ID, outboundPassthroughMessage.message().requestId());
                    MDC.put(COMMAND, outboundPassthroughMessage.message().command());
                    hasMessage = true;
                    switch (outboundPassthroughMessage.target()) {
                        case OutboundTarget.Player(int playerId) -> {
                            if (!hasPlayer) {
                                MDC.pushByKey(PLAYER_ID, String.valueOf(playerId));
                                hasPlayer = true;
                            }
                        }
                        case OutboundTarget.Session(long sessionId) -> {
                            if (!hasSession) {
                                MDC.pushByKey(SESSION_ID, String.valueOf(sessionId));
                                hasSession = true;
                            }
                        }
                        case OutboundTarget.All() -> {}
                    }
                }
                default -> {}
            }
        }

        try {
            return context.proceed();
        } finally {
            if (hasSession) {
                MDC.remove(SESSION_ID);
            }
            if (hasPlayer) {
                MDC.remove(PLAYER_ID);
            }
            if (hasMessage) {
                MDC.remove(COMMAND);
                MDC.remove(REQUEST_ID);
            }
        }
    }

}
