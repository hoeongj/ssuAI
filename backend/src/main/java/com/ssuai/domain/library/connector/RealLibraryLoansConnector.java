package com.ssuai.domain.library.connector;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.ssuai.domain.library.dto.LibraryLoanItem;
import com.ssuai.domain.library.dto.LibraryLoansResponse;
import com.ssuai.global.exception.ConnectorParseException;
import com.ssuai.global.exception.ConnectorTimeoutException;
import com.ssuai.global.exception.ConnectorUnavailableException;
import com.ssuai.global.exception.LibraryAuthRequiredException;

/**
 * Fetches the authenticated user's current library loans from
 * oasis.ssu.ac.kr via GET /pyxis-api/1/charges?offset=0&max=20.
 * Returns empty list when the upstream responds with success.noRecord.
 */
@Component
@ConditionalOnProperty(name = "ssuai.connector.library-loans", havingValue = "real")
public class RealLibraryLoansConnector implements LibraryLoansConnector {

    private static final Logger log = LoggerFactory.getLogger(RealLibraryLoansConnector.class);

    private static final String LOANS_PATH = "/pyxis-api/1/charges?offset=0&max=20";
    private static final String NEED_LOGIN_CODE = "error.authentication.needLogin";
    private static final String NO_RECORD_CODE = "success.noRecord";

    private final LibrarySeatProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RealLibraryLoansConnector(
            LibrarySeatProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("librarySeatRestClient") RestClient restClient
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public LibraryLoansResponse fetchLoans(String token) {
        String body = callUpstream(token);
        return parseBody(body);
    }

    private String callUpstream(String token) {
        try {
            return restClient.get()
                    .uri(LOANS_PATH)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Pyxis-Auth-Token", token != null ? token : "")
                    .header("Referer", properties.getReferer())
                    .header("Accept-Language", "ko")
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException exception) {
            log.warn("library loans connector timeout/io");
            throw new ConnectorTimeoutException(exception);
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            log.warn("library loans connector http error: status={}", status.value());
            if (status.is5xxServerError()) {
                throw new ConnectorUnavailableException(exception);
            }
            throw new ConnectorParseException(exception);
        }
    }

    private LibraryLoansResponse parseBody(String body) {
        if (body == null || body.isBlank()) {
            throw new ConnectorParseException();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new ConnectorParseException(exception);
        }

        String code = root.path("code").asText("");
        if (NO_RECORD_CODE.equals(code)) {
            return new LibraryLoansResponse(0, List.of());
        }

        if (!root.path("success").asBoolean(false)) {
            if (NEED_LOGIN_CODE.equals(code)) {
                log.info("library loans upstream returned needLogin — token expired or invalid");
                throw new LibraryAuthRequiredException();
            }
            log.warn("library loans upstream returned success=false: code={}", code);
            throw new ConnectorParseException();
        }

        JsonNode data = root.path("data");
        int total = data.path("totalCount").asInt(0);
        JsonNode list = data.path("list");

        List<LibraryLoanItem> items = new ArrayList<>();
        if (list.isArray()) {
            for (JsonNode entry : list) {
                items.add(toLoanItem(entry));
            }
        }
        return new LibraryLoansResponse(total, items);
    }

    private static LibraryLoanItem toLoanItem(JsonNode entry) {
        long id = entry.path("id").asLong(0L);
        String title = textOr(entry.path("title"), "(제목 미상)");
        String author = textOr(entry.path("author"), null);
        String callNumber = textOr(entry.path("callNumber"), null);
        LocalDate loanDate = parseDate(entry.path("loanDate").asText(null));
        LocalDate dueDate  = parseDate(entry.path("returnDate").asText(null));
        boolean overdue    = entry.path("isOverdue").asBoolean(false);
        boolean renewable  = entry.path("isRenewable").asBoolean(false);
        return new LibraryLoanItem(id, title, author, callNumber, loanDate, dueDate, overdue, renewable);
    }

    private static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            // Pyxis dates: "2026-05-10" or "2026-05-10T00:00:00" — take first 10 chars
            return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String textOr(JsonNode node, String fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return fallback;
        }
        String text = node.asText("");
        return text.isBlank() ? fallback : text;
    }
}
