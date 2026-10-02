package com.sender.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
	boolean existsByEmail(String email);
	boolean existsByUsername(String username);
	Optional<User> findByEmail(String email);

	@Query("""
		select u from User u
		where u.id <> :userId
		  and (lower(u.username) like lower(concat('%', :query, '%'))
		    or lower(u.displayName) like lower(concat('%', :query, '%')))
		order by u.username
		""")
	List<User> search(@Param("userId") Integer userId, @Param("query") String query, Pageable pageable);
}
