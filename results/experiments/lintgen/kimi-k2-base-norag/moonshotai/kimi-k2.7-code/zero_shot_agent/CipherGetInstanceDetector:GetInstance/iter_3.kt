package com.android.tools.lint.checks;
...
public class CipherGetInstanceDetector extends Detector implements SourceCodeScanner {
    private static final String CIPHER = "javax.crypto.Cipher";
    public static final Issue ISSUE = Issue.create(...);
    @Override
    public List<String> getApplicableCallNames() { return Collections.singletonList("getInstance"); }
    @Override
    public void visitMethodCall(JavaContext context, UCallExpression node, PsiMethod method) {
        if (!context.getEvaluator().isMemberInClass(method, CIPHER)) return;
        List<UExpression> args = node.getValueArguments();
        if (args.size() != 1) return;
        UExpression arg = args.get(0);
        Object value = ConstantEvaluator.evaluate(context, arg);
        if (!(value instanceof String)) return;
        String transformation = (String) value;
        if (transformation.equals("AES") || transformation.startsWith("AES/ECB") || transformation.startsWith("DES/ECB")) {
            context.report(ISSUE, node, context.getLocation(node), "...");
        }
    }
}