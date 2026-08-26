package ch.allianz.youngoitv.jt.mapper;

import ch.allianz.youngoitv.jt.dto.PortfolioPositionResponseDto;
import ch.allianz.youngoitv.jt.dto.PositionResponseDto;
import ch.allianz.youngoitv.jt.entity.Position;
import ch.allianz.youngoitv.jt.service.PositionValuation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PositionMapper {

    @Mapping(target = "securityId", source = "security.id")
    PositionResponseDto toResponseDto(Position position);

    // Mit zwei Quellparametern muss jedes Ziel explizit einen Parameternamen nennen, auch die
    // Felder, die schon vorher aus `position` kamen - MapStruct kann sonst nicht entscheiden, aus
    // welchem der beiden Parameter ein gleichnamiges Feld stammen soll.
    @Mapping(target = "accountId", source = "position.account.id")
    @Mapping(target = "accountName", source = "position.account.name")
    @Mapping(target = "securityId", source = "position.security.id")
    @Mapping(target = "symbol", source = "position.security.symbol")
    @Mapping(target = "securityName", source = "position.security.name")
    @Mapping(target = "tradingCurrency", source = "position.security.tradingCurrency")
    @Mapping(target = "sector", source = "position.security.sector")
    @Mapping(target = "countryCode", source = "position.security.countryCode")
    @Mapping(target = "totalQuantity", source = "position.totalQuantity")
    @Mapping(target = "averagePurchasePrice", source = "position.averagePurchasePrice")
    @Mapping(target = "currentPrice", source = "valuation.currentPrice")
    @Mapping(target = "marketValue", source = "valuation.marketValue")
    @Mapping(target = "unrealizedGainLoss", source = "valuation.unrealizedGainLoss")
    PortfolioPositionResponseDto toPortfolioResponseDto(Position position, PositionValuation valuation);
}
