package controller;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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

    public void initialize() {

        removeTabPane();

        txtFind.textProperty().addListener((ov, previous, current) -> calculateSearchResult());

        // Create a StringProperty to hold the HTML content
        StringProperty htmlContentProperty = new SimpleStringProperty();

        // Bind the StringProperty to the HTMLEditor's HTML content
        htmlContentProperty.bind(Bindings.createStringBinding(
                txtEditor::getHtmlText,  // Supplier to get the HTML content
                txtEditor.getStyleClass() // This is just a dummy dependency, replace with an appropriate property
        ));

        // Add a listener to the StringProperty
        htmlContentProperty.addListener((observable, oldValue, newValue) -> {
            calculateSearchResult();
        });

//        txtEditor.textProperty().addListener((ov, previous, current) -> calculateSearchResult());


        WebView webView = (WebView) txtEditor.lookup("WebView"); // Get internal WebView of  HTMLEditor

        if (webView != null) {
            webEngine = webView.getEngine(); // Get WebEngine associated with HTMLEditor

            // Set drag event handlers for WebView's text area
            webView.setOnDragOver(this::rootOnDragOver);
            webView.setOnDragDropped(this::rootOnDragDropped);
        }

        mnuNew.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                txtEditor.setHtmlText("");
                currentFilePath = null;
            }
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

    private void calculateSearchResult() {
        String query = txtFind.getText();
        searchResultList.clear();
        pos = 0;
        deselectHtmlEditor();

        Pattern pattern;
        try {
            pattern = Pattern.compile(query);
        } catch (RuntimeException e) {
            return;
        }
        Matcher matcher = pattern.matcher(getPlainTextFromHtmlEditor());

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            SearchResult result = new SearchResult(start, end);
            searchResultList.add(result);
        }

        lblResults.setText(String.format("%d Results", searchResultList.size()));

        select();
    }

    private void deselectHtmlEditor() {
        if (webEngine != null) {
            webEngine.executeScript("window.getSelection().removeAllRanges();");  // Execute JavaScript to deselect the text
        }
    }

    private String getPlainTextFromHtmlEditor() {
        String htmlContent = txtEditor.getHtmlText(); // Get the HTML content
        return htmlContent.replaceAll("<[^>]*>", "");
    }


    private void select() {
        if (searchResultList.isEmpty()) return;
        SearchResult searchResult = searchResultList.get(pos);
        selectRangeInHtmlEditor(searchResult.getStart(), searchResult.getEnd());
        lblResults.setText(String.format("%d/%d Results", (pos + 1), searchResultList.size()));
    }


    private void selectRangeInHtmlEditor(int start, int end) {

        if (webEngine != null) {
            // Inject JavaScript to select the text range
            String script = String.format(
                    "var range = document.createRange();" +
                            "var selection = window.getSelection();" +
                            "var node = document.body;" + // Assuming the content is within the body tag
                            "range.setStart(node.firstChild, %d);" +  // Start position (character index)
                            "range.setEnd(node.firstChild, %d);" +    // End position (character index)
                            "selection.removeAllRanges();" +          // Clear any previous selection
                            "selection.addRange(range);", start, end);


            webEngine.executeScript(script); // Execute the JavaScript to select the text
        }
    }


    public void btnDownOnAction(ActionEvent actionEvent) {
        pos++;
        if (pos == searchResultList.size()) {
            pos = -1;
            return;
        }
        select();
    }

    public void btnUpOnAction(ActionEvent actionEvent) {
        pos--;
        if (pos < 0) {
            pos = searchResultList.size();
            return;
        }
        select();
    }

    public void chkMatchCaseOnAction(ActionEvent actionEvent) {
    }

    public void btnReplaceAllOnAction(ActionEvent actionEvent) {
    }

    public void btnReplaceOnAction(ActionEvent actionEvent) {
    }


    public void pneTabOnClosed(Event event) {
        removeTabPane();
    }

    private void removeTabPane(){
        pneContainer.getChildren().remove(pneFindAndReplace); // Remove the TabPane from the layout
        AnchorPane.setTopAnchor(txtEditor, AnchorPane.getTopAnchor(pneFindAndReplace)); // Move HTMLEditor up by setting the same top anchor as TabPane had
    }
}
