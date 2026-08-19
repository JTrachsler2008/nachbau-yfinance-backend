package ch.allianz.youngoitv.jt.service.impl;

import ch.allianz.youngoitv.jt.dto.PortfolioCreateRequestDto;
import ch.allianz.youngoitv.jt.dto.PortfolioUpdateRequestDto;
import ch.allianz.youngoitv.jt.entity.Portfolio;
import ch.allianz.youngoitv.jt.entity.User;
import ch.allianz.youngoitv.jt.entity.UserRole;
import ch.allianz.youngoitv.jt.exception.InvalidRoleAssignmentException;
import ch.allianz.youngoitv.jt.exception.ResourceNotFoundException;
import ch.allianz.youngoitv.jt.exception.UnauthorizedAccessException;
import ch.allianz.youngoitv.jt.repository.PortfolioRepository;
import ch.allianz.youngoitv.jt.security.OwnerCheckService;
import ch.allianz.youngoitv.jt.service.PortfolioService;
import ch.allianz.youngoitv.jt.service.UserService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioServiceImpl implements PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final UserService userService;
    private final OwnerCheckService ownerCheckService;

    public PortfolioServiceImpl(
            PortfolioRepository portfolioRepository, UserService userService, OwnerCheckService ownerCheckService) {
        this.portfolioRepository = portfolioRepository;
        this.userService = userService;
        this.ownerCheckService = ownerCheckService;
    }

    @Override
    public Portfolio create(String username, PortfolioCreateRequestDto request) {
        User owner = userService.getByUsernameOrThrow(username);

        Portfolio portfolio = new Portfolio();
        portfolio.setUser(owner);
        portfolio.setName(request.name());
        portfolio.setBaseCurrency(request.baseCurrency());
        portfolio.setDescription(request.description());
        LocalDateTime now = LocalDateTime.now();
        portfolio.setCreatedAt(now);
        portfolio.setUpdatedAt(now);
        return portfolioRepository.save(portfolio);
    }

    @Override
    public List<Portfolio> listOwnedBy(String username) {
        User owner = userService.getByUsernameOrThrow(username);
        return portfolioRepository.findByUserId(owner.getId());
    }

    @Override
    public Portfolio getOwnedOrThrow(Long portfolioId, String username) {
        User principal = userService.getByUsernameOrThrow(username);
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio " + portfolioId + " not found"));

        if (!ownerCheckService.isAuthorizedForPortfolio(portfolio, principal)) {
            throw new UnauthorizedAccessException("Portfolio " + portfolioId + " does not belong to this user");
        }
        return portfolio;
    }

    @Override
    public Portfolio update(Long portfolioId, String username, PortfolioUpdateRequestDto request) {
        Portfolio portfolio = getOwnedOrThrow(portfolioId, username);

        if (request.name() != null) {
            portfolio.setName(request.name());
        }
        if (request.baseCurrency() != null) {
            portfolio.setBaseCurrency(request.baseCurrency());
        }
        if (request.description() != null) {
            portfolio.setDescription(request.description());
        }
        portfolio.setUpdatedAt(LocalDateTime.now());
        return portfolioRepository.save(portfolio);
    }

    @Override
    @Transactional
    public void delete(Long portfolioId, String username) {
        Portfolio portfolio = getOwnedOrThrow(portfolioId, username);
        portfolioRepository.delete(portfolio);
    }

    @Override
    public Portfolio assignManager(Long portfolioId, String ownerUsername, Long managerUserId) {
        User owner = userService.getByUsernameOrThrow(ownerUsername);
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio " + portfolioId + " not found"));

        if (!ownerCheckService.isOwner(portfolio, owner)) {
            throw new UnauthorizedAccessException(
                    "Only the owner of portfolio " + portfolioId + " may assign a manager");
        }

        if (managerUserId == null) {
            portfolio.setManager(null);
        } else {
            User manager = userService.getByIdOrThrow(managerUserId);
            if (manager.getRole() != UserRole.MANAGER) {
                throw new InvalidRoleAssignmentException(
                        "User " + managerUserId + " does not have the MANAGER role");
            }
            portfolio.setManager(manager);
        }
        return portfolioRepository.save(portfolio);
    }
}
