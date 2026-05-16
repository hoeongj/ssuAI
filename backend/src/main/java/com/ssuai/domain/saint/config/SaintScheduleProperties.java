package com.ssuai.domain.saint.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the realtime u-SAINT schedule fetcher (Task 16 PR 16b).
 *
 * <p>{@code timetableUrl} points at the SAP WebDynpro ZCMW2102 component
 * — note that this lives on {@code ecc.ssu.ac.kr:8443}, not on
 * {@code saint.ssu.ac.kr}. The cross-subdomain {@code MYSAPSSO2} cookie
 * captured during the SSO callback authenticates against ecc with no
 * extra round-trip (spec §3.1).
 *
 * <p>{@code timeout} caps both connect and read for a single ecc hop.
 * The cumulative-year iterate can fire up to ~5 POSTs back-to-back, so
 * we set this lower than the {@code ssuai.saint.session.ttl} (30 m) by
 * a wide margin to avoid stranding a half-finished iterate.
 */
@Component
@ConfigurationProperties(prefix = "ssuai.saint.schedule")
public class SaintScheduleProperties {

    private String timetableUrl = "https://ecc.ssu.ac.kr:8443/sap/bc/webdynpro/SAP/ZCMW2102";
    private Duration timeout = Duration.ofSeconds(15);

    public String getTimetableUrl() {
        return timetableUrl;
    }

    public void setTimetableUrl(String timetableUrl) {
        this.timetableUrl = timetableUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
