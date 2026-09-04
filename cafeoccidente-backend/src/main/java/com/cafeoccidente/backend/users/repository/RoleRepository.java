package com.cafeoccidente.backend.users.repository;

import com.cafeoccidente.backend.users.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
}
