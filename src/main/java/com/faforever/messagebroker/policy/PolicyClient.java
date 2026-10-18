package com.faforever.messagebroker.policy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@ApplicationScoped
@RegisterRestClient(configKey = "policy-server")
public interface PolicyClient {

    @Path("/verify")
    @POST
    void sendVerificationDetails(PolicyContents policyContents);

}
