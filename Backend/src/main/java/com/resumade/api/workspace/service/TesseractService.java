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
        
        // Use project-local tessdata directory
        String tessDataPath = "tessdata"; 
        tesseract.setDatapath(tessDataPath);
        tesseract.setLanguage("kor+eng");

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) return "";
            
            // Perform OCR
            String result = tesseract.doOCR(image);
            log.info("Tesseract OCR completed successfully.");
            return result;
        } catch (Throwable e) {
            // Use Throwable to catch Error (like Invalid memory access) as well as Exception
            log.warn("Tesseract OCR failed (possibly missing native libs or data): {}", e.getMessage());
            return "";
        }
    }
}
