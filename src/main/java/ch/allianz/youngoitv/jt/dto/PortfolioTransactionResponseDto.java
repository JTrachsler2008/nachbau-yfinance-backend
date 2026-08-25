package ch.allianz.youngoitv.jt.dto;

import ch.allianz.youngoitv.jt.entity.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Transaktion in der Listenansicht eines Portfolios.
 *
 * <p>Eigener Typ neben {@link TransactionResponseDto}: die Antwort auf das Anlegen bezieht sich auf
 * ein bekanntes Konto und ein bekanntes Wertpapier, eine Liste über das ganze Portfolio nicht. Ohne
 * Kontoname und Symbol müsste die Oberfläche beide Angaben je Zeile nachladen, und ohne accountId
 * liesse sich die im UI/UX-Plan geforderte Filterung nach Konto nicht bauen.</p>
 */
public record PortfolioTransactionResponseDto(
        Long id,
        Long accountId,
        String accountName,
        Long securityId,
        String symbol,
        String securityName,
        TransactionType transactionType,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fee,
        BigDecimal tax,
        BigDecimal splitRatio,
        String transactionCurrency,
        BigDecimal fxRateToPortfolio,
        LocalDate transactionDate) {
}
