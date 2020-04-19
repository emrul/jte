package org.jusecase.jte.internal;

import org.jusecase.jte.CodeResolver;

import java.util.LinkedHashSet;

public class TemplateCompiler {

    public static final String TAG_EXTENSION = ".jtag";
    public static final String LAYOUT_EXTENSION = ".jlayout";
    public static final String CLASS_SUFFIX = "JteGenerated";

    private final CodeResolver codeResolver;

    private final String templatePackageName;
    private final String tagPackageName;
    private final String layoutPackageName;
    private final boolean debug = false;

    public TemplateCompiler(CodeResolver codeResolver) {
        this(codeResolver, "org.jusecase.jte");

    }

    public TemplateCompiler(CodeResolver codeResolver, String packageName) {
        this.codeResolver = codeResolver;
        this.templatePackageName = packageName + ".templates";
        this.tagPackageName = packageName + ".tags";
        this.layoutPackageName = packageName + ".layouts";
    }


    public Template<?> compile(String name) {
        String templateCode = codeResolver.resolve(name);
        if (templateCode == null) {
            throw new RuntimeException("No code found for template " + name);
        }
        if (templateCode.isEmpty()) {
            return EmptyTemplate.INSTANCE;
        }

        ClassInfo templateInfo = new ClassInfo(name, templatePackageName);

        TemplateParameterParser attributeParser = new TemplateParameterParser();
        attributeParser.parse(templateCode);

        StringBuilder javaCode = new StringBuilder("package " + templateInfo.packageName + ";\n");
        for (String importClass : attributeParser.importClasses) {
            javaCode.append("import ").append(importClass).append(";\n");
        }

        javaCode.append("public final class ").append(templateInfo.className).append(" implements org.jusecase.jte.internal.Template<").append(attributeParser.className).append("> {\n");
        javaCode.append("\tpublic void render(").append(attributeParser.className).append(" ").append(attributeParser.instanceName).append(", org.jusecase.jte.TemplateOutput output) {\n");

        LinkedHashSet<ClassDefinition> classDefinitions = new LinkedHashSet<>();
        new TemplateParser(TemplateType.Template).parse(attributeParser.lastIndex, templateCode, new CodeGenerator(TemplateType.Template, javaCode, classDefinitions));
        javaCode.append("\t}\n");
        javaCode.append("}\n");

        ClassDefinition templateDefinition = new ClassDefinition(templateInfo.fullName);
        templateDefinition.setCode(javaCode.toString());
        classDefinitions.add(templateDefinition);

        if (debug) {
            System.out.println(templateDefinition.getCode());
        }

        try {
            ClassCompiler classCompiler = new ClassCompiler();
            return (Template<?>) classCompiler.compile(templateDefinition.getName(), classDefinitions).getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void compileTag(String name, LinkedHashSet<ClassDefinition> classDefinitions) {
        ClassInfo tagInfo = new ClassInfo(name, tagPackageName);

        ClassDefinition classDefinition = new ClassDefinition(tagInfo.fullName);
        if (classDefinitions.contains(classDefinition)) {
            return;
        }

        String tagCode = codeResolver.resolve(name);
        if (tagCode == null) {
            throw new RuntimeException("No code found for tag " + name);
        }

        classDefinitions.add(classDefinition);

        TagOrLayoutParameterParser parameterParser = new TagOrLayoutParameterParser();
        int lastIndex = parameterParser.parse(tagCode);

        StringBuilder javaCode = new StringBuilder("package " + tagInfo.packageName + ";\n");
        for (String importClass : parameterParser.importClasses) {
            javaCode.append("import ").append(importClass).append(";\n");
        }
        javaCode.append("public final class ").append(tagInfo.className).append(" {\n");
        javaCode.append("\tpublic static void render(org.jusecase.jte.TemplateOutput output");
        for (String parameter : parameterParser.parameters) {
            javaCode.append(", ").append(parameter);
        }
        javaCode.append(") {\n");

        new TemplateParser(TemplateType.Tag).parse(lastIndex, tagCode, new CodeGenerator(TemplateType.Tag, javaCode, classDefinitions));

        javaCode.append("\t}\n");
        javaCode.append("}\n");

        classDefinition.setCode(javaCode.toString());

        if (debug) {
            System.out.println(classDefinition.getCode());
        }
    }

    private void compileLayout(String name, LinkedHashSet<ClassDefinition> classDefinitions) {
        ClassInfo layoutInfo = new ClassInfo(name, layoutPackageName);

        ClassDefinition classDefinition = new ClassDefinition(layoutInfo.fullName);
        if (classDefinitions.contains(classDefinition)) {
            return;
        }

        String layoutCode = codeResolver.resolve(name);
        if (layoutCode == null) {
            throw new RuntimeException("No code found for layout " + name);
        }

        classDefinitions.add(classDefinition);

        TagOrLayoutParameterParser parameterParser = new TagOrLayoutParameterParser();
        int lastIndex = parameterParser.parse(layoutCode);

        StringBuilder javaCode = new StringBuilder("package " + layoutInfo.packageName + ";\n");
        for (String importClass : parameterParser.importClasses) {
            javaCode.append("import ").append(importClass).append(";\n");
        }
        javaCode.append("public final class ").append(layoutInfo.className).append(" {\n");
        javaCode.append("\tpublic static void render(org.jusecase.jte.TemplateOutput output");
        for (String parameter : parameterParser.parameters) {
            javaCode.append(", ").append(parameter);
        }
        javaCode.append(", java.util.function.Function<String, Runnable> jteLayoutSectionLookup");
        javaCode.append(") {\n");

        new TemplateParser(TemplateType.Layout).parse(lastIndex, layoutCode, new CodeGenerator(TemplateType.Layout, javaCode, classDefinitions));

        javaCode.append("\t}\n");
        javaCode.append("}\n");

        classDefinition.setCode(javaCode.toString());

        if (debug) {
            System.out.println(classDefinition.getCode());
        }
    }


    private class CodeGenerator implements TemplateParserVisitor {
        private final TemplateType type;
        private final LinkedHashSet<ClassDefinition> classDefinitions;
        private final StringBuilder javaCode;

        private CodeGenerator(TemplateType type, StringBuilder javaCode, LinkedHashSet<ClassDefinition> classDefinitions) {
            this.type = type;
            this.javaCode = javaCode;
            this.classDefinitions = classDefinitions;
        }

        @Override
        public void onTextPart(int depth, String textPart) {
            if (textPart.isEmpty()) {
                return;
            }

            writeIndentation(depth);
            javaCode.append("output.write(\"");
            appendEscaped(textPart);
            javaCode.append("\");\n");
        }

        @Override
        public void onCodePart(int depth, String codePart) {
            writeIndentation(depth);
            javaCode.append("output.write(").append(codePart).append(");\n");
        }

        @Override
        public void onCodeStatement(int depth, String codePart) {
            writeIndentation(depth);
            javaCode.append(codePart).append(";\n");
        }

        @Override
        public void onConditionStart(int depth, String condition) {
            writeIndentation(depth);
            javaCode.append("if (").append(condition).append(") {\n");
        }

        @Override
        public void onConditionElse(int depth, String condition) {
            writeIndentation(depth);
            javaCode.append("} else if (").append(condition).append(") {\n");
        }

        @Override
        public void onConditionElse(int depth) {
            writeIndentation(depth);
            javaCode.append("} else {\n");
        }

        @Override
        public void onConditionEnd(int depth) {
            writeIndentation(depth);
            javaCode.append("}\n");
        }

        @Override
        public void onForLoopStart(int depth, String codePart) {
            writeIndentation(depth);
            javaCode.append("for (").append(codePart).append(") {\n");
        }

        @Override
        public void onForLoopEnd(int depth) {
            writeIndentation(depth);
            javaCode.append("}\n");
        }

        @Override
        public void onTag(int depth, String name, String params) {
            compileTag(name.replace('.', '/') + TAG_EXTENSION, classDefinitions);

            writeIndentation(depth);
            javaCode.append(tagPackageName).append('.').append(name).append(CLASS_SUFFIX).append(".render(output");

            if (!params.isBlank()) {
                javaCode.append(", ").append(params);
            }
            javaCode.append(");\n");
        }

        @Override
        public void onLayout(int depth, String name, String params) {
            compileLayout(name.replace('.', '/') + LAYOUT_EXTENSION, classDefinitions);

            writeIndentation(depth);
            javaCode.append(layoutPackageName).append('.').append(name).append(CLASS_SUFFIX).append(".render(output");

            if (!params.isBlank()) {
                javaCode.append(", ").append(params);
            }

            javaCode.append(", new java.util.function.Function<String, Runnable>() {\n");
            writeIndentation(depth + 1);
            javaCode.append("public Runnable apply(String jteLayoutSection) {\n");
        }

        @Override
        public void onLayoutSection(int depth, String name) {
            if (type == TemplateType.Layout) {
                writeIndentation(depth);
                javaCode.append("jteLayoutSectionLookup.apply(\"").append(name.trim()).append("\").run();\n");
            } else {
                writeIndentation(depth + 2);
                javaCode.append("if (\"").append(name.trim()).append("\".equals(jteLayoutSection)) {\n");
                writeIndentation(depth + 3);
                javaCode.append("return new Runnable() {\n");
                writeIndentation(depth + 4);
                javaCode.append("public void run() {\n");
            }
        }

        @Override
        public void onLayoutSectionEnd(int depth) {
            writeIndentation(depth + 4);
            javaCode.append("}\n");
            writeIndentation(depth + 3);
            javaCode.append("};\n");
            writeIndentation(depth + 2);
            javaCode.append("}\n");
        }

        @Override
        public void onLayoutEnd(int depth) {
            writeIndentation(depth + 2);
            javaCode.append("return null;\n");
            writeIndentation(depth + 1);
            javaCode.append("}\n");
            writeIndentation(depth);
            javaCode.append("});\n");
        }

        @SuppressWarnings("StringRepeatCanBeUsed")
        private void writeIndentation(int depth) {
            for (int i = 0; i < depth + 2; ++i) {
                javaCode.append('\t');
            }
        }

        private void appendEscaped(String text) {
            for (int i = 0; i < text.length(); ++i) {
                char c = text.charAt(i);
                if (c == '\"') {
                    javaCode.append("\\\"");
                } else if (c == '\n') {
                    javaCode.append("\\n");
                } else if (c == '\t') {
                    javaCode.append("\\t");
                } else if (c == '\r') {
                    javaCode.append("\\r");
                } else if (c == '\f') {
                    javaCode.append("\\f");
                } else if (c == '\b') {
                    javaCode.append("\\b");
                } else {
                    javaCode.append(c);
                }
            }
        }
    }

    private static final class ClassInfo {
        final String className;
        final String packageName;
        final String fullName;

        ClassInfo(String name, String parentPackage) {
            int endIndex = name.lastIndexOf('.');
            if (endIndex == -1) {
                endIndex = name.length();
            }

            int startIndex = name.lastIndexOf('/');
            if (startIndex == -1) {
                startIndex = 0;
            } else {
                startIndex += 1;
            }

            className = name.substring(startIndex, endIndex) + CLASS_SUFFIX;
            if (startIndex == 0) {
                packageName = parentPackage;
            } else {
                packageName = parentPackage + "." + name.substring(0, startIndex - 1).replace('/', '.');
            }
            fullName = packageName + "." + className;
        }


    }
}
