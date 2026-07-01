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

    private static final Pattern ENCODING_PATTERN = Pattern.compile(
            "<\\?xml\\s+[^>]*encoding\\s*=\\s*[\"']([^\"']+)[\"']", 
            Pattern.CASE_INSENSITIVE
    );

    public static final Issue ISSUE = Issue.create(
            "EnforceUTF8",
            "Encoding used in resource files is not UTF-8",
            "XML supports encoding in a wide variety of character sets. However, not all "
                    + "tools handle the XML encoding attribute correctly, and nearly all Android "
                    + "apps use UTF-8, so by using UTF-8 you can protect yourself against subtle "
                    + "bugs when using non-ASCII characters.\n\n"
                    + "In particular, the Android Gradle build system will merge resource XML files "
                    + "assuming the resource files are using UTF-8 encoding.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    Utf8Detector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    public Utf8Detector() {
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        CharSequence contents = context.getContents();
        if (contents == null) {
            return;
        }

        // The XML declaration must be at the very beginning of the file.
        int limit = Math.min(contents.length(), 1024);
        String head = contents.subSequence(0, limit).toString();
        Matcher matcher = ENCODING_PATTERN.matcher(head);
        if (matcher.find()) {
            String encoding = matcher.group(1);
            if (!encoding.equalsIgnoreCase("utf-8") && !encoding.equalsIgnoreCase("utf8")) {
                Location location = Location.create(context.file, contents, matcher.start(1), matcher.end(1));
                context.report(
                        ISSUE,
                        location,
                        String.format("Encoding `%s` is not UTF-8", encoding)
                );
            }
        }
    }
}