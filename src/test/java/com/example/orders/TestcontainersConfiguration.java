package com.example.orders;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** Real MongoDB, Kafka and DynamoDB (LocalStack) for integration tests. Requires Docker. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static {
        // The AWS SDK reads credentials from system properties; LocalStack accepts any values.
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
    }

    @Bean
    @ServiceConnection
    MongoDBContainer mongoDbContainer() {
        return new MongoDBContainer(DockerImageName.parse("mongo:7.0"));
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.1"));
    }

    @Bean
    LocalStackContainer localStackContainer() {
        return new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                .withServices(LocalStackContainer.Service.DYNAMODB);
    }

    @Bean
    DynamicPropertyRegistrar dynamoProperties(LocalStackContainer localStack) {
        return registry -> {
            registry.add("app.dynamodb.endpoint", () -> localStack.getEndpoint().toString());
            registry.add("app.dynamodb.region", localStack::getRegion);
            registry.add("app.dynamodb.create-table", () -> "true");
        };
    }
}
