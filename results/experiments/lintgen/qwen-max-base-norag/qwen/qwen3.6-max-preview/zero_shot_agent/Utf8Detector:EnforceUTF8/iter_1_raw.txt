package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Pattern ENCODING_PATTERN = Pattern.compile(
            "<\\?xml[^>]+encoding\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);

    @Override
    public void visitDocument(XmlContext context, Document document) {
        CharSequence contents = context.getContents();
        if (contents == null || contents.length() == 0) {
            return;
        }

        Matcher matcher = ENCODING_PATTERN.matcher(contents);
        if (matcher.find()) {
            String encoding = matcher.group(1);
            if (!"UTF-8".equalsIgnoreCase(encoding)) {
                Location location = Location.create(context.file, contents, matcher.start(1), matcher.end(1));
                context.report(ISSUE, location,
                        "Encoding is not UTF-8; this can cause subtle bugs with non-ASCII characters " +
                        "because the Android Gradle build system assumes UTF-8.");
            }
        }
    }
}