package com.springlab.lab.code;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

@Service
public class CodeReferenceService {

    private final List<CodeReference> references;

    public CodeReferenceService(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("lab/code-references.json").getInputStream()) {
            this.references = List.copyOf(objectMapper.readValue(in, new TypeReference<List<CodeReference>>() { }));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load lab/code-references.json", e);
        }
    }

    public List<CodeReference> all() {
        return references;
    }
}
