package ch.allianz.youngoitv.jt.mapper;

import ch.allianz.youngoitv.jt.dto.TransactionResponseDto;
import ch.allianz.youngoitv.jt.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "securityId", source = "security.id")
    TransactionResponseDto toResponseDto(Transaction transaction);
}
