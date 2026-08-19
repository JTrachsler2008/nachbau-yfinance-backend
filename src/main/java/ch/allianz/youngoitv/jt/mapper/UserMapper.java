package ch.allianz.youngoitv.jt.mapper;

import ch.allianz.youngoitv.jt.dto.UserResponseDto;
import ch.allianz.youngoitv.jt.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponseDto toResponseDto(User user);
}
