package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.client.MarketDataProvider;
import ch.allianz.youngoitv.jt.client.Quote;
import ch.allianz.youngoitv.jt.client.SecurityInfo;
import ch.allianz.youngoitv.jt.client.SecuritySearchResult;
import ch.allianz.youngoitv.jt.dto.SecurityCreateRequestDto;
import ch.allianz.youngoitv.jt.entity.Security;
import ch.allianz.youngoitv.jt.exception.InvalidSecurityDataException;
import ch.allianz.youngoitv.jt.exception.ResourceNotFoundException;
import ch.allianz.youngoitv.jt.repository.SecurityRepository;
import ch.allianz.youngoitv.jt.security.AdminCheckService;
import ch.allianz.youngoitv.jt.service.SecurityService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class SecurityServiceImpl implements SecurityService {

    private static final String BOND = "BOND";

    /** Anlageart, wenn die Suche keinen genauen Treffer für das Symbol liefert (siehe {@link #lookupOrCreate}). */
    private static final String DEFAULT_ASSET_TYPE = "STOCK";

    private final SecurityRepository securityRepository;
    private final AdminCheckService adminCheckService;
    private final MarketDataProvider marketDataProvider;

    public SecurityServiceImpl(
            SecurityRepository securityRepository,
            AdminCheckService adminCheckService,
            MarketDataProvider marketDataProvider) {
        this.securityRepository = securityRepository;
        this.adminCheckService = adminCheckService;
        this.marketDataProvider = marketDataProvider;
    }

    @Override
    public Security create(SecurityCreateRequestDto request, String requesterUsername) {
        adminCheckService.requireAdmin(requesterUsername);
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

    @Override
    public List<Security> listAll() {
        return securityRepository.findAll(Sort.by("symbol"));
    }

    @Override
    public List<SecuritySearchResult> search(String query) {
        return marketDataProvider.search(query.trim()).orElse(List.of());
    }

    @Override
    public Security lookupOrCreate(String symbol) {
        String upperSymbol = symbol.trim().toUpperCase();
        return securityRepository.findBySymbol(upperSymbol).orElseGet(() -> create(upperSymbol));
    }

    /**
     * Legt ein Wertpapier aus Live-Marktdaten an.
     *
     * <p>Ohne einen bestätigten Kurs bleibt es beim 404: ein Symbol, das der Marktdatenanbieter nicht
     * kennt, würde sonst ein Wertpapier anlegen, das nie einen Preis bekommt. Name, Sektor und Land
     * kommen zusätzlich aus der Suche bzw. {@code getInfo}, wenn verfügbar - fehlen sie, bleiben die
     * Felder leer statt mit dem Symbol oder einer Vermutung aufgefüllt zu werden.</p>
     *
     * <p>Der Save läuft in einem eigenen try/catch für den Fall, dass zwei Anfragen für dasselbe neue
     * Symbol gleichzeitig hier ankommen: die zweite verletzt den Unique-Index auf {@code symbol} und
     * bekommt statt eines 500ers das inzwischen von der ersten angelegte Wertpapier zurück.</p>
     */
    private Security create(String upperSymbol) {
        Quote quote = marketDataProvider.getQuote(upperSymbol)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No live quote available for " + upperSymbol + ", cannot register it automatically"));
        SecuritySearchResult match = marketDataProvider.search(upperSymbol).orElse(List.of()).stream()
                .filter(candidate -> candidate.symbol().equalsIgnoreCase(upperSymbol))
                .findFirst()
                .orElse(null);
        SecurityInfo info = marketDataProvider.getInfo(upperSymbol).orElse(null);

        Security security = new Security();
        security.setSymbol(upperSymbol);
        security.setName(nameFor(upperSymbol, info, match));
        security.setAssetType(match != null ? match.quoteType() : DEFAULT_ASSET_TYPE);
        security.setTradingCurrency(quote.currency());
        security.setExchangeCode(match != null ? match.exchange() : null);
        security.setSector(info != null ? info.sector() : null);
        security.setCountryCode(info != null ? CountryCodes.iso2(info.country()) : null);
        LocalDateTime now = LocalDateTime.now();
        security.setCreatedAt(now);
        security.setUpdatedAt(now);

        try {
            return securityRepository.save(security);
        } catch (DataIntegrityViolationException alreadyCreatedConcurrently) {
            return getBySymbolOrThrow(upperSymbol);
        }
    }

    private static String nameFor(String upperSymbol, SecurityInfo info, SecuritySearchResult match) {
        if (info != null && info.name() != null) {
            return info.name();
        }
        if (match != null && match.name() != null) {
            return match.name();
        }
        return upperSymbol;
    }
}
