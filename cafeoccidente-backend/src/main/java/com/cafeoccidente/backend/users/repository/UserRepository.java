package com.cafeoccidente.backend.users.repository;

import com.cafeoccidente.backend.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
