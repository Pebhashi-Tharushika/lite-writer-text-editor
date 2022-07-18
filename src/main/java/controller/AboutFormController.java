package controller;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.util.Duration;

public class AboutFormController {
    public AnchorPane root;
    public ImageView imgLog;

    public void initialize(){

        ScaleTransition st = new ScaleTransition(Duration.millis(750),imgLog);
        st.setFromX(0);
        st.setToX(1);
        st.setFromY(0);
        st.setToY(1);
        st.playFromStart();

        FadeTransition ft = new FadeTransition(Duration.millis(750),root);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.playFromStart();
    }
}
