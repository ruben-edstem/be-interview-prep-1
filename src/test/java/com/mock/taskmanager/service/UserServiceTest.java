package com.mock.taskmanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mock.taskmanager.dto.response.UserResponse;
import com.mock.taskmanager.entity.Role;
import com.mock.taskmanager.entity.User;
import com.mock.taskmanager.exception.EmailAlreadyRegisteredException;
import com.mock.taskmanager.exception.InvalidRequestParameterException;
import com.mock.taskmanager.exception.UserNotFoundException;
import com.mock.taskmanager.mapper.UserMapper;
import com.mock.taskmanager.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, new UserMapper());
    }

    @Test
    void createStoresANormalizedEmailAndAHashedPassword() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("hashed-pass");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse created = userService.create("  Ada@Example.COM ", "s3cret-pass", Role.USER);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed-pass");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(created.email()).isEqualTo("ada@example.com");
        assertThat(created.role()).isEqualTo(Role.USER);
    }

    @Test
    void createRejectsAnEmailThatIsAlreadyRegistered() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyRegisteredException.class,
                () -> userService.create("ada@example.com", "s3cret-pass", Role.USER));

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void createTurnsAUniqueConstraintRaceIntoAnEmailConflict() {
        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("hashed-pass");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(EmailAlreadyRegisteredException.class,
                () -> userService.create("ada@example.com", "s3cret-pass", Role.USER));
    }

    @Test
    void createIfAbsentCreatesTheUserWhenTheEmailIsFree() {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("hashed-pass");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean created = userService.createIfAbsent("admin@example.com", "s3cret-pass", Role.ADMIN);

        assertThat(created).isTrue();
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void createIfAbsentLeavesAnExistingUserAlone() {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);

        boolean created = userService.createIfAbsent("Admin@Example.com", "s3cret-pass", Role.ADMIN);

        assertThat(created).isFalse();
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void getProfileReturnsTheUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(Role.USER)));

        UserResponse profile = userService.getProfile(USER_ID);

        assertThat(profile.id()).isEqualTo(USER_ID);
        assertThat(profile.email()).isEqualTo("ada@example.com");
    }

    @Test
    void getProfileOfAnUnknownUserThrowsNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getProfile(USER_ID));
    }

    @Test
    void listMapsEveryUserOnThePage() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt"));
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user(Role.ADMIN)), pageable, 1));

        Page<UserResponse> page = userService.list(pageable);

        assertThat(page.getContent()).extracting(UserResponse::role).containsExactly(Role.ADMIN);
    }

    @Test
    void listRejectsSortingByThePasswordHash() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("passwordHash"));

        InvalidRequestParameterException thrown =
                assertThrows(InvalidRequestParameterException.class, () -> userService.list(pageable));

        assertThat(thrown.getParameter()).isEqualTo("sort");
        assertThat(thrown.getReason()).isEqualTo("cannot sort by passwordHash");
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listRejectsAPageOffsetBeyondTheIntegerRange() {
        Pageable pageable = PageRequest.of(Integer.MAX_VALUE, 20);

        InvalidRequestParameterException thrown =
                assertThrows(InvalidRequestParameterException.class, () -> userService.list(pageable));

        assertThat(thrown.getParameter()).isEqualTo("page");
    }

    private User user(Role role) {
        return User.builder().id(USER_ID).email("ada@example.com").passwordHash("hashed-pass").role(role).build();
    }
}
