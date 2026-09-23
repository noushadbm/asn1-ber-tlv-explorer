package com.example.berexplorer;

import com.example.berexplorer.ber.BerDecoder;
import com.example.berexplorer.definition.DefinitionGenerator;
import com.example.berexplorer.definition.DefinitionParser;
import com.example.berexplorer.definition.Schema;
import com.example.berexplorer.definition.SchemaDecoder;
import com.example.berexplorer.model.TlvNode;
import com.example.berexplorer.util.Hex;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class Main extends Application {
    private final TextArea input = new TextArea();
    private final TextArea definition = new TextArea();
    private final TreeView<TlvNode> rawTree = new TreeView<>();
    private final TreeView<SchemaDecoder.DecodedNode> decodedTree = new TreeView<>();
    private final TextArea details = new TextArea();
    private final TextField detailsSearch = new TextField();
    private final Label detailsSearchStatus = new Label();
    private final HBox detailsSearchBar = new HBox();
    private final TextArea hexView = new TextArea();
    private final ComboBox<String> format = new ComboBox<>();
    private final TextField rootType = new TextField("Message");
    private byte[] currentBytes = new byte[0];
    private TlvNode root;
    private Schema schema;

    @Override public void start(Stage stage) {
        stage.setTitle("ASN.1 BER/DER Explorer — V2");
        BorderPane rootPane = new BorderPane();
        rootPane.setTop(toolbar(stage));
        SplitPane center = new SplitPane(inputPane(), resultPane()); center.setDividerPositions(.40);
        rootPane.setCenter(center);
        Scene scene = new Scene(rootPane, 1400, 850);
        stage.setScene(scene); stage.show();
    }

    private Node toolbar(Stage stage) {
        Button open=new Button("Open BER/DER"); open.setOnAction(e->openFile(stage));
        Button openDef=new Button("Open Definition"); openDef.setOnAction(e->openDefinition(stage));
        Button newDef=new Button("New Definition"); newDef.setOnAction(e->definition.clear());
        Button decode=new Button("Decode TLV"); decode.setOnAction(e->decodeInput());
        Button generate=new Button("Generate Definition"); generate.setOnAction(e->generateDefinition());
        Button apply=new Button("Apply Definition"); apply.setOnAction(e->applyDefinition());
        Button saveDef=new Button("Save Definition"); saveDef.setOnAction(e->saveDefinition(stage));
        format.getItems().addAll("HEX","BASE64"); format.setValue("HEX");
        Label rootLabel=new Label("Root:"); rootType.setPrefWidth(130);
        HBox bar=new HBox(8,open,openDef,new Label("Input:"),format,decode,new Separator(),generate,apply,newDef,saveDef,rootLabel,rootType);
        bar.setPadding(new Insets(8)); return bar;
    }

    private Node inputPane(){
        input.setPromptText("Paste BER/DER as hexadecimal or Base64. Example: 30 82 02 19 ..."); input.setWrapText(true);
        definition.setPromptText("ASN.1 definition, e.g. Message ::= SEQUENCE { id OCTET STRING, timestamp GeneralizedTime }");
        TitledPane defPane=new TitledPane("ASN.1 Definition", definition); defPane.setExpanded(true);
        VBox box=new VBox(6,new Label("Encoded input"),input,defPane); VBox.setVgrow(input,Priority.ALWAYS); VBox.setVgrow(defPane,Priority.ALWAYS); box.setPadding(new Insets(8)); return box;
    }

    private Node resultPane(){
        TabPane tabs=new TabPane();
        Tab tlv=new Tab("TLV Explorer",rawTree); tlv.setClosable(false);
        detailsSearch.setPromptText("Find in details");
        HBox.setHgrow(detailsSearch,Priority.ALWAYS);
        Button previousMatch=new Button("Previous"); previousMatch.setOnAction(e->findDetailsMatch(true,false));
        Button nextMatch=new Button("Next"); nextMatch.setOnAction(e->findDetailsMatch(false,false));
        Button closeSearch=new Button("Close"); closeSearch.setOnAction(e->hideDetailsSearch());
        detailsSearchBar.getChildren().addAll(detailsSearch,detailsSearchStatus,previousMatch,nextMatch,closeSearch);
        detailsSearchBar.setSpacing(6); detailsSearchBar.setPadding(new Insets(0,0,6,0));
        detailsSearchBar.setVisible(false); detailsSearchBar.setManaged(false);
        VBox detailsPanel=new VBox(detailsSearchBar,details); VBox.setVgrow(details,Priority.ALWAYS); detailsPanel.setPadding(new Insets(6));
        SplitPane inspect=new SplitPane(decodedTree,detailsPanel); inspect.setDividerPositions(.55); details.setEditable(false); details.setWrapText(true); details.setStyle("-fx-font-family: monospace;");
        inspect.addEventFilter(KeyEvent.KEY_PRESSED,e->{
            if(e.isShortcutDown() && e.getCode()==KeyCode.F){showDetailsSearch();e.consume();}
            else if(e.getCode()==KeyCode.ESCAPE && detailsSearchBar.isVisible()){hideDetailsSearch();e.consume();}
            else if(e.getCode()==KeyCode.ENTER && detailsSearch.isFocused()){findDetailsMatch(e.isShiftDown(),false);e.consume();}
        });
        detailsSearch.textProperty().addListener((obs,oldValue,newValue)->findDetailsMatch(false,true));
        details.textProperty().addListener((obs,oldValue,newValue)->{if(detailsSearchBar.isVisible())findDetailsMatch(false,true);});
        Tab dec=new Tab("Decoded Message",inspect); dec.setClosable(false);
        Tab hx=new Tab("Hex Viewer",hexView); hx.setClosable(false); hexView.setEditable(false); hexView.setStyle("-fx-font-family: monospace;");
        tabs.getTabs().addAll(tlv,dec,hx); rawTree.setShowRoot(true); decodedTree.setShowRoot(true);
        decodedTree.setCellFactory(tree -> new TreeCell<>() {
            @Override protected void updateItem(SchemaDecoder.DecodedNode value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label prefix = new Label(value.name + " : " + value.type + " = ");
                Label decodedValue = new Label(value.value);
                if (getTreeItem() != null && getTreeItem().isLeaf()
                        && !value.value.contains("\n") && !value.value.contains("\r")) {
                    decodedValue.setFont(Font.font(decodedValue.getFont().getFamily(), FontWeight.BOLD, decodedValue.getFont().getSize()));
                }
                HBox content = new HBox(prefix, decodedValue);
                setText(null);
                setGraphic(content);
            }
        });
        rawTree.getSelectionModel().selectedItemProperty().addListener((obs,a,b)->{if(b!=null)showDetails(b.getValue());});
        decodedTree.getSelectionModel().selectedItemProperty().addListener((obs,a,b)->{if(b!=null){showDecodedDetails(b.getValue());highlightEncodedInput(b.getValue().raw);}});
        return tabs;
    }

    private void showDetailsSearch(){
        detailsSearchBar.setManaged(true); detailsSearchBar.setVisible(true);
        detailsSearch.requestFocus(); detailsSearch.selectAll();
        findDetailsMatch(false,true);
    }

    private void hideDetailsSearch(){
        detailsSearchBar.setVisible(false); detailsSearchBar.setManaged(false);
        detailsSearchStatus.setText("");
        details.requestFocus();
    }

    private void findDetailsMatch(boolean backwards,boolean fromStart){
        String query=detailsSearch.getText();
        if(query==null || query.isEmpty()){detailsSearchStatus.setText("");return;}
        String text=details.getText().toLowerCase(Locale.ROOT);
        String target=query.toLowerCase(Locale.ROOT);
        List<Integer> matches=new ArrayList<>();
        for(int at=text.indexOf(target);at>=0;at=text.indexOf(target,at+Math.max(1,target.length())))matches.add(at);
        if(matches.isEmpty()){detailsSearchStatus.setText("No matches");return;}
        int selectionStart=details.getSelection().getStart();
        int selectionEnd=details.getSelection().getEnd();
        int matchIndex=0;
        if(!fromStart){
            if(backwards){
                matchIndex=matches.size()-1;
                for(int i=matches.size()-1;i>=0;i--)if(matches.get(i)<selectionStart){matchIndex=i;break;}
            }else{
                int start=selectionEnd;
                matchIndex=0;
                for(int i=0;i<matches.size();i++)if(matches.get(i)>=start){matchIndex=i;break;}
            }
        }
        int matchStart=matches.get(matchIndex);
        details.selectRange(matchStart,matchStart+query.length());
        detailsSearchStatus.setText((matchIndex+1)+" / "+matches.size());
    }

    private void decodeInput(){
        try { currentBytes=format.getValue().equals("HEX")?Hex.parse(input.getText()):Hex.base64(input.getText()); root=BerDecoder.decodeSingle(currentBytes); rawTree.setRoot(toRawItem(root)); hexView.setText(Hex.format(currentBytes)); details.setText("Decoded " + currentBytes.length + " bytes. Select a TLV node."); }
        catch(Exception ex){showError("BER decode failed",ex);}
    }
    private TreeItem<TlvNode> toRawItem(TlvNode n){TreeItem<TlvNode> i=new TreeItem<>(n); for(TlvNode c:n.getChildren())i.getChildren().add(toRawItem(c)); i.setExpanded(true); return i;}

    private void generateDefinition(){
        if(root==null){decodeInput(); if(root==null)return;}
        String name=rootType.getText().isBlank()?"Message":rootType.getText().trim(); definition.setText(DefinitionGenerator.generate(name,root));
    }
    private void applyDefinition(){
        if(root==null){decodeInput(); if(root==null)return;}
        try { schema=DefinitionParser.parse(definition.getText()); String name=rootType.getText().trim(); if(name.isBlank())name=schema.types.get(0).name; SchemaDecoder.DecodedNode d=SchemaDecoder.decode(schema,name,root); decodedTree.setRoot(toDecodedItem(d)); decodedTree.getRoot().setExpanded(true); }
        catch(Exception ex){showError("Definition decode failed",ex);}
    }
    private TreeItem<SchemaDecoder.DecodedNode> toDecodedItem(SchemaDecoder.DecodedNode n){TreeItem<SchemaDecoder.DecodedNode> i=new TreeItem<>(n); for(var c:n.children)i.getChildren().add(toDecodedItem(c)); i.setExpanded(true); return i;}

    private void showDetails(TlvNode n){
        StringBuilder s=new StringBuilder(); s.append("Type: ").append(n.universalTypeName()).append('\n'); s.append("Tag class: ").append(n.getTagClass()).append('\n'); s.append("Tag number: ").append(n.getTagNumber()).append(" (0x").append(Integer.toHexString(n.getTagNumber()).toUpperCase()).append(")\n"); s.append("Constructed: ").append(n.isConstructed()).append('\n'); s.append("Offset: ").append(n.getOffset()).append('\n'); s.append("Header length: ").append(n.getHeaderLength()).append('\n'); s.append("Value offset: ").append(n.getValueOffset()).append('\n'); s.append("Length: ").append(n.getLength()<0?"indefinite":n.getLength()).append('\n'); s.append("Total TLV length: ").append(n.getTotalLength()).append('\n'); s.append("Children: ").append(n.getChildren().size()).append('\n'); if(n.getLength()>=0){s.append("Value: ").append(BerDecoder.decodeValue(n)).append('\n');} if(n.getOffset()>=0 && n.getOffset()+n.getTotalLength()<=currentBytes.length){s.append("Raw TLV:\n").append(Hex.format(Arrays.copyOfRange(currentBytes,n.getOffset(),n.getOffset()+n.getTotalLength())));} details.setText(s.toString());
    }
    private void showDecodedDetails(SchemaDecoder.DecodedNode n){StringBuilder s=new StringBuilder();s.append("Name: ").append(n.name).append('\n').append("ASN.1 type: ").append(n.type).append('\n').append("Value: ").append(n.value).append('\n');if(n.raw!=null){s.append("Tag: ").append(n.raw.getTagClass()).append(' ').append(n.raw.getTagNumber()).append('\n').append("Offset: ").append(n.raw.getOffset()).append('\n').append("Length: ").append(n.raw.getLength()).append('\n');}details.setText(s.toString());}

    private void highlightEncodedInput(TlvNode node) {
        if(node==null || node.getOffset()<0 || node.getTotalLength()<=0)return;
        if(!"HEX".equals(format.getValue())) {
            format.setValue("HEX");
            input.setText(toHex(currentBytes));
        }
        List<Integer> digits=new ArrayList<>();
        String text=input.getText();
        for(int i=0;i<text.length();i++) {
            if(text.charAt(i)=='0' && i+1<text.length() && (text.charAt(i+1)=='x'||text.charAt(i+1)=='X')) {i++;continue;}
            if(Character.digit(text.charAt(i),16)>=0)digits.add(i);
        }
        int first=node.getOffset()*2;
        int last=(node.getOffset()+node.getTotalLength())*2-1;
        if(first>=0 && last<digits.size())input.selectRange(digits.get(first),digits.get(last)+1);
    }

    private void openFile(Stage stage){FileChooser fc=new FileChooser();fc.setTitle("Open BER/DER file");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("BER/DER files","*.ber","*.der","*.bin","*.dat","*.*"));File f=fc.showOpenDialog(stage);if(f==null)return;try{currentBytes=Files.readAllBytes(f.toPath());format.setValue("HEX");input.setText(toHex(currentBytes));decodeInput();}catch(Exception ex){showError("Cannot open file",ex);}}
    private String toHex(byte[] b){StringBuilder s=new StringBuilder();for(int i=0;i<b.length;i++){if(i>0)s.append(' ');s.append(String.format("%02X",b[i]));}return s.toString();}
    private void openDefinition(Stage stage){
        FileChooser fc=new FileChooser(); fc.setTitle("Open ASN.1 definition");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("ASN.1 definitions","*.asn1","*.asn","*.txt","*.*"));
        File f=fc.showOpenDialog(stage); if(f==null)return;
        try { definition.setText(Files.readString(f.toPath())); } catch(Exception ex){ showError("Cannot open definition",ex); }
    }
    private void saveDefinition(Stage stage){FileChooser fc=new FileChooser();fc.setTitle("Save ASN.1 definition");fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("ASN.1 files","*.asn1","*.asn"));File f=fc.showSaveDialog(stage);if(f==null)return;try{Files.writeString(f.toPath(),definition.getText());}catch(Exception ex){showError("Cannot save definition",ex);}}
    private void showError(String title,Exception ex){new Alert(Alert.AlertType.ERROR,title,ButtonType.OK){ {setHeaderText(ex.getMessage());} }.showAndWait();}

    public static void main(String[] args){launch(args);}
}
