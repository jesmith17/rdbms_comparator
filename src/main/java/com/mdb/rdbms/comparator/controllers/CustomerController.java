package com.mdb.rdbms.comparator.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdb.rdbms.comparator.models.Customer;
import com.mdb.rdbms.comparator.models.CustomerSearch;
import com.mdb.rdbms.comparator.services.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping(value="api/customers", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
@CrossOrigin(origins= "http://localhost:4200")
public class CustomerController {

    @Autowired
    CustomerService service;




    @PostMapping
    public Customer create(@RequestBody Customer customer){
        return service.create(customer);
    }


    @PatchMapping("{id}")
    public Customer update(@PathVariable("id") Integer id, @RequestBody Customer customer){
        return service.update(id, customer);
    }

    @PostMapping("search")
    public Page<Customer> getCustomers(@RequestParam(name = "db", required = false, defaultValue="pg") String db,
                                       @RequestBody CustomerSearch customerSearch, @RequestParam(name="page", required = false, defaultValue = "0") int page ){

        return this.service.getCustomers(db, customerSearch, page);
    }

    @GetMapping("{id}")
    public Customer getCustomer(@PathVariable("id") Integer id,@RequestParam(name = "db", required = false, defaultValue="pg") String db ){
        return service.getCustomerById(id, db);
    }

}
