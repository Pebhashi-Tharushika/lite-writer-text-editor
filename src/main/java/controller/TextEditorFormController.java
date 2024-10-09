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


    public void initialize() {
        removeTabPane();

        txtFind.textProperty().addListener((ov, previous, current) -> calculateSearchResult(false));

        WebView webView = (WebView) txtEditor.lookup("WebView"); // Get internal WebView of HTMLEditor

        if (webView != null) {

            // Set up the focus listener for the html editor
            webView.focusedProperty().addListener((obs, oldState, newState) -> {
                if (oldState && !newState) {
                    System.out.println("---------- focused");
                    if (btnUp.isFocused()) {
                        if(isCalculated && pos==0) pos=1;
                        else if (pos==0 || pos==searchResultList.size()) pos = searchResultList.size();

                    } else if (btnDown.isFocused()) {
                        if(pos >= searchResultList.size()-1 || (isCalculated && pos==0)) pos = -1;

                    } else if (txtFind.isFocused()) {
                        if(pos== searchResultList.size()) pos = 0; //up
                        if(pos==-1) pos = searchResultList.size() - 1; //down
                        select();
                    }
                    if(isCalculated)isCalculated = false;
                }
            });

            webEngine = webView.getEngine(); // Get WebEngine associated with HTMLEditor

            // Set drag event handlers for WebView's text area
            webView.setOnDragOver(this::rootOnDragOver);
            webView.setOnDragDropped(this::rootOnDragDropped);

            // Expose JavaFX methods to JavaScript (use JSObject)
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
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
                                    "                java.calculateSearchResult(false);" +
                                    "        }, 200);" +  // Debounce interval (200ms)
                                    "    }" +
                                    "});" +
                                    "observer.observe(document.body, { childList: true, subtree: true, characterData: true });"
                    );
                    System.out.println("---------- state");
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



        txtEditor.focusedProperty().addListener((obs, oldState, newState) -> {
            if (!oldState && newState) {
                System.out.println("text editor ekata awa");
                    select();
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
    public void calculateSearchResult(boolean isReplaced) {
        isCalculated = true;
        String query = txtFind.getText();
        searchResultList.clear();
        System.out.println("---------------- before: calculateSearchResult");
        if(!isReplaced){
                pos = 0;
                System.out.println("change pos: " + pos);
        }
        System.out.println("calculateSearchResult - deselect: " + txtEditor.isFocused());
        if ((txtEditor.isFocused() && isSelected) || txtFind.isFocused()) {
            System.out.println("deselect for calculate search result");
            deselectHtmlEditor();
        }

        if (query == null || query.isEmpty()) {
            lblResults.setText(String.format("%d Results", 0));
            return;
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(query);
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

                        System.out.println("calculateSearchResult - select: " + txtEditor.isFocused());
                        if (!txtEditor.isFocused() && (txtFind.isFocused() || btnUp.isFocused() || btnDown.isFocused())) {
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
        newlineOffsets.clear();  // Clear previous offsets if any

        if (htmlContent instanceof String) {
            String rawText = (String) htmlContent;
            StringBuilder normalizedText = new StringBuilder();
            int offset = 0;

            for (char c : rawText.toCharArray()) {
                if (c == '\n') {
                    normalizedText.append(" ");
                    newlineOffsets.add(offset); // Track each newline position as an offset
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
        if (searchResultList.isEmpty()) return;
        System.out.println("select - pos: " + pos);
        SearchResult searchResult = searchResultList.get(pos);
        selectRangeInHtmlEditor(searchResult.getStart(), searchResult.getEnd());
        lblResults.setText(String.format("%d/%d Results", (pos + 1), searchResultList.size()));

    }

    public void btnDownOnAction(ActionEvent actionEvent) {
//        System.out.println("POSDown = " + pos);
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
//            System.out.println("POSU = " + pos);
            return;
        }
        select();
    }

    public void chkMatchCaseOnAction(ActionEvent actionEvent) {
    }

    public void btnReplaceAllOnAction(ActionEvent actionEvent) {

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
            }

        }
    }

    public void btnReplaceOnAction(ActionEvent actionEvent) {

        if (txtFind.getText().isEmpty() || searchResultList.isEmpty() || !isSelected) {
            return;
        }

        if (webEngine != null) {
            SearchResult result = searchResultList.get(pos); // Get the current selection range
            int start = result.getStart();
            int end = result.getEnd();

            /*// Use the existing method to select the text range in the HTML editor
            selectRangeInHtmlEditor(start, end);*/

            // Replace the selected text in the editor
            /*String replacementScript = String.format(
                "var selection = window.getSelection();" +
                "if (selection.rangeCount > 0) {" +
                "    selection.deleteFromDocument();" + // Clear current selection
                "    document.execCommand('insertText', false, '%s');" + // Insert replacement text
                "}", txtReplace.getText()
            );*/

            String replacementScript = String.format(
                    "var selection = window.getSelection();" +
                            "if (selection.rangeCount > 0) {" +
                            "    var range = selection.getRangeAt(0);" +
                            "    var selectedText = range.toString();" +
                            "    if (selectedText === '%s') {" +  // Check if selected text matches txtFind's text
                            "        selection.deleteFromDocument();" +  // Clear the current selection
                            "        document.execCommand('insertText', false, '%s');" +  // Insert replacement text
                            "    }" +
                            "}", txtFind.getText().replace("'", "\\'"), txtReplace.getText().replace("'", "\\'")
            );

            webEngine.executeScript(replacementScript); // Execute JavaScript for replacement

            // Remove the replaced result from the search results list
            calculateSearchResult(true);

            // Update pos and results label to reflect the updated search results


//            lblResults.setText("Result " + (searchResultList.size()));
        }

    }


    /*public void btnReplaceOnAction(ActionEvent actionEvent) {
        if (!(txtFind.getText().isEmpty() || txtReplace.getText().isEmpty() || searchResultList.isEmpty())) {

            if (webEngine != null) {
                Object htmlContent = webEngine.executeScript("document.body.innerHTML"); // Get the HTML content from the editor

                if (htmlContent instanceof String) {
                    String htmlText = (String) htmlContent;
                    System.out.println(htmlText);

                    SearchResult result = searchResultList.get(pos); // Get the currently selected search result range
                    int start = result.getStart();
                    int end = result.getEnd();
                    System.out.println("start: " + start + ", end: " + end);

                    List<Integer> newlineOffsets = new ArrayList<>();
                    String plainText = getPlainTextFromHtmlEditor(webEngine, newlineOffsets);

                    // Split the plain text into three parts: before, to replace, and after
                    String plainBefore = plainText.substring(0, start);
                    String toReplacePlain = plainText.substring(start, end);
                    String plainAfter = plainText.substring(end);
                    System.out.println("before: " + plainBefore); //before: abcjk def
                    System.out.println("toReplace: " + toReplacePlain); //toReplace: jk
                    System.out.println("after: " + plainAfter); //after: ghi  jk lmjknojk pqrjk  stjku vjkw

                    // Find HTML start and calculate HTML end based on the length of the plain text replacement segment
    int htmlStart = convertPlainOffsetToHtmlOffset(plainBefore, htmlText);
    int htmlEnd = calculateHtmlEndOffset(htmlStart, toReplacePlain.length(), htmlText);
                    System.out.println("htmlStart: "+htmlStart);
                    System.out.println("htmlEnd: "+htmlEnd);

                    String htmlBefore = htmlText.substring(0, htmlStart);
                    String htmlAfter = htmlText.substring(htmlEnd);
                    System.out.println("before: " + htmlBefore);
                    System.out.println("after: " + htmlAfter);

                    String updatedHtmlText = htmlBefore + txtReplace.getText() + htmlAfter; // Reassemble the HTML content with the replaced selection


                    System.out.println("updatedHtmlText: " + updatedHtmlText);

                    txtEditor.setHtmlText(updatedHtmlText); // Update the HTML content back to the editor

                    searchResultList.remove(result); // Remove the replaced result from the list
                    lblResults.setText("Result " + searchResultList.size());

                    // Update the current position to the next match, if available
                    *//*if (!searchResultList.isEmpty()) {
                        pos--;
                    } else {
                        pos = -1;
                    }*//*

                }
            }

        }
    }*/

    private int calculateHtmlEndOffset(int htmlStart, int plainLength, String htmlText) {
        int htmlOffset = htmlStart;
        int plainOffset = 0;

        for (int i = htmlStart; i < htmlText.length() && plainOffset < plainLength; i++) {
            char c = htmlText.charAt(i);
            if (c != '<') {
                plainOffset++;
            } else {
                while (i < htmlText.length() && htmlText.charAt(i) != '>') {
                    i++;
                }
            }
            htmlOffset++;
        }
        return htmlOffset;
    }

    // Helper method to adjust plain text offset to HTML offset
    private int convertPlainOffsetToHtmlOffset(String plainTextSegment, String htmlText) {
        int plainTextLength = plainTextSegment.length();
        int htmlOffset = 0;
        int plainOffset = 0;

        int i;
        for (i = 0; i < htmlText.length() && plainOffset < plainTextLength; i++) {
            char c = htmlText.charAt(i);
            System.out.println("c: " + c);
            if (c != '<') {
                if (plainTextSegment.charAt(plainOffset) == c) {
                    plainOffset++;
                }
            } else {
                while (i < htmlText.length() && htmlText.charAt(i) != '>') {
                    i++;
                }
            }
            htmlOffset++;
            System.out.println("i: " + i);
            System.out.println("htmlOffset: " + htmlOffset);
            System.out.println("plainOffset: " + plainOffset);
        }
        System.out.println("final htmlOffset: " + htmlOffset);
        return i;
    }


    public void pneTabOnClosed(Event event) {
        removeTabPane();
    }

    private void removeTabPane() {
        pneContainer.getChildren().remove(pneFindAndReplace); // Remove the TabPane from the layout
        AnchorPane.setTopAnchor(txtEditor, AnchorPane.getTopAnchor(pneFindAndReplace)); // Move HTMLEditor up by setting the same top anchor as TabPane had
    }
}
