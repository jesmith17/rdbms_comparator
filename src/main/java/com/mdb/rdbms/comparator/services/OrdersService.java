package com.mdb.rdbms.comparator.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mdb.rdbms.comparator.models.*;
import com.mdb.rdbms.comparator.models.Order;
import com.mdb.rdbms.comparator.repositories.jpa.*;
import com.mdb.rdbms.comparator.repositories.mongo.OrderMongoRepository;
import com.mdb.rdbms.comparator.repositories.mongo.ProductMongoRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.*;
import org.bson.BsonArray;
import org.bson.Document;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;

@Service
public class OrdersService {

    @Autowired
    OrderMongoRepository mongoRepo;

    @Autowired
    EntityManager entityManager;

    @Autowired
    OrderJPARepository jpaRepo;

    @Autowired
    ProductJPARepository productJPARepository;

    @Autowired
    CustomerJPARepository customerJPARepository;

    @Autowired
    StoreJPARepository storeJPARepository;

    @Autowired
    AddressJpaRepository addressJPARepository;

    @Autowired
    ProductMongoRepository productRepo;

    @Autowired
    MeterRegistry registry;


    public Order create(Order order) {

        mongoRepo.save(order);
        // How do we do the extended reference pattern correctly?


        for (OrderDetails details: order.getDetails()){
            Product product = productJPARepository.findById(details.getProduct_id()).get();
            details.setProduct(product);
        }
        order.setCustomer(customerJPARepository.findById(order.getCustomer_id()).get());
        order.setStore(storeJPARepository.findById(order.getStore_id()).get());
        order.setShippingAddress(addressJPARepository.findById(order.getShippingAddressId()).get());
        return jpaRepo.save(order);

    }

    public List<Order> createBatch(List<Order> orders){
        List<Order> results = jpaRepo.saveAll(orders);
        mongoRepo.saveAll(results);
        return results;
    }


    public Page<Order> getAllOrders(String db, OrderSearch orderSearch, int page){
        Pageable paging = PageRequest.of(page, 100);
        if (db.equals("mongodb")) {
            return mongoSearch(orderSearch, paging);
        } else {
            return this.jpaSearch( orderSearch, paging);
        }
    }


    private Page<Order> mongoSearch(OrderSearch orderSearch, Pageable paging){
        HashMap<String, Object> params = new HashMap<>();
        Page<Order> result = null;
        int skip = 0;
        int limit = 100;
        double startCount = registry.counter("queries.issued").count();
        if (!orderSearch.isLucene()) {
           params = this.mongoQuery(params, orderSearch);
           result = mongoRepo.searchOrders(params ,paging);
        } else {
            JsonArray jsonArray = this.mongoLucene(orderSearch);
            List<Document> query = new ArrayList<>();
            for (JsonElement jsonElement : jsonArray) {
                query.add(Document.parse(jsonElement.toString()));
            }
            System.out.println(query.toString());
            List<Order> searchResults = mongoRepo.searchOrdersLucene(query, skip, limit);
            long totalRecords = mongoRepo.count();
            result = new PageImpl<>(searchResults, paging, totalRecords);
        }
        double queriesCount = registry.counter("queries.issued").count() - startCount;
        return new MetricsPage<Order>(result, queriesCount);
    }


    private Page<Order> jpaSearch(OrderSearch orderSearch, Pageable paging){

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Order> criteria = cb.createQuery(Order.class);
        Root<Order> root = criteria.from(Order.class);
        OrderSpecification orderSpec = new OrderSpecification(orderSearch);
        Session session = entityManager.unwrap(Session.class);
        Statistics stats = session.getSessionFactory().getStatistics();
        stats.clear();
        Page<Order> results = jpaRepo.findAll(orderSpec, paging);
        System.out.println(stats.getQueryExecutionCount());
        return new MetricsPage<>(results, stats.getPrepareStatementCount());
    }

    private HashMap<String, Object> mongoQuery(HashMap<String, Object> params, OrderSearch orderSearch){
        try {
            for (Field field : orderSearch.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (field.get(orderSearch) != null && (field.getType().equals(Address.class) || field.getType().equals(Customer.class) || field.getType().equals(Store.class))) {
                    Object subObj = field.get(orderSearch);
                    for (Field subField : subObj.getClass().getDeclaredFields()) {
                        subField.setAccessible(true);
                        if (subField.get(subObj) != null && !subField.get(subObj).toString().isEmpty()) {
                            params.put(field.getName() + "." + subField.getName(), subField.get(subObj));
                        }
                    }
                } else {
                    if (field.get(orderSearch) != null && !field.get(orderSearch).toString().isEmpty()) {
                        params.put(field.getName(), field.get(orderSearch));
                    }
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        params.remove("customer.phones");
        params.remove("customer.emails");
        params.remove("lucene");
        params.values().removeIf(Objects::isNull);
        return params;
    }

    private JsonArray mongoLucene( OrderSearch orderSearch){
        orderSearch.getCustomer().setPhones(null);
        orderSearch.getCustomer().setEmails(null);
        JsonArray json = new JsonArray();
        HashMap<String, Object> queryStringParams = new HashMap<>();
        try {
            for (Field field : orderSearch.getClass().getDeclaredFields()) {
                if (field.getName().equals("lucene")){
                    continue;
                }
                field.setAccessible(true);
                if (field.get(orderSearch) != null && (field.getType().equals(Address.class) || field.getType().equals(Customer.class) || field.getType().equals(Store.class))) {
                    Object subObj = field.get(orderSearch);
                    for (Field subField : subObj.getClass().getDeclaredFields()) {
                        subField.setAccessible(true);
                        if (subField.get(subObj) != null && !subField.get(subObj).toString().isEmpty()) {
                            if (Number.class.isAssignableFrom(subField.getType())) {
                                JsonObject equals = new JsonObject();
                                equals.addProperty("path", field.getName() + "." + subField.getName());
                                equals.addProperty("value", (Number)subField.get(subObj));
                                JsonObject entry = new JsonObject();
                                entry.add("equals", equals);
                                json.add(entry);
                            } else {
                                queryStringParams.put(field.getName() + "." + subField.getName(), subField.get(subObj));
                            }
                        }
                    }
                } else {
                    if (field.get(orderSearch) != null && !field.get(orderSearch).toString().isEmpty()) {
                        if (Number.class.isAssignableFrom(field.getType())) {
                            JsonObject equals = new JsonObject();
                            equals.addProperty("path", field.getName());
                            equals.addProperty("value", (Number)field.get(orderSearch));
                            JsonObject entry = new JsonObject();
                            entry.add("equals", equals);
                            json.add(entry);
                        } else {
                            queryStringParams.put(field.getName(), field.get(orderSearch));
                        }
                    }
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        StringBuilder builder = new StringBuilder();
        for (String key : queryStringParams.keySet()) {
            boolean firstLoop = builder.isEmpty();
            if (!firstLoop) {
                builder.append(" AND ");
            } else {
                builder.append(" ( ");
            }
            builder.append(key + " : " + queryStringParams.get(key).toString());
        }
        builder.append(" ) ");
        JsonObject queryStringElement = new JsonObject();
        queryStringElement.addProperty("defaultPath", "orderStatus");
        queryStringElement.addProperty("query", builder.toString());
        JsonObject queryString = new JsonObject();
        queryString.add("queryString", queryStringElement);
        json.add(queryString);


        return json;
    }



}
