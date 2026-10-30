
package com.klu.crmbackend.repository;

import com.klu.crmbackend.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    @Override
    @EntityGraph(attributePaths = {"company", "manager", "manager.company"})
    List<User> findAll();

    @Override
    @EntityGraph(attributePaths = {"company", "manager", "manager.company"})
    Optional<User> findById(Long id);
}
