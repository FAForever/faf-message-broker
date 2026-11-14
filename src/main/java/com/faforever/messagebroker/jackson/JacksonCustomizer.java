package com.faforever.messagebroker.jackson;

import io.quarkus.jackson.JsonMapperBuilderCustomizer;
import jakarta.inject.Singleton;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@Singleton
public class JacksonCustomizer implements JsonMapperBuilderCustomizer {
    @Override
    public void customize(JsonMapper.Builder builder) {
        builder.propertyNamingStrategy(new PropertyNamingStrategies.SnakeCaseStrategy());
    }
}
