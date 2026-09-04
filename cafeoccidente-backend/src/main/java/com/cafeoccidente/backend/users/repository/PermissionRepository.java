package com.cafeoccidente.backend.users.repository;

import com.cafeoccidente.backend.users.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
}
