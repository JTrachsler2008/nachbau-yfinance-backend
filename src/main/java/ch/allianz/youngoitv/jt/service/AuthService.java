package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.LoginRequestDto;
import ch.allianz.youngoitv.jt.dto.LoginResponseDto;

public interface AuthService {

    LoginResponseDto login(LoginRequestDto request);
}
