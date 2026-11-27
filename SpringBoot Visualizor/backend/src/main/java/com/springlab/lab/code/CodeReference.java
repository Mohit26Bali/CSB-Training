package com.springlab.lab.code;

import java.util.List;

/**
 * An excerpt of a real SpringLab source file. {@code excerpt} must equal lines {@code startLine..endLine} of
 * {@code file} (relative to the backend module); CodeReferenceTest fails the build when the source drifts.
 * Each note points at a line and the token on it that the note explains.
 */
public record CodeReference(String id, String title, String file, int startLine, int endLine, String language,
                            String excerpt, List<Note> notes) {

    public record Note(int line, String token, String text) {
    }
}
