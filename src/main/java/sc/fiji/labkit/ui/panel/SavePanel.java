/*-
 * #%L
 * The Labkit image segmentation tool for Fiji.
 * %%
 * Copyright (C) 2017 - 2023 Matthias Arzt
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package sc.fiji.labkit.ui.panel;

import ij.IJ;
import ij.ImagePlus;
import io.scif.services.DatasetIOService;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Cast;
import org.scijava.plugin.Parameter;
import sc.fiji.labkit.ui.DefaultExtensible;
import sc.fiji.labkit.ui.Extensible;
import sc.fiji.labkit.ui.labeling.Label;
import sc.fiji.labkit.ui.labeling.Labeling;
import sc.fiji.labkit.ui.labeling.LabelingSerializer;
import sc.fiji.labkit.ui.models.LabelingModel;
import net.miginfocom.swing.MigLayout;
import org.scijava.ui.behaviour.util.RunnableAction;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;
import java.util.function.Supplier;

import static sc.fiji.labkit.ui.Main.start;

/**
 * Panel that shows the list of labels.
 */
public class SavePanel {

    private final LabelingModel model;
    private final LabelingSerializer serializer;
    private final ComponentList<Label, JPanel> list = new ComponentList<>();
    private final JPanel panel;
    private final JFrame dialogParent;
    private final Function<Supplier<Label>, JPopupMenu> menuFactory;

    @Parameter
    DatasetService datasetService;

    @Parameter
    DatasetIOService datasetIOService;

    public SavePanel(JFrame dialogParent, LabelingModel model, Extensible extensible,
                      boolean fixedLabels, Function<Supplier<Label>, JPopupMenu> menuFactory)
    {
        this.model = model;
        this.serializer = new LabelingSerializer(extensible.context());
        this.dialogParent = dialogParent;
        this.panel = initPanel(fixedLabels);
        this.menuFactory = menuFactory;
//        model.listeners().addListener(this::update);
//        model.selected().notifier().addListener(() -> list.setSelected(model.selected().get()));
//        list.listeners().addListener(() -> model.selected().set(list.getSelected()));
        update();
    }

    public static JPanel newFramedSavePanel(
            LabelingModel imageLabelingModel, DefaultExtensible extensible,
            boolean fixedLabels)
    {
        return GuiUtils.createGroupedPanel( "Saving and Stardist", new SavePanel(extensible
                .dialogParent(), imageLabelingModel, extensible,
                fixedLabels, item1 -> extensible.createPopupMenu(Label.LABEL_MENU,
                item1)).getComponent());
    }

    public JComponent getComponent() {
        return panel;
    }

    // -- Helper methods --

    private void update() {
        list.clear();
//        List<Label> items = model.items();
//        items.forEach((label) -> list.add(label, new EntryPanel(label)));
    }

