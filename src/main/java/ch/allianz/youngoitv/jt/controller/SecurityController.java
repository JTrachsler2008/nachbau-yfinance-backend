package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.SecurityCreateRequestDto;
import ch.allianz.youngoitv.jt.dto.SecurityResponseDto;
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
}
