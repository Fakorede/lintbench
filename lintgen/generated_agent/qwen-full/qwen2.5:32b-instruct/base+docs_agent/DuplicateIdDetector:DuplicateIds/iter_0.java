package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LayoutDetector;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.Pair;
import com.intellij.psi.PsiElement;

import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Map;

public class DuplicateIdDetector extends LayoutDetector {
    public static final Issue ISSUE = Issue.create(
            "DuplicateIds",
            "Duplicate ids within a single layout can cause unexpected behavior with `findViewById()`.",
            "Within a layout, id's should be unique since otherwise `findViewById()` can return an unexpected view.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(DuplicateIdDetector.class, true));

    @NonNull
    @Override
    public String[] getXmlTagNames() {
        return new String[]{SdkConstants.ANDROID_URI, SdkConstants.AUTO_URI};
    }

    private final UElementHandler mHandler = new UElementHandler() {
        @Override
        public void visitElement(@NonNull UElement element) {
            if (element instanceof USimpleNameReferenceExpression) {
                checkForDuplicateIds(element);
            }
        }
    };

    @Override
    public void visitLayout(@NonNull XmlContext context, @NonNull Element root) {
        Map<String, Pair<Location, PsiElement>> idMap = new HashMap<>();
        for (Element child : getChildren(root)) {
            String idValue = getAttributeValue(child, "android:id");
            if (idValue != null && !idValue.isEmpty()) {
                Location location = context.getLocation(child);
                PsiElement psiElement = context.getPsiForLocation(location);

                if (idMap.containsKey(idValue)) {
                    Pair<Location, PsiElement> existingPair = idMap.get(idValue);
                    context.report(ISSUE, child, location,
                            "Duplicate id found: %s", idValue)
                            .secondary(existingPair.getFirst(), existingPair.getSecond(),
                                    "First occurrence of the same id");
                } else {
                    idMap.put(idValue, new Pair<>(location, psiElement));
                }
            }
        }
    }

    private void checkForDuplicateIds(@NonNull UElement element) {
        // No-op for now
    }

    @Override
    public List<String> getApplicableAttributes() {
        return List.of("android:id");
    }
}