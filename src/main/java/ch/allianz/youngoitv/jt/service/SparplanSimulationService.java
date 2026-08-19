package ch.allianz.youngoitv.jt.service;

import ch.allianz.youngoitv.jt.dto.SparplanRequestDto;
import ch.allianz.youngoitv.jt.dto.SparplanResponseDto;

public interface SparplanSimulationService {

    SparplanResponseDto simulate(SparplanRequestDto request);
}
