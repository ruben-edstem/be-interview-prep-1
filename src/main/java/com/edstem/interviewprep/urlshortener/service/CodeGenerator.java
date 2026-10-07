package com.edstem.interviewprep.urlshortener.service;

import com.edstem.interviewprep.urlshortener.entity.ShortUrl;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class CodeGenerator {

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] code = new char[ShortUrl.CODE_LENGTH];
        for (int i = 0; i < code.length; i++) {
            code[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }
}
