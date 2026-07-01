package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.XmlScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags "
                            + "that look like likely misspellings, they are flagged. "
                            + "Misspelled tags are ignored by the parser and can cause "
                            + "the affected component or declaration to be omitted.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private static final Set<String> KNOWN_TAGS =
            new HashSet<>(
                    Arrays.asList(
                            "manifest",
                            "application",
                            "activity",
                            "activity-alias",
                            "service",
                            "receiver",
                            "provider",
                            "uses-permission",
                            "uses-permission-sdk-23",
                            "uses-sdk",
                            "uses-feature",
                            "uses-library",
                            "uses-native-library",
                            "uses-split",
                            "uses-gl-texture",
                            "instrumentation",
                            "supports-screens",
                            "compatible-screens",
                            "permission",
                            "permission-group",
                            "permission-tree",
                            "intent-filter",
                            "intent",
                            "action",
                            "category",
                            "data",
                            "meta-data",
                            "grant-uri-permission",
                            "path-permission",
                            "profileable",
                            "queries",
                            "package",
                            "property",
                            "eat-comment",
                            "preferred"));

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String tag = element.getTagName();
        if (tag == null || tag.isEmpty()) {
            return;
        }

        String normalized = tag.toLowerCase();
        if (KNOWN_TAGS.contains(normalized)) {
            return;
        }

        List<String> suggestions = findSuggestions(normalized);
        if (suggestions.isEmpty()) {
            return;
        }

        String message = buildMessage(tag, suggestions);
        context.report(ISSUE, element, context.getLocation(element), message);
    }

    private static List<String> findSuggestions(String tag) {
        List<String> suggestions = new ArrayList<>();
        int threshold = tag.length() < 5 ? 1 : 2;

        for (String known : KNOWN_TAGS) {
            int distance = levenshtein(tag, known);
            if (distance > 0 && distance <= threshold) {
                suggestions.add(known);
            }
        }

        Collections.sort(
                suggestions,
                (a, b) -> {
                    int distA = levenshtein(tag, a);
                    int distB = levenshtein(tag, b);
                    if (distA != distB) {
                        return distA - distB;
                    }
                    return a.compareTo(b);
                });

        return suggestions;
    }

    private static String buildMessage(String tag, List<String> suggestions) {
        StringBuilder sb = new StringBuilder();
        sb.append("Possible typo: `<").append(tag).append(">` is not a standard Android manifest tag");
        if (suggestions.size() == 1) {
            sb.append("; did you mean `<").append(suggestions.get(0)).append(">`?");
        } else {
            sb.append("; did you mean one of ");
            for (int i = 0; i < suggestions.size(); i++) {
                if (i > 0) {
                    sb.append(i == suggestions.size() - 1 ? " or " : ", ");
                }
                sb.append("<").append(suggestions.get(i)).append(">");
            }
            sb.append("?");
        }
        return sb.toString();
    }

    private static int levenshtein(String s, String t) {
        int m = s.length();
        int n = t.length();

        if (m == 0) {
            return n;
        }
        if (n == 0) {
            return m;
        }

        int[] previous = new int[n + 1];
        int[] current = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            current[0] = i;
            char sc = s.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = sc == t.charAt(j - 1) ? 0 : 1;
                current[j] =
                        Math.min(
                                Math.min(current[j - 1] + 1, previous[j] + 1),
                                previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[n];
    }
}