package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.client.SecuritySearchResult;
import ch.allianz.youngoitv.jt.dto.SecurityCreateRequestDto;
import ch.allianz.youngoitv.jt.entity.Security;
import java.util.List;

public interface SecurityService {

    Security create(SecurityCreateRequestDto request, String requesterUsername);

    Security getBySymbolOrThrow(String symbol);

    Security getByIdOrThrow(Long id);

    /**
     * Alle Wertpapiere, nach Symbol sortiert.
     *
     * <p>Der Stammdatenbestand ist für alle Benutzer derselbe, deshalb ohne Eigentumsprüfung. Angelegt
     * werden Wertpapiere nur von Administratoren, gelesen von jedem angemeldeten Benutzer, weil das
     * Transaktionsformular ein Auswahlfeld über den Bestand braucht.</p>
     */
    List<Security> listAll();

    /**
     * Live-Suche beim Marktdatenanbieter, für die Vorschläge im Kaufformular.
     *
     * <p>Liefert eine leere Liste statt eines Fehlers, wenn der Anbieter nicht erreichbar ist oder
     * nichts findet: eine leere Vorschlagsliste ist ein normaler Bedienzustand, kein Grund, das
     * Formular mit einer Fehlermeldung zu blockieren.</p>
     */
    List<SecuritySearchResult> search(String query);

    /**
     * Liefert das Wertpapier zum Symbol, legt es bei Bedarf aus Live-Marktdaten an.
     *
     * <p>Bewusste, enge Ausnahme vom Admin-only-Prinzip (YOUNGOITV-441): jeder angemeldete Benutzer
     * darf hierüber ein Wertpapier anlegen, aber nur eines, das der Marktdatenanbieter im Moment des
     * Aufrufs mit einem echten Kurs bestätigt - kein freies Anlegen beliebiger Stammdaten wie im
     * Admin-Formular.</p>
     *
     * @throws ch.allianz.youngoitv.jt.exception.ResourceNotFoundException wenn der Marktdatenanbieter
     *     keinen Kurs für das Symbol liefert
     */
    Security lookupOrCreate(String symbol);
}
