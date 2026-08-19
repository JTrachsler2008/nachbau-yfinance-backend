package ch.allianz.youngoitv.jt.mapper;

import ch.allianz.youngoitv.jt.dto.AccountResponseDto;
import ch.allianz.youngoitv.jt.entity.Account;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    AccountResponseDto toResponseDto(Account account);
}
