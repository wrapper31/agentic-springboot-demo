package com.example.orders.persistence.dynamo;

import java.util.List;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Repository
public class OrderAuditRepository {

    private final DynamoDbTable<OrderAuditRecord> table;

    public OrderAuditRepository(DynamoDbTable<OrderAuditRecord> orderAuditTable) {
        this.table = orderAuditTable;
    }

    public void save(OrderAuditRecord record) {
        table.putItem(record);
    }

    public List<OrderAuditRecord> findByOrderId(String orderId) {
        var condition = QueryConditional.keyEqualTo(Key.builder().partitionValue(orderId).build());
        return table.query(condition).items().stream().toList();
    }
}
