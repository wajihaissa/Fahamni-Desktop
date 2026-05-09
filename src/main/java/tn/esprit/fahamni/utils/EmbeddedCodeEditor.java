package tn.esprit.fahamni.utils;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class EmbeddedCodeEditor extends StackPane {

    private static final String CODEMIRROR_VERSION = "5.65.20";
    private static final Map<String, String> LANGUAGE_MODES = Map.ofEntries(
            Map.entry("java", "text/x-java"),
            Map.entry("javascript", "text/javascript"),
            Map.entry("typescript", "text/typescript"),
            Map.entry("python", "text/x-python"),
            Map.entry("cpp", "text/x-c++src"),
            Map.entry("c", "text/x-csrc"),
            Map.entry("sql", "text/x-sql"),
            Map.entry("html", "text/html"),
            Map.entry("css", "text/css"),
            Map.entry("php", "application/x-httpd-php")
    );
    private static final String EDITOR_HTML = buildEditorHtml();

    private final WebView webView = new WebView();
    private final WebEngine engine = webView.getEngine();
    private String text = "";
    private String language = "java";
    private boolean readOnly;
    private boolean loaded;
    private Runnable changeListener;

    public EmbeddedCodeEditor() {
        getStyleClass().add("quiz-code-editor-shell");

        webView.setContextMenuEnabled(false);
        webView.setMinHeight(280);
        webView.setPrefHeight(420);
        webView.setMaxHeight(Double.MAX_VALUE);
        webView.setMinWidth(320);

        getChildren().add(webView);

        engine.setJavaScriptEnabled(true);
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                loaded = true;
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("javaBridge", new JavaBridge());
                applyStateToPage();
            }
        });
        engine.loadContent(EDITOR_HTML);
    }

    public void setEditorLanguage(String language) {
        this.language = normalizeLanguage(language);
        applyStateToPage();
    }

    public String getEditorLanguage() {
        return language;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        applyStateToPage();
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
        applyStateToPage();
    }

    public String getText() {
        if (loaded) {
            Object currentValue = engine.executeScript("window.getEditorText()");
            text = currentValue == null ? "" : currentValue.toString();
        }
        return text;
    }

    public void focusEditor() {
        if (!loaded) {
            return;
        }
        engine.executeScript("window.focusEditor()");
    }

    public void setOnContentChanged(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    private void applyStateToPage() {
        if (!loaded) {
            return;
        }

        String escapedText = escapeForJavaScript(text);
        String escapedMode = escapeForJavaScript(resolveCodeMirrorMode(language));
        engine.executeScript("window.setEditorState('" + escapedText + "', '" + escapedMode + "', " + readOnly + ")");
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "java";
        }

        String normalized = language.trim().toLowerCase();
        return switch (normalized) {
            case "c++", "cplusplus" -> "cpp";
            case "c#" -> "java";
            case "js", "node", "nodejs" -> "javascript";
            case "ts" -> "typescript";
            case "py" -> "python";
            default -> normalized;
        };
    }

    private String resolveCodeMirrorMode(String language) {
        return LANGUAGE_MODES.getOrDefault(language, LANGUAGE_MODES.get("java"));
    }

    private String escapeForJavaScript(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "")
                .replace("\n", "\\n")
                .replace("</", "<\\/");
    }

    private static String buildEditorHtml() {
        String codeMirrorCss = loadResourceText("META-INF/resources/webjars/codemirror/" + CODEMIRROR_VERSION + "/lib/codemirror.css");
        String themeCss = loadResourceText("META-INF/resources/webjars/codemirror/" + CODEMIRROR_VERSION + "/theme/material-darker.css");
        String codeMirrorJs = loadResourceText("META-INF/resources/webjars/codemirror/" + CODEMIRROR_VERSION + "/lib/codemirror.js");
        String clikeModeJs = loadResourceText("META-INF/resources/webjars/codemirror/" + CODEMIRROR_VERSION + "/mode/clike/clike.js");
        String javascriptModeJs = loadResourceText("META-INF/resources/webjars/codemirror/" + CODEMIRROR_VERSION + "/mode/javascript/javascript.js");
        String pythonModeJs = loadResourceText("META-INF/resources/webjars/codemirror/" + CODEMIRROR_VERSION + "/mode/python/python.js");
        String sqlModeJs = loadResourceText("META-INF/resources/webjars/codemirror/" + CODEMIRROR_VERSION + "/mode/sql/sql.js");
        String xmlModeJs = loadResourceText("META-INF/resources/webjars/codemirror/" + CODEMIRROR_VERSION + "/mode/xml/xml.js");
        String cssModeJs = loadResourceText("META-INF/resources/webjars/codemirror/" + CODEMIRROR_VERSION + "/mode/css/css.js");
        String htmlMixedModeJs = loadResourceText("META-INF/resources/webjars/codemirror/" + CODEMIRROR_VERSION + "/mode/htmlmixed/htmlmixed.js");
        String phpModeJs = loadResourceText("META-INF/resources/webjars/codemirror/" + CODEMIRROR_VERSION + "/mode/php/php.js");

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <style>
                """ + codeMirrorCss + "\n" + themeCss + """
                  </style>
                  <style>
                    :root {
                      color-scheme: dark;
                      --editor-bg: #0b1220;
                      --editor-surface: #111a2d;
                      --editor-border: #31415f;
                      --editor-text: #dbe7ff;
                      --editor-muted: #7f92b2;
                    }

                    * {
                      box-sizing: border-box;
                    }

                    html, body {
                      height: 100%;
                      margin: 0;
                      background:
                        radial-gradient(circle at top left, rgba(96, 165, 250, 0.12), transparent 38%),
                        linear-gradient(180deg, var(--editor-surface) 0%, var(--editor-bg) 100%);
                      color: var(--editor-text);
                      font-family: Consolas, "Cascadia Code", "Courier New", monospace;
                      overflow: hidden;
                    }

                    .editor-shell {
                      height: 100%;
                    }

                    .CodeMirror {
                      height: 100%;
                      min-height: 280px;
                      background: transparent;
                      color: var(--editor-text);
                      font-family: Consolas, "Cascadia Code", "Courier New", monospace;
                      font-size: 15px;
                      line-height: 1.6;
                    }

                    .CodeMirror-gutters {
                      background: rgba(15, 23, 42, 0.78);
                      border-right: 1px solid rgba(96, 165, 250, 0.10);
                    }

                    .CodeMirror-linenumber {
                      color: var(--editor-muted);
                    }

                    .CodeMirror-cursor {
                      border-left: 1px solid #f8fafc;
                    }

                    .CodeMirror-selected {
                      background: rgba(96, 165, 250, 0.28) !important;
                    }

                    .CodeMirror.cm-review-readonly .CodeMirror-cursor {
                      display: none;
                    }
                  </style>
                </head>
                <body>
                  <div id="editor-shell" class="editor-shell"></div>

                  <script>
                """ + codeMirrorJs + "\n" + clikeModeJs + "\n" + javascriptModeJs + "\n"
                + pythonModeJs + "\n" + sqlModeJs + "\n" + xmlModeJs + "\n" + cssModeJs + "\n"
                + htmlMixedModeJs + "\n" + phpModeJs + """
                  </script>
                  <script>
                    const host = document.getElementById('editor-shell');
                    const editor = CodeMirror(host, {
                      value: '',
                      mode: 'text/x-java',
                      theme: 'material-darker',
                      lineNumbers: true,
                      lineWrapping: false,
                      indentUnit: 4,
                      tabSize: 4,
                      indentWithTabs: false,
                      smartIndent: true,
                      electricChars: true,
                      viewportMargin: Infinity
                    });

                    editor.on('change', instance => {
                      if (window.javaBridge && typeof window.javaBridge.onContentChanged === 'function') {
                        window.javaBridge.onContentChanged(instance.getValue());
                      }
                    });

                    window.setEditorState = function(text, mode, readOnly) {
                      const nextValue = text || '';
                      const nextMode = mode || 'text/x-java';
                      const nextReadOnly = !!readOnly;
                      if (editor.getOption('mode') !== nextMode) {
                        editor.setOption('mode', nextMode);
                      }
                      if (editor.getOption('readOnly') !== nextReadOnly) {
                        editor.setOption('readOnly', nextReadOnly);
                        if (nextReadOnly) {
                          editor.getWrapperElement().classList.add('cm-review-readonly');
                        } else {
                          editor.getWrapperElement().classList.remove('cm-review-readonly');
                        }
                      }
                      if (editor.getValue() !== nextValue) {
                        editor.setValue(nextValue);
                      }
                      editor.refresh();
                    };

                    window.focusEditor = function() {
                      editor.focus();
                    };

                    window.getEditorText = function() {
                      return editor.getValue() || '';
                    };

                    window.addEventListener('resize', () => editor.refresh());
                    editor.refresh();
                  </script>
                </body>
                </html>
                """;
    }

    private static String loadResourceText(String resourcePath) {
        try (InputStream inputStream = EmbeddedCodeEditor.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing editor resource: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load editor resource: " + resourcePath, exception);
        }
    }

    public final class JavaBridge {
        public void onContentChanged(String updatedText) {
            text = updatedText == null ? "" : updatedText;
            if (changeListener != null) {
                Platform.runLater(changeListener);
            }
        }
    }
}
