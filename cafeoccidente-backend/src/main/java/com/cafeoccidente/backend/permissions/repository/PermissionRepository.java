package com.cafeoccidente.backend.permissions.repository;

import com.cafeoccidente.backend.permissions.entity.Permission;
import com.cafeoccidente.backend.permissions.entity.Screen;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    List<Permission> findByUserIdOrderByScreen(Long userId);

    Optional<Permission> findByUserIdAndScreen(Long userId, Screen screen);
}
