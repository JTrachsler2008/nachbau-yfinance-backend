package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.MeResponseDto;
import ch.allianz.youngoitv.jt.dto.RegisterRequestDto;
import ch.allianz.youngoitv.jt.dto.UpdateRoleRequestDto;
import ch.allianz.youngoitv.jt.dto.UserResponseDto;
import ch.allianz.youngoitv.jt.mapper.UserMapper;
import ch.allianz.youngoitv.jt.service.UserService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    public MeResponseDto me(Principal principal) {
        var user = userService.getByUsernameOrThrow(principal.getName());
        return new MeResponseDto(user.getUsername(), user.getRole());
    }

    @PostMapping
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        var user = userService.register(request.username(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(userMapper.toResponseDto(user));
    }

    @PatchMapping("/{id}/role")
    public UserResponseDto updateRole(
            Principal principal, @PathVariable Long id, @Valid @RequestBody UpdateRoleRequestDto request) {
        var user = userService.updateRole(id, request.role(), principal.getName());
        return userMapper.toResponseDto(user);
    }
}
