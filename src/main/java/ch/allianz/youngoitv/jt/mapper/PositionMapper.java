package ch.allianz.youngoitv.jt.mapper;

import ch.allianz.youngoitv.jt.dto.PositionResponseDto;
import ch.allianz.youngoitv.jt.entity.Position;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PositionMapper {

    @Mapping(target = "securityId", source = "security.id")
    PositionResponseDto toResponseDto(Position position);
}
