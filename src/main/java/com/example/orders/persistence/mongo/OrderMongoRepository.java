package com.example.orders.persistence.mongo;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrderMongoRepository extends MongoRepository<OrderDocument, String> {

    List<OrderDocument> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
