package com.example.atelier.document.extract;

import com.example.atelier.document.model.DocumentModel;

import java.io.InputStream;

public interface DocumentExtractor {

    boolean supports(String mimeType, String fileName);

    DocumentModel extract(InputStream input, ExtractContext context) throws Exception;
}
