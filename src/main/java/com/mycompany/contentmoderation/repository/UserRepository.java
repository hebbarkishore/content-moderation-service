package com.mycompany.contentmoderation.repository;

import com.mycompany.contentmoderation.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByFileKey(String fileKey);
}
