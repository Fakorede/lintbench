package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.XmlScannerConstants;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typo in manifest tag",
                    "This check looks through the manifest, and if it finds any tags that look like likely misspellings of standard Android manifest tags, they are flagged.",
                    Category.CORRECTNESS,
                    5,
                    Severity.WARNING,
                    new Implementation(ManifestTypoDetector.class, EnumSet.of(Scope.MANIFEST)));

    private static final String[] VALID_TAGS =
            new String[] {
                "action",
                "activity",
                "activity-alias",
                "application",
                "attribution",
                "category",
                "compatible-screens",
                "data",
                "grant-uri-permission",
                "instrumentation",
                "intent-filter",
                "layout",
                "manifest",
                "meta-data",
                "path-permission",
                "permission",
                "permission-group",
                "permission-tree",
                "profileable",
                "property",
                "provider",
                "queries",
                "receiver",
                "service",
                "supports-gl-texture",
                "supports-screens",
                "uses-configuration",
                "uses-feature",
                "uses-library",
                "uses-native-library",
                "uses-permission",
                "uses-permission-sdk-23",
                "uses-sdk"
            };

    private static final Set<String> VALID_TAG_SET = new HashSet<>(VALID_TAGS.length);

    static {
        for (String tag : VALID_TAGS) {
            VALID_TAG_SET.add(tag.toLowerCase(Locale.US));
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String tag = element.getTagName();
        if (tag == null || tag.isEmpty() || tag.indexOf(':') != -1) {
            return;
        }

        String lowerTag = tag.toLowerCase(Locale.US);
        if (VALID_TAG_SET.contains(lowerTag)) {
            return;
        }

        for (String valid : VALID_TAGS) {
            if (looksLikeTypo(lowerTag, valid)) {
                String message =
                        String.format(
                                "Possible typo: '%1$s' is not a standard Android manifest tag; did you mean '%2$s'?",
                                tag, valid);
                context.report(ISSUE, element, context.getNameLocation(element), message);
                return;
            }
        }
    }

    private static boolean looksLikeTypo(String tag, String valid) {
        if (tag.length() <= 3) {
            return false;
        }

        String validLower = valid.toLowerCase(Locale.US);
        if (tag.equals(validLower)) {
            return false;
        }

        int lengthDiff = Math.abs(tag.length() - validLower.length());
        if (lengthDiff > 1) {
            return false;
        }

        if (tag.length() == validLower.length()) {
            int mismatches = 0;
            int mismatchIndex = -1;
            for (int i = 0; i < tag.length(); i++) {
                if (tag.charAt(i) != validLower.charAt(i)) {
                    mismatches++;
                    if (mismatches > 2) {
                        return false;
                    }
                    mismatchIndex = i;
                }
            }

            if (mismatches == 1) {
                return true;
            }

            if (mismatches == 2 && mismatchIndex > 0) {
                int prev = mismatchIndex - 1;
                return tag.charAt(prev) == validLower.charAt(mismatchIndex)
                        && tag.charAt(mismatchIndex) == validLower.charAt(prev);
            }

            return false;
        }

        String longer = tag.length() > validLower.length() ? tag : validLower;
        String shorter = tag.length() > validLower.length() ? validLower : tag;

        int i = 0;
        int j = 0;
        boolean foundDifference = false;
        while (i < shorter.length() && j < longer.length()) {
            if (shorter.charAt(i) == longer.charAt(j)) {
                i++;
                j++;
            } else if (foundDifference) {
                return false;
            } else {
                foundDifference = true;
                j++;
            }
        }

        return true;
    }
}