package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.SecurityCreateRequestDto;
import ch.allianz.youngoitv.jt.entity.Security;

public interface SecurityService {

    Security create(SecurityCreateRequestDto request, String requesterUsername);

    Security getBySymbolOrThrow(String symbol);

    Security getByIdOrThrow(Long id);
}
