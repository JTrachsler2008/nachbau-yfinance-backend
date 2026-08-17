package ch.allianz.youngoitv.jt.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.allianz.youngoitv.jt.entity.User;
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
}
