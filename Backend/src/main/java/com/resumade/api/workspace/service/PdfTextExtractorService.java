package com.resumade.api.workspace.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class PdfTextExtractorService {

    private static final int MIN_TEXT_LENGTH = 50;

    /**
     * PDF 바이트 배열에서 텍스트를 추출합니다.
     * 채용사이트에서 제공하는 디지털 PDF는 텍스트가 내장되어 있으므로
     * PDFTextStripper로 직접 추출합니다.
     *
     * @param pdfBytes PDF 파일 바이트 배열
     * @return 추출된 텍스트
     * @throws IllegalArgumentException 텍스트 추출에 실패하거나 텍스트가 없는 경우
     */
    public String extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            String extractedText = stripper.getText(document).trim();
            log.info("PDF text extraction completed: {} characters extracted from {} page(s)",
                    extractedText.length(), document.getNumberOfPages());

            if (extractedText.length() < MIN_TEXT_LENGTH) {
                throw new IllegalArgumentException(
                        "PDF에서 텍스트를 추출할 수 없습니다. 텍스트 기반 PDF 파일인지 확인해 주세요."
                );
            }

            return extractedText;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to extract text from PDF: {}", e.getMessage(), e);
            throw new IllegalArgumentException("PDF 파일을 읽을 수 없습니다. 손상된 파일이 아닌지 확인해 주세요.");
        }
    }
}
