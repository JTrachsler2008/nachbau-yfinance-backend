package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.entity.User;

public interface UserService {

    User register(String username, String email, String rawPassword);

    User getByUsernameOrThrow(String username);
}
