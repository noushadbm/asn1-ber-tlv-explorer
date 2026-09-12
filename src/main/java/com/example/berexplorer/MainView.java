package com.example.berexplorer;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

public final class MainView {
    private final Stage stage;
    private final BorderPane root = new BorderPane();
    private final TextArea input = new TextArea();
    private final TreeView<TlvNode> tree = new TreeView<>();
    private final TextArea details = new TextArea();
    private final Label status = new Label("Ready");
    private final Label byteCount = new Label("0 bytes");
    private final ComboBox<String> inputFormat = new ComboBox<>();

    public MainView(Stage stage) {
        this.stage = stage;
        build();
    }

    public BorderPane root() { return root; }

    private void build() {
        root.setTop(toolbar());
        root.setCenter(content());
        root.setBottom(statusBar());
        input.setPromptText("Paste BER/DER as hexadecimal, e.g. 30 82 02 19 04 10 ...");
        input.setWrapText(true);
        input.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 13px;");
        details.setEditable(false);
        details.setWrapText(false);
        details.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 13px;");
        tree.setShowRoot(true);
        tree.setCellFactory(tv -> new TreeCell<>() {
            @Override protected void updateItem(TlvNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(String.format("%s  [%s]  len=%s  @%d%s",
                        item.typeName(), item.tagClass() == TlvNode.TagClass.UNIVERSAL ? String.format("0x%02X", item.universalTagByte()) : "tag " + item.tagNumber(),
                        item.indefiniteLength() ? "indefinite" : Long.toString(item.length()), item.offset(),
                        item.constructed() ? "  (constructed)" : ""));
            }
        });
        tree.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && selected.getValue() != null) showDetails(selected.getValue());
        });
        inputFormat.getItems().addAll("HEX", "BASE64");
        inputFormat.getSelectionModel().selectFirst();
    }

    private Node toolbar() {
        Button open = new Button("Open BER/DER…");
        open.setOnAction(e -> openFile());
        Button decode = new Button("Decode");
        decode.getStyleClass().add("primary-button");
        decode.setOnAction(e -> decode());
        Button clear = new Button("Clear");
        clear.setOnAction(e -> { input.clear(); tree.setRoot(null); details.clear(); byteCount.setText("0 bytes"); status.setText("Ready"); });
        Button paste = new Button("Paste");
        paste.setOnAction(e -> input.setText(Clipboard.getSystemClipboard().getString()));
        ToolBar bar = new ToolBar(open, new Separator(), new Label("Input:"), inputFormat, paste, decode, clear);
        bar.setPadding(new Insets(8));
        return bar;
    }

    private Node content() {
        VBox inputBox = new VBox(6, new Label("BER / DER Input"), input, byteCount);
        VBox.setVgrow(input, Priority.ALWAYS);
        inputBox.setPadding(new Insets(10));
        inputBox.setPrefWidth(450);

        VBox treeBox = new VBox(6, new Label("TLV Explorer"), tree);
        VBox.setVgrow(tree, Priority.ALWAYS);
        treeBox.setPadding(new Insets(10));
        treeBox.setPrefWidth(620);

        VBox detailBox = new VBox(6, new Label("Selected Node"), details);
        VBox.setVgrow(details, Priority.ALWAYS);
        detailBox.setPadding(new Insets(10));
        detailBox.setPrefWidth(420);

        SplitPane split = new SplitPane(inputBox, treeBox, detailBox);
        split.setDividerPositions(0.32, 0.76);
        return split;
    }

    private Node statusBar() {
        HBox box = new HBox(15, status);
        box.setPadding(new Insets(6, 10, 8, 10));
        return box;
    }

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open BER/DER file");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("BER/DER files", "*.ber", "*.der", "*.bin"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        var file = chooser.showOpenDialog(stage);
        if (file == null) return;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            inputFormat.getSelectionModel().select("HEX");
            input.setText(HexUtils.hex(bytes, bytes.length));
            status.setText("Loaded " + file.getName() + " (" + bytes.length + " bytes)");
            decodeBytes(bytes);
        } catch (IOException ex) {
            showError("Could not open file", ex.getMessage());
        }
    }

    private void decode() {
        try {
            byte[] bytes;
            if (inputFormat.getValue().equals("HEX")) bytes = HexUtils.parseHex(input.getText());
            else bytes = java.util.Base64.getMimeDecoder().decode(input.getText());
            decodeBytes(bytes);
        } catch (IllegalArgumentException ex) {
            showError("Invalid input", ex.getMessage());
        }
    }

    private void decodeBytes(byte[] bytes) {
        try {
            TlvNode rootNode = new BerDecoder().decodeSingle(bytes);
            tree.setRoot(toTree(rootNode));
            tree.getRoot().setExpanded(true);
            expandFirstLevels(tree.getRoot(), 2);
            byteCount.setText(bytes.length + " bytes");
            status.setText("Decoded successfully");
            showDetails(rootNode);
        } catch (BerDecoder.BerDecodeException ex) {
            tree.setRoot(null);
            details.clear();
            byteCount.setText(bytes.length + " bytes");
            status.setText("Decode failed");
            showError("BER decode error", ex.getMessage());
        }
    }

    private TreeItem<TlvNode> toTree(TlvNode node) {
        TreeItem<TlvNode> item = new TreeItem<>(node);
        for (TlvNode child : node.children()) item.getChildren().add(toTree(child));
        return item;
    }

    private void expandFirstLevels(TreeItem<TlvNode> item, int depth) {
        if (depth < 0) return;
        item.setExpanded(true);
        if (depth == 0) return;
        for (TreeItem<TlvNode> child : item.getChildren()) expandFirstLevels(child, depth - 1);
    }

    private void showDetails(TlvNode n) {
        StringBuilder s = new StringBuilder();
        s.append("Type             : ").append(n.typeName()).append('\n');
        s.append("Tag class        : ").append(n.tagClass()).append('\n');
        s.append("Constructed      : ").append(n.constructed()).append('\n');
        s.append("Tag number       : ").append(n.tagNumber()).append('\n');
        if (n.tagClass() == TlvNode.TagClass.UNIVERSAL && n.tagNumber() < 31) {
            s.append("Tag octet        : 0x").append(String.format("%02X", n.universalTagByte())).append('\n');
        }
        s.append("Offset            : ").append(n.offset()).append('\n');
        s.append("Header length     : ").append(n.headerLength()).append(" bytes\n");
        s.append("Value offset      : ").append(n.valueOffset()).append('\n');
        s.append("Length            : ").append(n.indefiniteLength() ? "indefinite" : n.length()).append('\n');
        s.append("Total TLV length  : ").append(n.totalLength()).append(" bytes\n");
        s.append("Children          : ").append(n.children().size()).append('\n');
        s.append('\n');
        s.append("Decoded value:\n  ").append(BerDecoder.displayValue(n)).append('\n');
        s.append('\n');
        s.append("Raw value (hex):\n  ").append(HexUtils.hex(n.value(), 512)).append('\n');
        s.append('\n');
        s.append("ASCII preview:\n  ").append(HexUtils.asciiPreview(n.value(), 256)).append('\n');
        details.setText(s.toString());
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
