public class RegistrationDetector extends Detector implements Detector.UastScanner {
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() { ... }
    @Override
    public UElementHandler createUastHandler(JavaContext context) { ... }
}