package com.cafeoccidente.backend.users.repository;

import com.cafeoccidente.backend.users.entity.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByIdNumber(String idNumber);

    /** Bloquea los usuarios activos de un rol: dos cambios a la vez no pueden dejar el sistema sin Administrador. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.active = true AND u.role.name = :role")
    List<User> lockActiveByRole(@Param("role") String role);
}
