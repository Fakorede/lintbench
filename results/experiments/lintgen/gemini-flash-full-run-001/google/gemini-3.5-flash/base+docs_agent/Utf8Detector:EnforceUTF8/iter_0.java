package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Document;

public class Utf8Detector extends Detector implements XmlScanner {

    private static final Pattern ENCODING_PATTERN = Pattern.compile(
            "<\\?xml\\s+[^>]*encoding\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    public static final Issue ISSUE = Issue.create(
            "EnforceUTF8",
            "Encoding used in resource files is not UTF-8",
            "XML supports encoding in a wide variety of character sets. " +
            "However, not all tools handle the XML encoding attribute correctly, " +
            "and nearly all Android apps use UTF-8, so by using UTF-8 you can " +
            "protect yourself against subtle bugs when using non-ASCII characters.\n\n" +
            "In particular, the Android Gradle build system will merge resource XML files " +
            "assuming the resource files are using UTF-8 encoding.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    Utf8Detector.class,
                    Scope.RESOURCE_FILE_SCOPE
            )
    );

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        CharSequence contents = context.getContents();
        if (contents == null) {
            return;
        }

        int limit = Math.min(contents.length(), 1024);
        CharSequence head = contents.subSequence(0, limit);
        Matcher matcher = ENCODING_PATTERN.matcher(head);
        if (matcher.find()) {
            String encoding = matcher.group(1);
            String canonical = encoding.trim().toUpperCase(Locale.US);
            if (!canonical.equals("UTF-8") && !canonical.equals("UTF8")) {
                int start = matcher.start(1);
                int end = matcher.end(1);
                Location location = context.getLocation(context.file, contents, start, end);
                context.report(
                        ISSUE,
                        location,
                        "Encoding used in resource files is not UTF-8 (" + encoding + ")"
                );
            }
        }
    }
}