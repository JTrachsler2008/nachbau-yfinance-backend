package ch.allianz.youngoitv.jt.service;

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
}
