package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.entity.User;
import ch.allianz.youngoitv.jt.entity.UserRole;
import ch.allianz.youngoitv.jt.exception.ResourceNotFoundException;
import ch.allianz.youngoitv.jt.exception.UnauthorizedAccessException;
import ch.allianz.youngoitv.jt.exception.UserAlreadyExistsException;
import ch.allianz.youngoitv.jt.repository.UserRepository;
import ch.allianz.youngoitv.jt.service.UserService;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User register(String username, String email, String rawPassword) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new UserAlreadyExistsException("Username '" + username + "' is already taken");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("Email '" + email + "' is already registered");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setCreatedAt(LocalDateTime.now());
        // Selbstregistrierung erhaelt immer PRIVATANLEGER - ein mitgeschicktes role-Feld existiert im
        // RegisterRequestDto bewusst nicht (verhindert Privilege-Escalation ueber Mass-Assignment).
        user.setRole(UserRole.PRIVATANLEGER);
        return userRepository.save(user);
    }

    @Override
    public User getByUsernameOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User '" + username + "' not found"));
    }

    @Override
    public User getByIdOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }

    @Override
    public User updateRole(Long userId, UserRole newRole, String adminUsername) {
        User admin = getByUsernameOrThrow(adminUsername);
        if (admin.getRole() != UserRole.ADMIN) {
            throw new UnauthorizedAccessException("Only an ADMIN may change a user's role");
        }
        User target = getByIdOrThrow(userId);
        target.setRole(newRole);
        return userRepository.save(target);
    }
}
