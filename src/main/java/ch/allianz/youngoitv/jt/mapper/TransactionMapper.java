package ch.allianz.youngoitv.jt.mapper;

import ch.allianz.youngoitv.jt.dto.PortfolioTransactionResponseDto;
import ch.allianz.youngoitv.jt.dto.TransactionResponseDto;
import ch.allianz.youngoitv.jt.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "securityId", source = "security.id")
    TransactionResponseDto toResponseDto(Transaction transaction);

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "accountName", source = "account.name")
    @Mapping(target = "securityId", source = "security.id")
    @Mapping(target = "symbol", source = "security.symbol")
    @Mapping(target = "securityName", source = "security.name")
    PortfolioTransactionResponseDto toPortfolioResponseDto(Transaction transaction);
}
