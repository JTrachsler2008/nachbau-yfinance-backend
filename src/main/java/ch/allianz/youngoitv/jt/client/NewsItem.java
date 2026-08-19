package ch.allianz.youngoitv.jt.client;

import java.time.Instant;

public record NewsItem(String title, String url, Instant publishedAt) {
}
