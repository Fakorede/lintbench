package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;
import static com.android.SdkConstants.WEB_VIEW;

public class WebViewDetector extends LayoutDetector {

    public static final Issue ISSUE =
            Issue.create(
                    "WebViewLayout",
                    "WebView in wrap_content parent",
                    "The WebView implementation has certain performance optimizations which will not "
                            + "work correctly if the parent view is using wrap_content rather than "
                            + "match_parent. This can lead to subtle UI bugs.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            WebViewDetector.class,
                            Scope.RESOURCE_FILE_SCOPE));

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(WEB_VIEW);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String width = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
        String height = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (VALUE_WRAP_CONTENT.equals(width) || VALUE_WRAP_CONTENT.equals(height)) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "WebView should not use wrap_content for layout dimensions as it can lead to "
                            + "performance issues and subtle UI bugs");
        }
    }
}