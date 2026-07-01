package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utf8Detector extends ResourceXmlDetector {

    public static final Issue ISSUE = Issue.create(
            "EnforceUTF8",
            "Encoding should be UTF-8",
            "XML supports encoding in a wide variety of character sets. However, not all tools handle the XML encoding attribute correctly, and nearly all Android apps use UTF-8, so by using UTF-8 you can protect yourself against subtle bugs when using non-ASCII characters.\n\nIn particular, the Android Gradle build system will merge resource XML files assuming the resource files are using UTF-8 encoding.",
            Category.I18N,
            5,
            Severity.WARNING,
            new Implementation(Utf8Detector.class, Scope.RESOURCE_FILE_SCOPE));

    private static final Pattern ENCODING_PATTERN =
            Pattern.compile("<\\?xml[^?]*?\\s+encoding\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }
        XmlContext xmlContext = (XmlContext) context;
        CharSequence contents = xmlContext.getContents();
        if (contents == null) {
            return;
        }

        Matcher matcher = ENCODING_PATTERN.matcher(contents);
        if (matcher.find()) {
            String encoding = matcher.group(1);
            if (encoding != null && !"UTF-8".equalsIgnoreCase(encoding)) {
                Location location = Location.create(
                        xmlContext.file, contents.toString(), matcher.start(1), matcher.end(1));
                xmlContext.report(ISSUE, location,
                        "Resource files should use UTF-8 encoding, not " + encoding);
            }
        }
    }
}