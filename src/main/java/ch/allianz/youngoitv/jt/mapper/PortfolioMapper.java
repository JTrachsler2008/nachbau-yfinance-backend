package ch.allianz.youngoitv.jt.mapper;

import ch.allianz.youngoitv.jt.dto.PortfolioResponseDto;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PortfolioMapper {

    @Mapping(target = "ownerUsername", source = "user.username")
    @Mapping(target = "managerUserId", source = "manager.id")
    @Mapping(target = "managerUsername", source = "manager.username")
    PortfolioResponseDto toResponseDto(Portfolio portfolio);
}
