package com.mdb.rdbms.comparator.repositories.mongo;

import com.mdb.rdbms.comparator.models.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;

@Repository
public interface CustomerMongoRepository extends MongoRepository<Customer, Integer> {


    @Query(value="?0", sort = "{'firstName': -1, 'lastName': 1, 'address.city':  1}")
    Page<Customer> sortCustomers(HashMap<String, Object> params, Pageable paging);




    @Aggregation(pipeline= {"{'$search': {index:'customer_search', text:{ query:?0, fuzzy:{},path:['lastName','firstName','address.city','address.state','address.zip','phones.number','emails.email']}}}",
            "{'$skip': ?1}", "{'$limit': ?2}",
            "{'$project': {title: 1,firstName: 1,lastName: 1,address: 1,phones: 1,emails: 1,meta: {count: '$$SEARCH_META.count.total', score: { $meta: 'searchScore'},token: { $meta: 'searchSequenceToken'}}}}"})
    List<Customer> searchCustomers(String queryTerm, int skip, int limit);



}
