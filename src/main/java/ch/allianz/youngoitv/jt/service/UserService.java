package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.entity.User;
import ch.allianz.youngoitv.jt.entity.UserRole;

public interface UserService {

    User register(String username, String email, String rawPassword);

    User getByUsernameOrThrow(String username);

    User getByIdOrThrow(Long id);

    User updateRole(Long userId, UserRole newRole, String adminUsername);
}
