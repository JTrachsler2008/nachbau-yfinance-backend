package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.SecurityCreateRequestDto;
import ch.allianz.youngoitv.jt.dto.SecurityLookupOrCreateRequestDto;
import ch.allianz.youngoitv.jt.dto.SecurityResponseDto;
import ch.allianz.youngoitv.jt.dto.SecuritySearchResultDto;
import ch.allianz.youngoitv.jt.mapper.SecurityMapper;
import ch.allianz.youngoitv.jt.service.SecurityService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/securities")
public class SecurityController {

    private final SecurityService securityService;
    private final SecurityMapper securityMapper;

    public SecurityController(SecurityService securityService, SecurityMapper securityMapper) {
        this.securityService = securityService;
        this.securityMapper = securityMapper;
    }

    @PostMapping
    public ResponseEntity<SecurityResponseDto> create(
            Principal principal, @Valid @RequestBody SecurityCreateRequestDto request) {
        var security = securityService.create(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(securityMapper.toResponseDto(security));
    }

    /** Liste aller Wertpapiere für das Auswahlfeld im Transaktionsformular. */
    @GetMapping
    public List<SecurityResponseDto> list() {
        return securityService.listAll().stream().map(securityMapper::toResponseDto).toList();
    }

    @GetMapping("/{symbol}")
    public SecurityResponseDto getBySymbol(@PathVariable String symbol) {
        return securityMapper.toResponseDto(securityService.getBySymbolOrThrow(symbol));
    }

    /**
     * Live-Suche beim Marktdatenanbieter, für die Vorschläge im Kaufformular.
     *
     * <p>Anders als {@link #list} nicht der Stammdatenbestand: das Kaufformular braucht Vorschläge für
     * Symbole, die es hier unter Umständen noch gar nicht gibt.</p>
     */
    @GetMapping("/search")
    public List<SecuritySearchResultDto> search(@RequestParam String query) {
        return securityService.search(query).stream()
                .map(result -> new SecuritySearchResultDto(
                        result.symbol(), result.name(), result.exchange(), result.quoteType()))
                .toList();
    }

    /**
     * Legt ein Wertpapier aus der Live-Suche an, oder liefert es, falls es bereits existiert.
     *
     * <p>Absichtlich ohne Admin-Prüfung: die enge, dokumentierte Ausnahme steht in
     * {@link ch.allianz.youngoitv.jt.service.SecurityService#lookupOrCreate}.</p>
     */
    @PostMapping("/lookup-or-create")
    public SecurityResponseDto lookupOrCreate(@Valid @RequestBody SecurityLookupOrCreateRequestDto request) {
        return securityMapper.toResponseDto(securityService.lookupOrCreate(request.symbol()));
    }
}
