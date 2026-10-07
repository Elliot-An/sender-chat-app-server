package com.sender.backend.friendship;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface FriendshipRepository extends JpaRepository<Friendship, Integer> {
    @Query("""
        select f from Friendship f
        where (f.requester.id = :userId or f.addressee.id = :userId)
          and f.status = :status
        order by f.updatedAt desc
        """)
    List<Friendship> findAcceptedForUser(@Param("userId") Integer userId, @Param("status") Friendship.Status status);

    @Query("""
        select case when f.requester.id = :userId then f.addressee.id else f.requester.id end
        from Friendship f
        where (f.requester.id = :userId or f.addressee.id = :userId)
          and f.status = :status
        """)
    List<Integer> findFriendIds(@Param("userId") Integer userId, @Param("status") Friendship.Status status);

    List<Friendship> findByAddresseeIdAndStatusOrderByCreatedAtDesc(Integer userId, Friendship.Status status);

    @Query("""
        select f from Friendship f
        where (f.requester.id = :first and f.addressee.id = :second)
           or (f.requester.id = :second and f.addressee.id = :first)
        order by f.createdAt desc
        """)
    List<Friendship> findBetween(Integer first, Integer second);
}
