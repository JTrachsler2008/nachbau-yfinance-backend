package ch.allianz.youngoitv.jt.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.allianz.youngoitv.jt.entity.Portfolio;
import ch.allianz.youngoitv.jt.entity.User;
import ch.allianz.youngoitv.jt.repository.PortfolioRepository;
import ch.allianz.youngoitv.jt.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OwnerCheckServiceTest {

    @Autowired
    private OwnerCheckService ownerCheckService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("irrelevant-for-this-test");
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    private Portfolio createPortfolio(User owner) {
        Portfolio portfolio = new Portfolio();
        portfolio.setUser(owner);
        portfolio.setName("Test-Portfolio");
        portfolio.setBaseCurrency("CHF");
        portfolio.setCreatedAt(LocalDateTime.now());
        portfolio.setUpdatedAt(LocalDateTime.now());
        return portfolioRepository.save(portfolio);
    }

    @Test
    void ownerIsAuthorized() {
        User owner = createUser("frank");
        Portfolio portfolio = createPortfolio(owner);

        assertThat(ownerCheckService.isAuthorizedForPortfolio(portfolio, owner)).isTrue();
    }

    @Test
    void nonOwnerIsNotAuthorized() {
        User owner = createUser("gina");
        User stranger = createUser("hank");
        Portfolio portfolio = createPortfolio(owner);

        assertThat(ownerCheckService.isAuthorizedForPortfolio(portfolio, stranger)).isFalse();
    }
}
