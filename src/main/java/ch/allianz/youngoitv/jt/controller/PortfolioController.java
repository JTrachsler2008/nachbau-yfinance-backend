package ch.allianz.youngoitv.jt.controller;

import ch.allianz.youngoitv.jt.dto.ManagerAssignRequestDto;
import ch.allianz.youngoitv.jt.dto.PortfolioCreateRequestDto;
import ch.allianz.youngoitv.jt.dto.PortfolioResponseDto;
import ch.allianz.youngoitv.jt.dto.PortfolioUpdateRequestDto;
import ch.allianz.youngoitv.jt.mapper.PortfolioMapper;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioMapper portfolioMapper;

    public PortfolioController(PortfolioService portfolioService, PortfolioMapper portfolioMapper) {
        this.portfolioService = portfolioService;
        this.portfolioMapper = portfolioMapper;
    }

    @PostMapping
    public ResponseEntity<PortfolioResponseDto> create(
            Principal principal, @Valid @RequestBody PortfolioCreateRequestDto request) {
        var portfolio = portfolioService.create(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(portfolioMapper.toResponseDto(portfolio));
    }

    @GetMapping
    public List<PortfolioResponseDto> list(Principal principal) {
        return portfolioService.listOwnedBy(principal.getName()).stream()
                .map(portfolioMapper::toResponseDto)
                .toList();
    }

    @GetMapping("/{id}")
    public PortfolioResponseDto get(Principal principal, @PathVariable Long id) {
        return portfolioMapper.toResponseDto(portfolioService.getOwnedOrThrow(id, principal.getName()));
    }

    @PatchMapping("/{id}")
    public PortfolioResponseDto update(
            Principal principal, @PathVariable Long id, @Valid @RequestBody PortfolioUpdateRequestDto request) {
        return portfolioMapper.toResponseDto(portfolioService.update(id, principal.getName(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Principal principal, @PathVariable Long id) {
        portfolioService.delete(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/manager")
    public PortfolioResponseDto assignManager(
            Principal principal, @PathVariable Long id, @RequestBody ManagerAssignRequestDto request) {
        var portfolio = portfolioService.assignManager(id, principal.getName(), request.managerUserId());
        return portfolioMapper.toResponseDto(portfolio);
    }
}
