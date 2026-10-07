package com.mock.taskmanager.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mock.taskmanager.entity.Role;
import com.mock.taskmanager.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmailReturnsTheMatchingUser() {
        userRepository.save(user("ada@example.com", Role.USER));
        userRepository.save(user("grace@example.com", Role.ADMIN));

        User found = userRepository.findByEmail("grace@example.com").orElseThrow();

        assertThat(found.getRole()).isEqualTo(Role.ADMIN);
        assertThat(found.getId()).isNotNull();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void findByEmailReturnsEmptyForAnUnknownEmail() {
        userRepository.save(user("ada@example.com", Role.USER));

        assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void existsByEmailReflectsWhatIsStored() {
        userRepository.save(user("ada@example.com", Role.USER));

        assertThat(userRepository.existsByEmail("ada@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    void savingTwoUsersWithTheSameEmailViolatesTheUniqueConstraint() {
        userRepository.saveAndFlush(user("ada@example.com", Role.USER));

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(user("ada@example.com", Role.ADMIN)));
    }

    private User user(String email, Role role) {
        return User.builder().email(email).passwordHash("hashed-pass").role(role).build();
    }
}
