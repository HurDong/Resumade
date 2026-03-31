package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.workspace.dto.PnuApiResponse;
import com.resumade.api.workspace.dto.SpellCheckResponse;
import com.resumade.api.workspace.dto.SpellCorrection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 부산대학교 한국어 맞춤법 검사기 HTTP 클라이언트.
 *
 * <p>API 1회 호출당 500자 제한이 있으므로, 입력 텍스트를 문장 단위로
 * 500자 이하 청크로 분할하여 순차 호출 후 결과를 병합한다.</p>
 *
 * <p>동일 errorWord가 여러 청크에 걸쳐 중복될 경우 첫 번째 등장만 유지한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PnuSpellCheckClient {

    private static final String PNU_URL = "http://speller.cs.pusan.ac.kr/results";
    private static final int CHUNK_SIZE = 500;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * 텍스트의 맞춤법·띄어쓰기 오류를 검사한다.
     *
     * @param text 검사 대상 원문 (길이 제한 없음, 내부에서 청크 분할)
     * @return 교정 제안 목록
     */
    public SpellCheckResponse check(String text) {
        List<String> chunks = splitIntoChunks(text);
        log.info("[PNU] chunks={}", chunks.size());

        // errorWord 기준 중복 제거 (LinkedHashMap → 순서 보존)
        Map<String, SpellCorrection> merged = new LinkedHashMap<>();

        for (String chunk : chunks) {
            List<SpellCorrection> corrections = callApi(chunk);
            for (SpellCorrection c : corrections) {
                merged.putIfAbsent(c.getErrorWord(), c);
            }
        }

        SpellCheckResponse response = new SpellCheckResponse();
        response.setCorrections(new ArrayList<>(merged.values()));
        return response;
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private List<SpellCorrection> callApi(String chunk) {
        try {
            String body = "text1=" + URLEncoder.encode(chunk, StandardCharsets.UTF_8)
                    + "&utf8=1&client_id=0";

            String raw = restClient.post()
                    .uri(PNU_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.debug("[PNU] raw={}", raw);

            if (raw == null || raw.isBlank()) {
                return List.of();
            }

            PnuApiResponse apiResponse = objectMapper.readValue(raw, PnuApiResponse.class);

            if (apiResponse == null || apiResponse.getErrInfo() == null) {
                return List.of();
            }

            return apiResponse.getErrInfo().stream()
                    .filter(e -> e.getOrgStr() != null && !e.getOrgStr().isBlank())
                    .map(this::toCorrection)
                    .toList();

        } catch (Exception e) {
            log.warn("[PNU] API call failed for chunk: {}", e.getMessage());
            return List.of();
        }
    }

    private SpellCorrection toCorrection(PnuApiResponse.ErrInfo info) {
        SpellCorrection c = new SpellCorrection();
        c.setErrorWord(info.getOrgStr());

        // candWord가 "|" 구분자로 여러 후보를 담을 수 있음 → 첫 번째만 사용
        String cand = info.getCandWord();
        if (cand != null && cand.contains("|")) {
            cand = cand.split("\\|")[0].strip();
        }
        c.setSuggestedWord(cand != null ? cand.strip() : "");
        c.setReason(mapTypeToReason(info.getType()));
        return c;
    }

    private String mapTypeToReason(int type) {
        return switch (type) {
            case 1 -> "맞춤법 오류";
            case 2 -> "띄어쓰기 오류";
            case 3 -> "표준어 의심";
            case 4 -> "통계적 교정";
            default -> "맞춤법 오류";
        };
    }

    /**
     * 텍스트를 문장 경계(. ? ! \n) 기준으로 CHUNK_SIZE 이하 청크로 분할한다.
     * 단일 문장이 CHUNK_SIZE를 초과하면 강제로 잘라낸다.
     */
    private List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        // 문장 구분자 뒤의 공백 포함해서 분리
        String[] sentences = text.split("(?<=[.?!\n])\\s*");

        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (sentence.isBlank()) continue;

            // 단일 문장이 CHUNK_SIZE 초과면 강제 분할
            if (sentence.length() > CHUNK_SIZE) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                for (int i = 0; i < sentence.length(); i += CHUNK_SIZE) {
                    chunks.add(sentence.substring(i, Math.min(i + CHUNK_SIZE, sentence.length())));
                }
                continue;
            }

            if (current.length() + sentence.length() > CHUNK_SIZE) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            current.append(sentence).append(" ");
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString().strip());
        }

        return chunks.isEmpty() ? List.of(text) : chunks;
    }
}
