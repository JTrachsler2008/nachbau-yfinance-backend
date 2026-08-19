package ch.allianz.youngoitv.jt.mapper;

import ch.allianz.youngoitv.jt.dto.FxRateResponseDto;
import ch.allianz.youngoitv.jt.entity.FxRate;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FxRateMapper {

    FxRateResponseDto toResponseDto(FxRate fxRate);
}
