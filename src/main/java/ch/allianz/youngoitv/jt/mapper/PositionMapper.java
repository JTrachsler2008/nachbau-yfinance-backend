package ch.allianz.youngoitv.jt.mapper;

import ch.allianz.youngoitv.jt.dto.PortfolioPositionResponseDto;
import ch.allianz.youngoitv.jt.dto.PositionResponseDto;
import ch.allianz.youngoitv.jt.entity.Position;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PositionMapper {

    @Mapping(target = "securityId", source = "security.id")
    PositionResponseDto toResponseDto(Position position);

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "accountName", source = "account.name")
    @Mapping(target = "securityId", source = "security.id")
    @Mapping(target = "symbol", source = "security.symbol")
    @Mapping(target = "securityName", source = "security.name")
    @Mapping(target = "tradingCurrency", source = "security.tradingCurrency")
    @Mapping(target = "sector", source = "security.sector")
    @Mapping(target = "countryCode", source = "security.countryCode")
    PortfolioPositionResponseDto toPortfolioResponseDto(Position position);
}
