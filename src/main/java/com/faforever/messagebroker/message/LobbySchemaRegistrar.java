package com.faforever.messagebroker.message;

import com.faforever.messagebroker.message.client.ProtocolMessage;
import com.faforever.messagebroker.message.service.LobbySchemaDetails;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaKeyword;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
@RequiredArgsConstructor
public class LobbySchemaRegistrar {

    private static final JacksonSchemaModule JACKSON_MODULE = new JacksonSchemaModule(
            JacksonOption.RESPECT_JSONPROPERTY_REQUIRED, JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY);
    private static final PropertyNamingStrategies.SnakeCaseStrategy SNAKE_CASE_STRATEGY = new PropertyNamingStrategies.SnakeCaseStrategy();

    private final MessageBroker messageBroker;

    @Startup
    void registerSchemas() {
        registerSchemasUsingTypeInfo(ProtocolMessage.Incoming.class, LobbySchemaDetails.Direction.INBOUND);
        registerSchemasUsingTypeInfo(ProtocolMessage.Outgoing.class, LobbySchemaDetails.Direction.OUTBOUND);
    }

    private void registerSchemasUsingTypeInfo(Class<?> baseMessageClass, LobbySchemaDetails.Direction direction) {
        JsonTypeInfo typeInfo = baseMessageClass.getAnnotation(JsonTypeInfo.class);
        if (typeInfo == null) {
            throw new IllegalArgumentException("No JsonTypeInfo");
        }
        JsonSubTypes jsonSubTypes = baseMessageClass.getAnnotation(JsonSubTypes.class);
        if (jsonSubTypes == null) {
            throw new IllegalArgumentException("No JsonSubTypes");
        }
        Map<? extends Class<?>, String> identifierMap = Arrays.stream(jsonSubTypes.value())
                                                              .collect(Collectors.toMap(JsonSubTypes.Type::value,
                                                                                        JsonSubTypes.Type::name));
        String propertyName = typeInfo.property();

        SchemaGeneratorConfigBuilder builder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12,
                                                                                OptionPreset.PLAIN_JSON).with(
                                                                                                                JACKSON_MODULE)
                                                                                                        .with(Option.STANDARD_FORMATS,
                                                                                                              Option.ADDITIONAL_FIXED_TYPES,
                                                                                                              Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT);


        builder.forFields()
               .withPropertyNameOverrideResolver(
                       fieldScope -> SNAKE_CASE_STRATEGY.nameForField(null, null, fieldScope.getName()))
               .withNullableCheck(
                       fieldScope -> fieldScope.getRawMember().getAnnotatedType().getAnnotation(Nullable.class) != null)
               .withRequiredCheck(
                       fieldScope -> fieldScope.getRawMember().getAnnotatedType().getAnnotation(Nullable.class) ==
                                     null);

        builder.forTypesInGeneral().withTypeAttributeOverride((objectNode, typeScope, context) -> {
            String command = identifierMap.get(typeScope.getType().getErasedType());
            if (command == null) {
                return;
            }

            objectNode.withObjectProperty(context.getKeyword(SchemaKeyword.TAG_PROPERTIES))
                      .putObject(propertyName)
                      .put(context.getKeyword(SchemaKeyword.TAG_CONST), command);
            objectNode.withArrayProperty(context.getKeyword(SchemaKeyword.TAG_REQUIRED)).add(propertyName);
        });

        SchemaGeneratorConfig config = builder.build();
        SchemaGenerator generator = new SchemaGenerator(config);

        identifierMap.forEach((messageClass, command) -> {
            ObjectNode schemaNode = generator.generateSchema(messageClass);
            messageBroker.handleLobbySchemaRegistration(new LobbySchemaDetails(command, direction, schemaNode,
                                                                               "connection"));
        });
    }
}
