package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.JdAnalysisResponse;
import com.resumade.api.workspace.service.WorkspaceAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class JdAnalysisService {

    private final WorkspaceAiService aiService;
    private final TesseractService tesseractService;
    private final Map<String, String> jdCache = new ConcurrentHashMap<>();
    private final Map<String, byte[]> imageCache = new ConcurrentHashMap<>();

    public String initAnalysis(String rawJd) {
        String uuid = UUID.randomUUID().toString();
        jdCache.put(uuid, rawJd);
        log.info("Initialized JD analysis for UUID: {}", uuid);
        return uuid;
    }

    public String initImageAnalysis(byte[] imageBytes) {
        String uuid = UUID.randomUUID().toString();
        imageCache.put(uuid, imageBytes);
        log.info("Initialized JD Image analysis for UUID: {}", uuid);
        return uuid;
    }

    public void processAnalysis(String uuid, SseEmitter emitter) {
        String rawJd = jdCache.remove(uuid);
        if (rawJd == null) {
            sendError(emitter, "Invalid or expired session");
            return;
        }

        try {
            sendEvent(emitter, "START", "분석을 시작합니다...");
            sendEvent(emitter, "ANALYZING", "공고 내용을 추출 중입니다...");
            
            JdAnalysisResponse response = aiService.analyzeJd(rawJd);
            
            sendEvent(emitter, "COMPLETE", response);
            emitter.complete();
            log.info("Completed JD analysis for UUID: {}", uuid);
        } catch (Exception e) {
            log.error("JD Analysis failed for UUID: {}: {}", uuid, e.getMessage(), e);
            sendError(emitter, "AI 분석 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    public void processImageAnalysis(String uuid, SseEmitter emitter) {
        byte[] imageBytes = imageCache.remove(uuid);
        if (imageBytes == null) {
            sendError(emitter, "Invalid or expired session");
            return;
        }

        try {
            sendEvent(emitter, "START", "이미지 분석을 시작합니다...");
            sendEvent(emitter, "ANALYZING", "전용 OCR 엔진으로 텍스트를 추출 중입니다...");

            // 1. Specialized OCR first (Hybrid Strategy)
            String ocrText = tesseractService.extractText(imageBytes);
            log.info("Specialized OCR extracted {} characters", ocrText.length());

            sendEvent(emitter, "ANALYZING", "AI가 추출된 텍스트와 이미지를 교차 검증 중입니다...");

            // 2. High-quality resize for Vision backup
            byte[] processedImage = resizeImageIfNeeded(imageBytes);
            String base64Image = java.util.Base64.getEncoder().encodeToString(processedImage);
            dev.langchain4j.data.message.ImageContent imageContent = dev.langchain4j.data.message.ImageContent.from(base64Image, "image/png");
            
            // 3. AI structuring with OCR text hint
            JdAnalysisResponse response = aiService.analyzeJdWithOcr(ocrText, imageContent);
            
            sendEvent(emitter, "COMPLETE", response);
            emitter.complete();
            log.info("Completed Hybrid JD Image analysis for UUID: {}", uuid);
        } catch (Exception e) {
            log.error("JD Image Analysis failed for UUID: {}: {}", uuid, e.getMessage(), e);
            sendError(emitter, "이미지 분석 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private byte[] resizeImageIfNeeded(byte[] imageBytes) {
        try {
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (originalImage == null) return imageBytes;

            int maxWidth = 2048;
            int maxHeight = 2048;
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            if (width <= maxWidth && height <= maxHeight) {
                log.info("Image is within limits ({}x{}). Skipping resize.", width, height);
                return imageBytes; // No need to resize
            }

            log.info("Resizing image from {}x{} to fit {}x{}", width, height, maxWidth, maxHeight);

            double ratio = Math.min((double) maxWidth / width, (double) maxHeight / height);
            int targetWidth = (int) (width * ratio);
            int targetHeight = (int) (height * ratio);

            BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = outputImage.createGraphics();
            
            // Set high quality rendering hints
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(outputImage, "png", baos);
            byte[] result = baos.toByteArray();
            log.info("Image resized successfully. Final size: {} bytes", result.length);
            return result;
        } catch (IOException e) {
            log.error("Failed to resize image, using original", e);
            return imageBytes;
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            log.warn("Failed to send SSE event: {}", e.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("ERROR").data(Map.of("message", message)));
            emitter.complete();
        } catch (IOException e) {
            log.warn("Failed to send error event: {}", e.getMessage());
        }
    }
}