    private JPanel initPanel(boolean fixedLabels) {
        JPanel panel = new JPanel();
        panel.setLayout(new MigLayout("insets 0, gap 0", "[grow]", "[grow][]"));
        list.getComponent().setBorder(BorderFactory.createEmptyBorder());
        //panel.add(list.getComponent(), "grow, span, push, wrap");
        if (!fixedLabels) {
            JPanel buttonsPanel = new JPanel();
            buttonsPanel.setBackground(UIManager.getColor("List.background"));
            buttonsPanel.setLayout(new MigLayout("insets 4pt, gap 4pt", "[grow]",
                    ""));
            buttonsPanel.add(GuiUtils.createActionIconButton("Save all",
                            new RunnableAction("Save all", this::saveAllLabels), "remove.png"),
                    "gapbefore push");
            buttonsPanel.add(GuiUtils.createActionIconButton("Stardist",
                            new RunnableAction("Save all", this::callStardist), "remove.png"),
                    "gapbefore push");
            panel.add(buttonsPanel, "grow, span");
        }
        return panel;
    }

//    private void saveAllLabels() {
//        List<Label> items = new ArrayList<>(model.items());
//        items.forEach(model::removeLabel);
//    }
    public void saveAllLabels() {
        String bitmapFolderName = "instance_bitmaps";
        Path pathToFolder = Paths.get(model.defaultFileName()).resolveSibling(bitmapFolderName);
        String pathToLabelingFolder = model.defaultFileName();

        String label_name = "l_" + model;
        Path pathToBitmap = pathToFolder.resolve(label_name);
        RandomAccessibleInterval<? extends IntegerType<?>> img = model.labeling().get().getIndexImg();
        ImagePlus imp = ImageJFunctions.wrap(Cast.unchecked(img), "labeling", null);

        try {
            Files.createDirectories(pathToFolder);
            IJ.save(imp, pathToBitmap.toFile().getPath());
            serializer.save(model.labeling().get(), pathToLabelingFolder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //ImagePlus imp = ImageJFunctions.show(Cast.unchecked(img), "Labeling");
    }
    private void callStardist() {
        Path pathToFolder = Paths.get("/home/david/Work/catrin-bcd/");
        String labeling_filename = "saved_segmentation.tiff.labeling";
        Path pathToLabeling = pathToFolder.resolve(labeling_filename);
        try {
            // Create a socket object and connect to the server
            Socket soc = new Socket("localhost", 2004);
            // Create a data output stream to write data to the socket
            DataOutputStream dout = new DataOutputStream(soc.getOutputStream());
            // Create a data input stream to read data from the socket
            DataInputStream din = new DataInputStream(soc.getInputStream());
            // Write the message in bytes
            dout.writeBytes("1");
            // Flush and close the stream and the socket
            dout.flush();
            //dout.close();

            String msg = din.readUTF();

            if (msg.equals("1")){
                Labeling labeling = serializer.open(pathToLabeling.toString());
                model.labeling().set(labeling);
            }
 //           soc.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

//        Path pathToFolder = Paths.get("/home/david/Work/catrin-bcd/");
//        String filename = "saved_segmentation.tiff";
//        Path pathToFile = pathToFolder.resolve(filename);
//        RandomAccessibleInterval<? extends IntegerType<?>> img = model.labeling().get().getIndexImg();
//        ImagePlus imp = ImageJFunctions.wrap(Cast.unchecked(img), "labeling", null);
//        IJ.save(imp, filename);

        //ImagePlus imp = ImageJFunctions.show(Cast.unchecked(img), "Labeling");
    }
    //        ImgSaver saver = new ImgSaver(extensible.context());
//        saver.saveImg(filename, ImgView.wrap(img, null));
//        System.out.println(img.toString());

//    private JButton initializeAddLabelButton() {
//        //RunnableAction addLabelAction = new RunnableAction("Add label", this::addLabel);
//        JButton button = GuiUtils.createActionIconButton("Add label", addLabelAction, "add.png");
//        button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
//                .put(KeyStroke.getKeyStroke("ctrl A"), "create new label");
//        button.getActionMap().put("create new label", addLabelAction);
//        button.setToolTipText("<html><small>Keyboard shortcut:</small></html>");
//        return button;
//    }

//    private void changeSelectedLabel() {
//        Label label = list.getSelected();
//        if (label != null)
//            model.selected().set(label);
//    }

//    private void addLabel() {
//        model.addLabel();
//    }

//    private void removeAllLabels() {
//        List<Label> items = new ArrayList<>(model.items());
//        items.forEach(model::removeLabel);
//    }

//    private void renameLabel(Label label) {
//        final String oldName = label.name();
//        String newName = JOptionPane.showInputDialog(dialogParent,
//                "Rename label \"" + oldName + "\" to:", oldName);
//        if (newName == null) return;
//        model.renameLabel(label, newName);
//    }

//    private void changeColor(Label label) {
//        ARGBType color = label.color();
//        Color newColor = JColorChooser.showDialog(dialogParent,
//                "Choose Color for Label \"" + label.name() + "\"", new Color(color
//                        .get()));
//        if (newColor == null) return;
//        model.setColor(label, new ARGBType(newColor.getRGB()));
//    }

//    private void localize(Label label) {
//        model.localizeLabel(label);
//    }

    // -- Helper methods --
//    private class EntryPanel extends JPanel {
//
//        private final Label label;
//
//        EntryPanel(Label label) {
//            this.label = label;
//            setOpaque(true);
//            setLayout(new MigLayout("insets 4pt, gap 4pt, fillx"));
//            add(initColorButton());
//            add(new JLabel(label.name()), "grow, push, width 0:0:pref");
//            JPopupMenu menu = menuFactory.apply(() -> this.label);
//            add(initPopupMenuButton(menu));
//            setComponentPopupMenu(menu);
//            add(initFinderButton(), "gapx 4pt");
//            add(initVisibilityCheckbox());
//            initRenameOnDoubleClick();
//        }
//
//        private JCheckBox initVisibilityCheckbox() {
//            JCheckBox checkBox = GuiUtils.styleCheckboxUsingEye(new JCheckBox());
//            checkBox.setSelected(label.isVisible());
//            checkBox.addItemListener(event -> {
//                model.setActive(label, event.getStateChange() == ItemEvent.SELECTED);
//            });
//            checkBox.setOpaque(false);
//            return checkBox;
//        }
//
//        private JButton initPopupMenuButton(JPopupMenu menu) {
//            JButton button = new BasicArrowButton(BasicArrowButton.SOUTH);
//            button.setFocusable(false);
//            button.addActionListener(actionEvent -> {
//                menu.show(button, 0, button.getHeight());
//            });
//            return button;
//        }
//
//        private void initRenameOnDoubleClick() {
//            addMouseListener(new MouseAdapter() {
//
//                public void mouseClicked(MouseEvent event) {
//                    if (event.getClickCount() == 2) renameLabel(label);
//                }
//            });
//        }
//
//        private JButton initColorButton() {
//            JButton colorButton = new JButton();
//            colorButton.setFocusable(false);
//            colorButton.setBorder(new EmptyBorder(1, 1, 1, 1));
//            colorButton.setIcon(GuiUtils.createIcon(new Color(label.color().get())));
//            colorButton.addActionListener(l -> changeColor(label));
//            return colorButton;
//        }
//
//        private JButton initFinderButton() {
//            return GuiUtils.createIconButton(GuiUtils.createAction("locate",
//                    () -> localize(label), "crosshair.png"));
//        }
//
//    }

}
