
package com.klu.crmbackend.repository;

import com.klu.crmbackend.model.Customer;
import com.klu.crmbackend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByEmail(String email);

    // Customers assigned to a specific user
    List<Customer> findByAssignedTo(User user);

    // Customers assigned to users with a specific role
    List<Customer> findByAssignedTo_Role(String role);
}
