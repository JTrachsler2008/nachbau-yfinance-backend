package ch.allianz.youngoitv.jt.mapper;

import ch.allianz.youngoitv.jt.dto.PortfolioResponseDto;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PortfolioMapper {

    PortfolioResponseDto toResponseDto(Portfolio portfolio);
}
