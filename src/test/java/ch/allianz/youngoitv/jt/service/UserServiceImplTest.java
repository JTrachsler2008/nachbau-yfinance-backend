package ch.allianz.youngoitv.jt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.allianz.youngoitv.jt.entity.User;
import ch.allianz.youngoitv.jt.entity.UserRole;
import ch.allianz.youngoitv.jt.exception.UnauthorizedAccessException;
import ch.allianz.youngoitv.jt.exception.UserAlreadyExistsException;
import ch.allianz.youngoitv.jt.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceImplTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registerStoresHashedPasswordNotPlaintext() {
        User saved = userService.register("alice", "alice@example.com", "s3cret!");

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getPasswordHash()).isNotEqualTo("s3cret!");
        assertThat(passwordEncoder.matches("s3cret!", reloaded.getPasswordHash())).isTrue();
    }

    @Test
    void wrongPasswordDoesNotMatch() {
        User saved = userService.register("bob", "bob@example.com", "correct-password");
        User reloaded = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(passwordEncoder.matches("wrong-password", reloaded.getPasswordHash())).isFalse();
    }

    @Test
    void duplicateUsernameIsRejected() {
        userService.register("gina", "gina@example.com", "password123");

        assertThatThrownBy(() -> userService.register("gina", "other@example.com", "password123"))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void duplicateEmailIsRejected() {
        userService.register("hank", "hank@example.com", "password123");

        assertThatThrownBy(() -> userService.register("other-hank", "hank@example.com", "password123"))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void selfRegistrationAlwaysGetsThePrivatanlegerRole() {
        User saved = userService.register("ivo", "ivo@example.com", "password123");

        assertThat(saved.getRole()).isEqualTo(UserRole.PRIVATANLEGER);
    }

    @Test
    void adminCanChangeAnotherUsersRole() {
        User admin = userService.register("admin1", "admin1@example.com", "password123");
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
        User target = userService.register("jonas", "jonas@example.com", "password123");

        User updated = userService.updateRole(target.getId(), UserRole.MANAGER, "admin1");

        assertThat(updated.getRole()).isEqualTo(UserRole.MANAGER);
    }

    @Test
    void nonAdminCannotChangeAnyonesRole() {
        userService.register("karl", "karl@example.com", "password123");
        User target = userService.register("lea", "lea@example.com", "password123");

        assertThatThrownBy(() -> userService.updateRole(target.getId(), UserRole.ADMIN, "karl"))
                .isInstanceOf(UnauthorizedAccessException.class);
    }
}
