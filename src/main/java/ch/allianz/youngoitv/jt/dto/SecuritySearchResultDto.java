package ch.allianz.youngoitv.jt.dto;

/** Ein Treffer der Live-Suche beim Marktdatenanbieter (`SecuritySearchResult`). */
public record SecuritySearchResultDto(String symbol, String name, String exchange, String quoteType) {
}
