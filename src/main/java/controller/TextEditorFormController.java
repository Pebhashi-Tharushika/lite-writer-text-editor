package controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.print.PageLayout;
import javafx.print.Printer;
import javafx.print.PrinterAttributes;
import javafx.print.PrinterJob;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.web.HTMLEditor;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.print.PageFormat;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

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
    public MenuItem mnuAbout;
    public HTMLEditor txtEditor;

    private final Clipboard clipboard = Clipboard.getSystemClipboard();
    private Path currentFilePath;

    public void initialize() {

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
                WebEngine webEngine = getWebEngine();
                setContentOnSysClipboard(webEngine);
                if (webEngine != null) {
                    webEngine.executeScript("window.getSelection().deleteFromDocument()"); // Remove the selected text from the HTMLEditor
                }
            }
        });

        mnuCopy.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                setContentOnSysClipboard(getWebEngine());
            }
        });

        mnuPaste.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                WebEngine webEngine = getWebEngine();
                Object clipboardContent = clipboard.getContent(DataFormat.PLAIN_TEXT); // Get clipboard content

                if (clipboardContent instanceof String) {
                    String pastedText = (String) clipboardContent; // Get the pasted text
                    webEngine.executeScript("document.execCommand('insertText', false, '" + pastedText + "')"); // Execute a JavaScript script to insert the pasted text at the current cursor position
                }
            }
        });

        mnuSelectAll.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                WebEngine webEngine = getWebEngine();
                if (webEngine != null) {
                    webEngine.executeScript("document.body.focus(); document.execCommand('selectAll', false, null);"); // Execute a JavaScript script to select all texts
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

        try(FileInputStream fis = new FileInputStream(file)) {
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

    /* Get WebEngine associated with HTMLEditor */
    private WebEngine getWebEngine() {
        WebView webView = (WebView) txtEditor.lookup(".web-view"); // Get WebView
        return webView.getEngine();
    }

    private void setContentOnSysClipboard(WebEngine webEngine) {
        if (webEngine != null) {
            Object selectedText = webEngine.executeScript("window.getSelection().toString()"); // Execute a JavaScript script to get the selected text

            if (selectedText instanceof String) {
                String selectedString = (String) selectedText;
                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putString(selectedString);
                clipboard.setContent(clipboardContent);
            }
        }
    }


}
