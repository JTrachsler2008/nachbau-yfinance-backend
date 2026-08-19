package ch.allianz.youngoitv.jt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Zentrale RestClient-Beans mit konfigurierten Timeouts fuer externe Kursdatenquellen (behebt ARC-11:
 * kein ad-hoc {@code new RestTemplate()}/{@code new RestClient()} mehr in den Providern). URLs sind
 * ueber Properties konfigurierbar, nicht hartcodiert (behebt ARC-7).
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient yFinanceRestClient(
            @Value("${app.market-data.yfinance.base-url}") String baseUrl,
            @Value("${app.market-data.yfinance.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${app.market-data.yfinance.read-timeout-ms:3000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Bean
    public RestClient alphaVantageRestClient(
            @Value("${app.market-data.alphavantage.base-url}") String baseUrl,
            @Value("${app.market-data.alphavantage.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${app.market-data.alphavantage.read-timeout-ms:3000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
