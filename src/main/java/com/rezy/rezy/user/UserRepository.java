package com.rezy.rezy.user;

import com.rezy.rezy.user.domain.AuthProvider;
import com.rezy.rezy.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmailAndProvider(String email, AuthProvider provider);
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerUserId);
    boolean existsByEmailAndProvider(String email, AuthProvider provider);
}
