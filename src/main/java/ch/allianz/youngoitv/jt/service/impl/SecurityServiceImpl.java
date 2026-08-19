package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.dto.SecurityCreateRequestDto;
import ch.allianz.youngoitv.jt.entity.Security;
import ch.allianz.youngoitv.jt.exception.InvalidSecurityDataException;
import ch.allianz.youngoitv.jt.exception.ResourceNotFoundException;
import ch.allianz.youngoitv.jt.repository.SecurityRepository;
import ch.allianz.youngoitv.jt.service.SecurityService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class SecurityServiceImpl implements SecurityService {

    private static final String BOND = "BOND";

    private final SecurityRepository securityRepository;

    public SecurityServiceImpl(SecurityRepository securityRepository) {
        this.securityRepository = securityRepository;
    }

    @Override
    public Security create(SecurityCreateRequestDto request) {
        boolean isBond = BOND.equalsIgnoreCase(request.assetType());
        if (!isBond && (request.couponRate() != null || request.maturityDate() != null)) {
            throw new InvalidSecurityDataException(
                    "couponRate/maturityDate are only allowed for assetType BOND, not " + request.assetType());
        }

        Security security = new Security();
        security.setSymbol(request.symbol());
        security.setIsin(request.isin());
        security.setName(request.name());
        security.setAssetType(request.assetType());
        security.setExchangeCode(request.exchangeCode());
        security.setTradingCurrency(request.tradingCurrency());
        security.setCountryCode(request.countryCode());
        security.setSector(request.sector());
        security.setCouponRate(isBond ? request.couponRate() : null);
        security.setMaturityDate(isBond ? request.maturityDate() : null);
        LocalDateTime now = LocalDateTime.now();
        security.setCreatedAt(now);
        security.setUpdatedAt(now);
        return securityRepository.save(security);
    }

    @Override
    public Security getBySymbolOrThrow(String symbol) {
        return securityRepository.findBySymbol(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Security '" + symbol + "' not found"));
    }

    @Override
    public Security getByIdOrThrow(Long id) {
        return securityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Security " + id + " not found"));
    }
}
