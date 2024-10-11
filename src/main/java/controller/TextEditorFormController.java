package controller;


import netscape.javascript.JSObject;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.HTMLEditor;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import util.SearchResult;

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextEditorFormController {
    public MenuItem mnuNew;
    public MenuItem mnuOpen;
    public MenuItem mnuSave;
    public MenuItem mnuSaveAs;
    public MenuItem mnuPrint;
    public MenuItem mnuClose;
    public MenuItem mnuCut;
    public MenuItem mnuCopy;
    public MenuItem mnuPaste;
    public MenuItem mnuSelectAll;
    public MenuItem mnuFindAndReplace;
    public MenuItem mnuAbout;
    public HTMLEditor txtEditor;

    private final Clipboard clipboard = Clipboard.getSystemClipboard();

    public Button btnDown;
    public Button btnUp;
    public CheckBox chkMatchCase;
    public TextField txtFind;
    public TextField txtReplace;
    public Button btnReplace;
    public Button btnReplaceAll;
    public Label lblResults;
    public TabPane pneFindAndReplace;
    public AnchorPane pneContainer;
    public Tab pneTab;
    private Path currentFilePath;
    private WebEngine webEngine;

    private final ArrayList<SearchResult> searchResultList = new ArrayList<>();
    private int pos = 0;

    private boolean isSelected = false;
    private boolean isCalculated = false;
    private boolean isClickedReplace = false;
    private boolean isClickedReplaceAll = false;


    public void initialize() {
        removeTabPane();

//        chkMatchCase.setSelected(false);

        txtFind.textProperty().addListener((ov, previous, current) -> calculateSearchResult(false, false));

        WebView webView = (WebView) txtEditor.lookup("WebView"); // Get internal WebView of HTMLEditor

        if (webView != null) {

            // Set up the focus listener for the html editor
            webView.focusedProperty().addListener((obs, oldState, newState) -> {
                System.out.println("AWA");
                if (oldState && !newState) {
                    System.out.println("GIYA");
                    if (btnUp.isFocused()) {
                        if (isCalculated && pos == 0) pos = 1;
                        else if (pos == 0 || pos == searchResultList.size()) pos = searchResultList.size();

                    } else if (btnDown.isFocused()) {
                        if (pos == searchResultList.size()) pos = 0;
                        else if (pos >= searchResultList.size() - 1 || (isCalculated && pos == 0)) pos = -1;

                    } else if (txtFind.isFocused() || txtReplace.isFocused()) {
                        if (pos == searchResultList.size()) pos = 0; //up
                        if (pos == -1) pos = searchResultList.size() - 1; //down
                        select();
                    }
                    if (isCalculated) isCalculated = false;
                }
            });

            webEngine = webView.getEngine(); // Get WebEngine associated with HTMLEditor

            // Set drag event handlers for WebView's text area
            webView.setOnDragOver(this::rootOnDragOver);
            webView.setOnDragDropped(this::rootOnDragDropped);

            // Expose JavaFX methods to JavaScript (use JSObject)
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                System.out.println("state: html editor state changed");
                if (newState == Worker.State.SUCCEEDED) {

                    /*if(isClickedReplace){
                        System.out.println("state: btn replace clicked");
                        isClickedReplace = false;
                        return;
                    }*/

                    System.out.println("state: new state == success");

                    // Make the current class instance available to JavaScript
                    JSObject window = (JSObject) webEngine.executeScript("window");
                    window.setMember("java", this);

                    // Set up the MutationObserver to detect changes in the HTML editor
                    webEngine.executeScript(
                            "var timeoutId;" +  // Declare a variable to handle the debounce timeout
                                    "var observer = new MutationObserver(function(mutations) {" +
                                    "    var relevantChange = false;" + // Flag to track if relevant changes occurred
                                    "    mutations.forEach(function(mutation) {" +
                                    "        if (mutation.type === 'characterData' || mutation.type === 'childList') {" +
                                    "            relevantChange = true;" + // If relevant change detected, set the flag
                                    "        }" +
                                    "    });" +
                                    "    if (relevantChange) { " +
                                    "        clearTimeout(timeoutId);" +  // Clear the previous timeout if still active
                                    "        timeoutId = setTimeout(function() { " +  // Set a new timeout (debounce)
                                    "            console.log('Debounced and filtered content change');" +
                                    "                java.calculateSearchResult(false,false);" +
                                    "        }, 200);" +  // Debounce interval (200ms)
                                    "    }" +
                                    "});" +
                                    "observer.observe(document.body, { childList: true, subtree: true, characterData: true });"
                    );
                }
            });
        }

        mnuNew.setOnAction(actionEvent -> {
            txtEditor.setHtmlText("");
            currentFilePath = null;
        });

        mnuSave.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                if (currentFilePath == null) {
                    saveAs();
                } else {
                    saveFile(currentFilePath);
                }
            }
        });

        mnuSaveAs.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                saveAs();
            }
        });

        mnuPrint.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                if (Printer.getDefaultPrinter() == null) {
                    showAlert(Alert.AlertType.ERROR, "No default printer has been selected");
                    return;
                }

                PrinterJob printerJob = PrinterJob.createPrinterJob();
                if (printerJob != null) {
                    if (printerJob.showPageSetupDialog(txtEditor.getScene().getWindow())) {
                        if (printerJob.printPage(txtEditor)) {
                            printerJob.endJob();
                            showAlert(Alert.AlertType.INFORMATION, "Document printed successfully!");
                        } else {
                            showAlert(Alert.AlertType.ERROR, "Error printing document. Try again.");
                        }
                    }
                } else {
                    showAlert(Alert.AlertType.ERROR, "Failed to initialize a new printer job");
                }
            }

        });

        mnuOpen.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                openFile();
            }
        });

        mnuClose.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                Platform.exit();
            }
        });

        mnuCut.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                if (webEngine != null) {
                    setContentOnSysClipboard(webEngine);
                    webEngine.executeScript("window.getSelection().deleteFromDocument()"); // Remove the selected text from the HTMLEditor
                }
            }
        });

        mnuCopy.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                if (webEngine != null) setContentOnSysClipboard(webEngine);
            }
        });

        mnuPaste.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                if (webEngine != null) {
                    Object clipboardHtmlContent = clipboard.getContent(DataFormat.HTML); // Get clipboard HTML content if HTML content is available in the clipboard

                    if (clipboardHtmlContent instanceof String) {
                        String pastedHtml = (String) clipboardHtmlContent;
                        webEngine.executeScript("document.execCommand('insertHTML', false, '" + pastedHtml + "')");
                    } else {
                        Object clipboardTextContent = clipboard.getContent(DataFormat.PLAIN_TEXT); // Get clipboard plain text content if no HTML content is available
                        if (clipboardTextContent instanceof String) {
                            String pastedText = (String) clipboardTextContent;
                            String escapedHtml = escapeForHtml(pastedText); // Escape special characters for HTML
                            webEngine.executeScript("document.execCommand('insertHTML', false, '" + escapedHtml + "')"); // Insert plain text, converted to HTML
                        }
                    }
                }
            }
        });

        mnuSelectAll.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                if (webEngine != null) {
                    webEngine.executeScript("document.body.focus(); document.execCommand('selectAll', false, null);"); // Execute a JavaScript script to select all texts
                }
            }
        });

        mnuFindAndReplace.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {

                if (!pneContainer.getChildren().contains(pneFindAndReplace)) {
                    pneContainer.getChildren().add(pneFindAndReplace); // add the TabPane from the layout

                    if (pneFindAndReplace.getTabs().isEmpty()) {
                        pneFindAndReplace.getTabs().add(pneTab);  // Re-add the tab to the TabPane if it's not already present
                        pneFindAndReplace.getSelectionModel().select(pneTab); // Bring the tab to the front to make it active
                    }

                    // Adjust the top anchor of the htmlEditor to account for the FindAndReplace pane
                    AnchorPane.setTopAnchor(txtEditor, AnchorPane.getTopAnchor(pneFindAndReplace) + pneFindAndReplace.getPrefHeight());
                }
            }
        });

        mnuAbout.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                URL resource = this.getClass().getResource("/view/AboutForm.fxml");
                if (resource == null) {
                    showAlert(Alert.AlertType.ERROR, "Resource not found");
                    return;
                }
                try {
                    Parent container = FXMLLoader.load(resource);
                    Scene aboutScene = new Scene(container);
                    Stage aboutStage = new Stage();
                    aboutStage.setScene(aboutScene);
                    aboutStage.setTitle("About");
                    aboutStage.initModality(Modality.APPLICATION_MODAL);
                    aboutStage.show();
                    aboutStage.centerOnScreen();
                    aboutStage.setResizable(false);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }


    private String escapeForHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "<br>")    // Convert newlines to HTML <br> tags
                .replace("\r", "");       // Remove carriage returns
    }

    private void saveFile(Path filePath) {
        try (FileOutputStream fos = new FileOutputStream(filePath.toFile(), false)) {
            String text = txtEditor.getHtmlText();
            byte[] bytes = text.getBytes();
            fos.write(bytes);
            currentFilePath = filePath;
            showAlert(Alert.AlertType.INFORMATION, "File saved successfully!");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error saving file: " + e.getMessage());
        }

        /*try {
            WebEngine webEngine = getWebEngine();
            if (webEngine != null) {
                String htmlContent = (String) webEngine.executeScript("document.documentElement.outerHTML");
                Files.writeString(filePath, htmlContent);
                currentFilePath = filePath; // Update current file path
                showAlert(Alert.AlertType.INFORMATION, "File saved successfully!");
            }
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error saving file: " + e.getMessage());
        }*/
    }

    private void saveAs() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save As");
        fileChooser.setInitialFileName(currentFilePath == null ? "untitled" : currentFilePath.getFileName().toString());
        File file = fileChooser.showSaveDialog(txtEditor.getScene().getWindow());
        if (file == null) return;
        saveFile(file.toPath());


        /*FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save As");
        File file = new File(System.getProperty("user.home"));
        fileChooser.setInitialDirectory(file);
        fileChooser.setInitialFileName(currentFilePath == null ? "untitled" : currentFilePath.getFileName().toString());
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("All Files", "*.*"));

        File saveLocation = fileChooser.showSaveDialog(txtEditor.getScene().getWindow()); //display as modal window
        if (saveLocation != null) {
            saveFile(saveLocation.toPath());
        }*/
    }

    private void openFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open a text file");
        File file = fileChooser.showOpenDialog(txtEditor.getScene().getWindow());
        if (file == null) return;

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] bytes = fis.readAllBytes();
            txtEditor.setHtmlText(new String(bytes));
            currentFilePath = file.toPath();
            showAlert(Alert.AlertType.INFORMATION, "File opened successfully!");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Error opening file: " + e.getMessage());
        }


        /*FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open File");
//        fileChooser.setInitialDirectory(new File(".")); // initial directory is the current directory
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home"))); // initial directory is home directory
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*", "*")
        );

        File file = fileChooser.showOpenDialog(txtEditor.getScene().getWindow());
        if (file != null) {
            try {
                WebEngine webEngine = getWebEngine();
                if (webEngine != null) {
                    String htmlContent = Files.readString(file.toPath());
                    webEngine.loadContent(htmlContent);
                    currentFilePath = file.toPath();
                    showAlert(Alert.AlertType.INFORMATION, "File opened successfully!");
                }
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Error opening file: " + e.getMessage());
            }
        }*/
    }

    private void showAlert(Alert.AlertType alertType, String message) {
        Alert alert = new Alert(alertType, message);
        alert.showAndWait();
    }

    private void setContentOnSysClipboard(WebEngine webEngine) {
        if (webEngine != null) {
            // Execute a JavaScript script to get full HTML of the selected content
            Object selectedHtml = webEngine.executeScript(
                    "var sel = window.getSelection();" +
                            "if (sel.rangeCount > 0) {" +
                            "    var range = sel.getRangeAt(0);" +
                            "    var div = document.createElement('div');" +
                            "    div.appendChild(range.cloneContents());" +
                            "    div.innerHTML;" +
                            "} else { '' }"
            );

            if (selectedHtml instanceof String) {
                String selectedString = (String) selectedHtml;
                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putHtml(selectedString); // Put HTML content into clipboard

                // Also add plain text fallback
                clipboardContent.putString(selectedString.replaceAll("<[^>]+>", ""));  // Strip HTML tags for plain text

                clipboard.setContent(clipboardContent);
            }
        }
    }

    /* drag and drop text file on editor*/

    public void rootOnDragOver(DragEvent dragEvent) {
        Dragboard dragboard = dragEvent.getDragboard();
        if (dragboard.hasFiles()) {
            dragEvent.acceptTransferModes(TransferMode.COPY_OR_MOVE);
        }
        dragEvent.consume();
    }

    public void rootOnDragDropped(DragEvent dragEvent) {
        Dragboard dragboard = dragEvent.getDragboard();
        boolean success = false;
        if (dragboard.hasFiles()) {
            List<File> files = dragboard.getFiles();
            if (!files.isEmpty()) {
                File droppedFile = files.get(0);
                try (FileInputStream fis = new FileInputStream(droppedFile)) {
                    byte[] bytes = fis.readAllBytes();
                    txtEditor.setHtmlText(new String(bytes));
                    success = true;
                } catch (IOException e) {
                    showAlert(Alert.AlertType.ERROR, e.getMessage());
                }
            }
        }
        dragEvent.setDropCompleted(success);
        dragEvent.consume();
    }

    /* find and replace */


    // This method will be called when content changes in the HTMLEditor and textField (Find)
    public void calculateSearchResult(boolean isReplaced, boolean isChecked) {
        System.out.println("cal: isClickedReplace" + isClickedReplace);
        System.out.println("cal: isReplaced" + isReplaced);

        if (isClickedReplace && !isReplaced) {
            isClickedReplace = false;
            return;
        }

        isCalculated = true;
        String query = txtFind.getText();
        int previousPos = pos;

        searchResultList.clear();
        System.out.println("cal:------------- before: calculateSearchResult");

        if (txtFind.isFocused()) {
            System.out.println("cal: deselect for calculate search result if txtFind is focused");
            deselectHtmlEditor();
        }

        if (query == null || query.isEmpty()) {
            lblResults.setText(String.format("%d Results", 0));
            System.out.println("cal: search karanna text ekk na. isSelected- " + isSelected);
            System.out.println("cal: txtFind.isFocused() - " + txtFind.isFocused());
            return;
        }

        System.out.println("cal: search karanna text ekk tiye. isSelected- " + isSelected);
        Pattern pattern;
        try {
            pattern = chkMatchCase.isSelected() ? Pattern.compile(query) : Pattern.compile(query, Pattern.CASE_INSENSITIVE);
        } catch (RuntimeException e) {
            return;
        }

        List<Integer> newlineOffsets = new ArrayList<>();

        // Debounce (optional, adjust timeout if needed)
        new Thread(() -> {
            try {
                Thread.sleep(200); // Debounce delay (adjust as needed)

                // Access UI elements only on FX application thread
                Platform.runLater(() -> {
                    if (webEngine != null) {
                        String plainText = getPlainTextFromHtmlEditor(webEngine, newlineOffsets);
                        System.out.println("cal: plain Text: " + plainText);
                        Matcher matcher = pattern.matcher(plainText);
                        while (matcher.find()) {
                            int start = matcher.start();
                            int end = matcher.end();
                            int adjustedStart = adjustForOffsets(start, newlineOffsets);
                            int adjustedEnd = adjustForOffsets(end, newlineOffsets);
                            SearchResult result = new SearchResult(adjustedStart, adjustedEnd);
                            searchResultList.add(result);
                        }

                        lblResults.setText(String.format("%d Results", searchResultList.size()));

                        if (!isReplaced || isChecked || previousPos >= searchResultList.size() || previousPos < 0)
                            pos = 0;

                        if (txtFind.isFocused() || btnUp.isFocused() || btnDown.isFocused() || btnReplace.isFocused() || chkMatchCase.isFocused()) {
                            select(); // Highlight the first search result (if any)
                        }
                    }
                });
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }).start();
    }

    // adjust positions based on newline offsets
    private int adjustForOffsets(int position, List<Integer> offsets) {
        int adjustedPosition = position;
        for (Integer offset : offsets) {
            if (offset < position) {
                adjustedPosition--; // Shift back for each newline
            } else {
                break;
            }
        }
        return adjustedPosition;
    }


    private void deselectHtmlEditor() {
        if (webEngine != null) {
            webEngine.executeScript("window.getSelection().removeAllRanges();"); // Deselect text using JavaScript
            isSelected = false;
        }
    }


    private void selectRangeInHtmlEditor(int start, int end) {
        if (webEngine != null) {
            // JavaScript to traverse the text nodes and select the correct range
            String script = String.format(
                    "function selectTextInRange(start, end) {" +
                            "    var selection = window.getSelection();" +
                            "    var range = document.createRange();" +
                            "    var textNode = null;" +
                            "    var charCount = 0;" +
                            "    var nodeStack = [document.body];" + // Stack to traverse the DOM
                            "    while (nodeStack.length > 0) {" +
                            "        var node = nodeStack.pop();" +
                            "        if (node.nodeType === Node.TEXT_NODE) {" + // Check if the node is a text node
                            "            var textLength = node.textContent.length;" +
                            "            if (charCount + textLength > start) {" +
                            "                textNode = node;" +
                            "                var adjustedStart = Math.max(0, start - charCount);" +
                            "                var adjustedEnd = Math.min(node.textContent.length, end - charCount);" +
                            "                if (adjustedStart < adjustedEnd) {" +
                            "                    range.setStart(textNode, adjustedStart);" + // Adjust start index
                            "                    range.setEnd(textNode, adjustedEnd);" + // Adjust end index
                            "                    selection.removeAllRanges();" + // Clear any previous selection
                            "                    selection.addRange(range);" + // Add the new range
                            "                    break;" + // Stop once selection is made for the current node
                            "                }" +
                            "            }" +
                            "            charCount += textLength;" + // Count the total number of characters it has traversed so far
                            "        } else if (node.nodeType === Node.ELEMENT_NODE && node.tagName !== 'BR') {" +
                            "            for (var i = node.childNodes.length - 1; i >= 0; i--) {" +
                            "                nodeStack.push(node.childNodes[i]);" +
                            "            }" +
                            "        }" +
                            "    }" +
                            "}" +
                            "selectTextInRange(%d, %d);", start, end);

            webEngine.executeScript(script); // Execute the JavaScript to select the text
            isSelected = true;
        }
    }

    private String getPlainTextFromHtmlEditor(WebEngine webEngine, List<Integer> newlineOffsets) {
        Object htmlContent = webEngine.executeScript("document.body.innerText");
        String plainText = "";
        newlineOffsets.clear();

        if (htmlContent instanceof String) {
            String rawText = (String) htmlContent;
            StringBuilder normalizedText = new StringBuilder();
            int offset = 0;

            for (char c : rawText.toCharArray()) {
                if (c == '\n') {
                    normalizedText.append(" ");  // Ensure space after each newline
                    newlineOffsets.add(offset);   // Track newline position as an offset
                } else {
                    normalizedText.append(c);
                }
                offset++;
            }
            plainText = normalizedText.toString();
        }
        return plainText;
    }


    private void select() {
        System.out.println("select: search List is empty? - " + searchResultList.isEmpty());
        if (searchResultList.isEmpty()) return;
        System.out.println("select - pos: " + pos);
        System.out.println("select: txt find is focused? - " + txtFind.isFocused()); //true
        SearchResult searchResult = searchResultList.get(pos);
        selectRangeInHtmlEditor(searchResult.getStart(), searchResult.getEnd());

        // Request focus back to txtFind after selecting in HTML editor after replacing all
        if (isClickedReplaceAll) {
            System.out.println("select: replace all btn is clicked ");
            txtFind.requestFocus();
            txtFind.positionCaret(txtFind.getText().length());
            isClickedReplaceAll = false;
        }

        System.out.println("select: txt find is focused after selecting ? - " + txtFind.isFocused()); //false
        lblResults.setText(String.format("%d/%d Results", (pos + 1), searchResultList.size()));

    }

    public void btnDownOnAction(ActionEvent actionEvent) {
        pos++;
        System.out.println("POSDown ++ = " + pos);
        if (pos >= searchResultList.size()) {
            if (pos == searchResultList.size() + 1) {
                pos = 1;
                select();
                return;
            }
            pos = -1;
            System.out.println("POSD = " + pos);
            return;
        }
        select();
    }

    public void btnUpOnAction(ActionEvent actionEvent) {
        pos--;
        System.out.println("POSUP = " + pos);
        if (pos < 0) {
            if (pos < -1) {
                pos = searchResultList.size() - 2;
                select();
                return;
            }
            pos = searchResultList.size();
            return;
        }
        select();
    }

    public void chkMatchCaseOnAction(ActionEvent actionEvent) {
        calculateSearchResult(false, true);
        System.out.println(chkMatchCase.isSelected());
        System.out.println(chkMatchCase.isSelected() ? "care case sensitivity" : "ignore cs");
    }

    public void btnReplaceAllOnAction(ActionEvent actionEvent) {

        isClickedReplaceAll = true;
        if (txtFind.getText().isEmpty() || searchResultList.isEmpty()) {
            return;
        }

        if (webEngine != null) {
            Object htmlContent = webEngine.executeScript("document.body.innerHTML"); // Get the HTML content from the editor

            if (htmlContent instanceof String) {
                String htmlText = (String) htmlContent;

                Pattern pattern = Pattern.compile(Pattern.quote(txtFind.getText())); // Compile the pattern from the search term
                Matcher matcher = pattern.matcher(htmlText);
                String updatedHtmlText = matcher.replaceAll(Matcher.quoteReplacement(txtReplace.getText())); // Replace all instances in the HTML content

                txtEditor.setHtmlText(updatedHtmlText); // Set the updated HTML content back to the editor
                lblResults.setText("Result " + 0);
                searchResultList.clear();
                deselectHtmlEditor();

            }

        }

    }


    public void btnReplaceOnAction(ActionEvent actionEvent) {
        isClickedReplace = true;

        if (txtFind.getText().isEmpty() || searchResultList.isEmpty() || !isSelected) {
            return;
        }

        if (webEngine != null) {
            System.out.println("replace: current pos to replace: " + pos);

            // Escape single quotes in txtFind and txtReplace text
            String findTextEscaped = txtFind.getText().replace("'", "\\'");
            String replaceTextEscaped = txtReplace.getText().replace("'", "\\'");

            String replacementScript = String.format(
                    "var selection = window.getSelection();" +
                            "var matchCase = %b;" +  // Pass `true` for case-sensitive, `false` for case-insensitive
                            "if (selection.rangeCount > 0) {" +
                            "    var range = selection.getRangeAt(0);" +
                            "    var selectedText = range.toString();" +
                            "    var compareText = matchCase ? selectedText : selectedText.toLowerCase();" + // Adjust comparison based on matchCase
                            "    var targetText = matchCase ? '%s' : '%s'.toLowerCase();" + // Adjust target text based on matchCase
                            "    if (compareText.trim() === targetText) {" +
                            "        var start = range.startOffset;" +
                            "        var end = range.endOffset;" +
                            "        var textBefore = range.startContainer.textContent.substring(0, start);" +
                            "        var textAfter = range.endContainer.textContent.substring(end);" +

                            // Handle isolated cases based on surrounding characters
                            "        var replacementText;" +
                            "        if (textBefore.endsWith(' ') && textAfter.startsWith(' ')) {" + // Case 1: Isolated with spaces on both sides
                            "            replacementText = '%s ';" +
                            "        } else if (textBefore.endsWith(' ') && textAfter.trim() === '') {" + // Case 2: End of line with space before
                            "            replacementText = ' %s';" +
                            "        } else if (textBefore.trim() === '' && textAfter.startsWith(' ')) {" + // Case 3: Beginning of line with space after
                            "            replacementText = '%s ';" +
                            "        } else {" + // Case 4: General replacement for inline cases
                            "            replacementText = '%s';" +
                            "        }" +

                            "        selection.deleteFromDocument();" +
                            "        document.execCommand('insertText', false, replacementText);" +
                            "    }" +
                            "}",
                    chkMatchCase.isSelected(), // Set matchCase to true/false based on selection
                    findTextEscaped, findTextEscaped, // Target text for both cases
                    replaceTextEscaped, replaceTextEscaped, replaceTextEscaped, replaceTextEscaped
            );

            webEngine.executeScript(replacementScript); // Execute the JavaScript replacement script
            calculateSearchResult(true, false);
        }
    }

    public void pneTabOnClosed(Event event) {
        removeTabPane();
    }

    private void removeTabPane() {
        pneContainer.getChildren().remove(pneFindAndReplace); // Remove the TabPane from the layout
        AnchorPane.setTopAnchor(txtEditor, AnchorPane.getTopAnchor(pneFindAndReplace)); // Move HTMLEditor up by setting the same top anchor as TabPane had
    }
}
