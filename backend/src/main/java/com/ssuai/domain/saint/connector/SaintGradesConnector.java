package com.ssuai.domain.saint.connector;

import com.ssuai.domain.auth.saint.PortalCookies;
import com.ssuai.domain.saint.dto.GradesResponse;
import com.ssuai.global.exception.SaintSessionExpiredException;

/**
 * Fetches a student's cumulative u-SAINT grades (학기별 GPA history +
 * 학적부/증명 누적 통계 + 학기별 세부), keyed by their captured portal
 * cookies. Profile-switched via {@code ssuai.connector.saint-grades}
 * (mock | real).
 *
 * <p>An implementation MUST throw {@link SaintSessionExpiredException}
 * when the supplied cookies no longer authenticate against
 * {@code ecc.ssu.ac.kr} — same contract as {@link SaintScheduleConnector}.
 */
public interface SaintGradesConnector {

    GradesResponse fetchGrades(String studentId, PortalCookies cookies);
}
