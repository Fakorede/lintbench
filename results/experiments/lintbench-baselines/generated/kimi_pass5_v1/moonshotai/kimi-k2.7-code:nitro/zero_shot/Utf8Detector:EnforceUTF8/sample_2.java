package com.android.tools.lint.checks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceFolderType;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlAttribute;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlDocument;
import com.android.tools.lint.detector.api.XmlTag;

public class Utf8Detector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION = new Implementation(
            Utf8Detector.class,
            Scope.RESOURCE_FILE_SCOPE
    );

    public static final Issue ENFORCE_UTF8 = Issue.create(
            "EnforceUTF8",
            "Encoding used in resource files is not UTF-8",
            "XML supports encoding in a wide variety of character sets. However, not all "
                    + "tools handle the XML encoding attribute correctly, and nearly all Android "
                    + "apps use UTF-8, so by using UTF-8 you can protect yourself against subtle "
                    + "bugs when using non-ASCII characters.\n\n"
                    + "In particular, the Android Gradle build system will merge resource XML files "
                    + "assuming the resource files are using UTF-8 encoding.",
            Category.I18N,
            6,
            Severity.ERROR,
            IMPLEMENTATION
    );

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull XmlDocument document) {
        XmlTag prolog = document.getProlog();
        if (prolog == null) {
            return;
        }

        XmlAttribute encoding = prolog.getAttribute("encoding", null);
        if (encoding == null) {
            return;
        }

        String value = encoding.getValue();
        if (value != null && !"UTF-8".equalsIgnoreCase(value)) {
            String message = String.format(
                    "The encoding \"%1$s\" is not UTF-8. Resource files must use UTF-8 encoding.",
                    value
            );
            Location location = context.getLocation(prolog);
            context.report(ENFORCE_UTF8, prolog, location, message);
        }
    }
}