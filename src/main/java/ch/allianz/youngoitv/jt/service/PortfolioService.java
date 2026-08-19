package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.PortfolioCreateRequestDto;
import ch.allianz.youngoitv.jt.dto.PortfolioUpdateRequestDto;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import java.util.List;

public interface PortfolioService {

    Portfolio create(String username, PortfolioCreateRequestDto request);

    List<Portfolio> listOwnedBy(String username);

    Portfolio getOwnedOrThrow(Long portfolioId, String username);

    Portfolio update(Long portfolioId, String username, PortfolioUpdateRequestDto request);

    void delete(Long portfolioId, String username);
}
