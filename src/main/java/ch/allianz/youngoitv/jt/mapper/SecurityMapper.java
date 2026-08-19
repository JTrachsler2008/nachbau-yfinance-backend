package ch.allianz.youngoitv.jt.mapper;

import ch.allianz.youngoitv.jt.dto.SecurityResponseDto;
import ch.allianz.youngoitv.jt.entity.Security;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SecurityMapper {

    SecurityResponseDto toResponseDto(Security security);
}
