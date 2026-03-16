package com.resumade.api.workspace.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Slf4j
@Service
public class TesseractService {

    public String extractText(byte[] imageBytes) {
        Tesseract tesseract = new Tesseract();
        // You might need to set the datapath if not in default location
        // tesseract.setDatapath("/usr/share/tesseract-ocr/4.00/tessdata"); 
        tesseract.setLanguage("kor+eng"); // Support both Korean and English

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            String result = tesseract.doOCR(image);
            log.info("Tesseract OCR completed. Extracted length: {}", result.length());
            return result;
        } catch (TesseractException | IOException e) {
            log.error("Tesseract OCR failed: {}", e.getMessage());
            return "";
        }
    }
}
