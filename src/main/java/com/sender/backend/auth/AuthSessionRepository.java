package com.sender.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Integer> {
	Optional<AuthSession> findByTokenHash(String tokenHash);
	List<AuthSession> findAllByUserId(Integer userId);
}
