package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;

public class Utf8Detector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "EnforceUTF8",
            "Encoding used in resource files is not UTF-8",
            "XML supports encoding in a wide variety of character sets. However, not all " +
            "tools handle the XML encoding attribute correctly, and nearly all Android " +
            "apps use UTF-8, so by using UTF-8 you can protect yourself against subtle " +
            "bugs when using non-ASCII characters.\n\n" +
            "In particular, the Android Gradle build system will merge resource XML files " +
            "assuming the resource files are using UTF-8 encoding.",
            Category.MESSAGES,
            5,
            Severity.WARNING,
            new Implementation(
                    Utf8Detector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    private static final Pattern ENCODING_PATTERN = Pattern.compile(
            "<\\?xml\\s+[^>]*encoding\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        String encoding = null;
        try {
            encoding = document.getXmlEncoding();
        } catch (Throwable t) {
            // Ignore and fall back to manual parsing
        }

        if (encoding == null) {
            CharSequence contents = context.getContents();
            if (contents != null) {
                String head = contents.subSequence(0, Math.min(contents.length(), 200)).toString();
                Matcher matcher = ENCODING_PATTERN.matcher(head);
                if (matcher.find()) {
                    encoding = matcher.group(1);
                }
            }
        }

        if (encoding != null && !isUtf8(encoding)) {
            Location location = findEncodingLocation(context, encoding);
            if (location == null) {
                location = context.getLocation(document);
            }
            context.report(
                    ISSUE,
                    location,
                    "Encoding used in resource files is not UTF-8"
            );
        }
    }

    private boolean isUtf8(String encoding) {
        return "utf-8".equalsIgnoreCase(encoding) || "utf8".equalsIgnoreCase(encoding);
    }

    private Location findEncodingLocation(XmlContext context, String encoding) {
        CharSequence contents = context.getContents();
        if (contents == null) {
            return null;
        }
        int index = contents.toString().indexOf(encoding);
        if (index != -1) {
            return Location.create(context.file, contents, index, index + encoding.length());
        }
        return null;
    }
}