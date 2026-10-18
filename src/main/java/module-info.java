import org.jspecify.annotations.NullMarked;

@NullMarked
module com.faforever.messagebroker {
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.classmate;
    requires com.github.victools.jsonschema.generator;
    requires com.github.victools.jsonschema.module.jackson;
    requires com.networknt.schema;
    requires io.quarkus.core;
    requires io.smallrye.common.annotation;
    requires io.smallrye.config;
    requires io.vertx.core;
    requires jakarta.annotation;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.interceptor;
    requires jakarta.persistence;
    requires jakarta.transaction;
    requires jakarta.ws.rs;
    requires micrometer.commons;
    requires micrometer.core;
    requires microprofile.jwt.auth.api;
    requires microprofile.rest.client.api;
    requires org.eclipse.microprofile.config;
    requires org.hibernate.orm.core;
    requires org.jspecify;
    requires org.slf4j;
    requires quarkus.hibernate.orm.panache;
    requires quarkus.scheduler.api;
    requires quarkus.websockets.next;
    requires smallrye.jwt;
    requires smallrye.reactive.messaging.api;
    requires smallrye.reactive.messaging.rabbitmq;
    requires tools.jackson.core;
    requires tools.jackson.databind;
    requires org.jboss.logging;

    requires static lombok;
}