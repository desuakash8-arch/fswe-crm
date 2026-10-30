package com.klu.crmbackend.repository;
import com.klu.crmbackend.model.PasswordReset;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;
public interface PasswordResetRepository extends JpaRepository<PasswordReset,String> {
    @Modifying @Query("update PasswordReset p set p.used=true where p.userId=:userId")
    void invalidateForUser(Long userId);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select p from PasswordReset p where p.tokenHash=:hash")
    Optional<PasswordReset> lock(String hash);
}
