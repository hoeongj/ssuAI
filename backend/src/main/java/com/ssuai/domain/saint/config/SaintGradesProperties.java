package com.ssuai.domain.saint.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the realtime u-SAINT grades fetcher (Task 16 PR 16c).
 *
 * <p>{@code gradesUrl} points at the SAP WebDynpro ZCMB3W0017 component
 * — same host (ecc.ssu.ac.kr:8443) as the schedule fetcher but a
 * different module. Cross-subdomain MYSAPSSO2 from the SmartID handshake
 * authenticates here without an extra round-trip (spec §3.5.1).
 *
 * <p>{@code timeout} caps both connect and read for a single ecc hop.
 * The 이전학기 iterate fires one POST per prior term reached from
 * the 학기별 성적 history, so the worst-case grades fetch is on the
 * order of 10–15 hops. Keep this well under the SaintSessionStore TTL.
 */
@Component
@ConfigurationProperties(prefix = "ssuai.saint.grades")
public class SaintGradesProperties {

    private String gradesUrl = "https://ecc.ssu.ac.kr:8443/sap/bc/webdynpro/SAP/ZCMB3W0017";
    private Duration timeout = Duration.ofSeconds(15);

    public String getGradesUrl() {
        return gradesUrl;
    }

    public void setGradesUrl(String gradesUrl) {
        this.gradesUrl = gradesUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
