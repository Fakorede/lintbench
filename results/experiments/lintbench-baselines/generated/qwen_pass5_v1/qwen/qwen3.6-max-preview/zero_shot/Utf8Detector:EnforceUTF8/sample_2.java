package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Document;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utf8Detector extends ResourceXmlDetector {

    private static final Pattern ENCODING_PATTERN = Pattern.compile(
            "encoding\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    public static final Issue ISSUE = Issue.create(
            "EnforceUTF8",
            "Encoding used in resource files is not UTF-8",
            "XML supports encoding in a wide variety of character sets. However, not all " +
            "tools handle the XML encoding attribute correctly, and nearly all Android " +
            "apps use UTF-8, so by using UTF-8 you can protect yourself against subtle " +
            "bugs when using non-ASCII characters.\n\n" +
            "In particular, the Android Gradle build system will merge resource XML files " +
            "assuming the resource files are using UTF-8 encoding.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        try {
            String contents = context.getContents();
            if (contents == null || contents.isEmpty()) {
                return;
            }

            int xmlDeclStart = contents.indexOf("<?xml");
            if (xmlDeclStart == -1) {
                return;
            }

            int xmlDeclEnd = contents.indexOf("?>", xmlDeclStart);
            if (xmlDeclEnd == -1) {
                return;
            }

            String declaration = contents.substring(xmlDeclStart, xmlDeclEnd + 2);
            Matcher matcher = ENCODING_PATTERN.matcher(declaration);
            if (matcher.find()) {
                String encoding = matcher.group(1);
                if (!"UTF-8".equalsIgnoreCase(encoding)) {
                    Location location = Location.create(context.file, contents, xmlDeclStart, xmlDeclEnd + 2);
                    context.report(ISSUE, location, "The file encoding should be UTF-8 (was " + encoding + ")");
                }
            }
        } catch (IOException e) {
            // Ignore I/O errors during content reading
        }
    }
}