package ch.allianz.youngoitv.jt.mapper;

import ch.allianz.youngoitv.jt.dto.LotResponseDto;
import ch.allianz.youngoitv.jt.service.Lot;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LotMapper {

    LotResponseDto toResponseDto(Lot lot);
}
