package com.example.orders.config;

import com.example.orders.persistence.dynamo.OrderAuditRecord;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;

@Configuration
public class DynamoDbConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbConfig.class);

    @Bean
    DynamoDbClient dynamoDbClient(AppProperties props) {
        var builder = DynamoDbClient.builder().region(Region.of(props.dynamodb().region()));
        if (StringUtils.hasText(props.dynamodb().endpoint())) {
            builder.endpointOverride(URI.create(props.dynamodb().endpoint()));
        }
        return builder.build();
    }

    @Bean
    DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
    }

    @Bean
    DynamoDbTable<OrderAuditRecord> orderAuditTable(DynamoDbEnhancedClient client, AppProperties props) {
        DynamoDbTable<OrderAuditRecord> table =
                client.table(props.dynamodb().auditTable(), TableSchema.fromBean(OrderAuditRecord.class));
        if (props.dynamodb().createTable()) {
            try {
                table.createTable();
                log.info("Created DynamoDB table {}", props.dynamodb().auditTable());
            } catch (ResourceInUseException alreadyExists) {
                log.debug("DynamoDB table {} already exists", props.dynamodb().auditTable());
            }
        }
        return table;
    }
}
