package controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.web.HTMLEditor;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class TextEditorFormController {
    public MenuItem mnuNew;
    public MenuItem mnuOpen;
    public MenuItem mnuSave;
    public MenuItem mnuPrint;
    public MenuItem mnuClose;
    public MenuItem mnuCut;
    public MenuItem mnuCopy;
    public MenuItem mnuPaste;
    public MenuItem mnuSelectAll;
    public MenuItem mnuAbout;
    public HTMLEditor txtEditor;

    public void initialize() {

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
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        mnuNew.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                txtEditor.setHtmlText("");
            }
        });

        mnuClose.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                Platform.exit();
            }
        });
    }
}
