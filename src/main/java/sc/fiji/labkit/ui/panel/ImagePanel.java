
package sc.fiji.labkit.ui.panel;

import bdv.export.ProgressWriter;
import ij.IJ;
import ij.ImagePlus;
import io.scif.services.DatasetIOService;
import net.imagej.Dataset;
import net.imagej.DatasetService;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Cast;
import net.miginfocom.swing.MigLayout;
import org.scijava.Context;
import org.scijava.app.StatusService;
import org.scijava.io.location.FileLocation;
import org.scijava.plugin.Parameter;
import org.scijava.ui.behaviour.util.RunnableAction;
import sc.fiji.labkit.ui.*;
import sc.fiji.labkit.ui.inputimage.DatasetInputImage;
import sc.fiji.labkit.ui.inputimage.InputImage;
import sc.fiji.labkit.ui.inputimage.SpimDataInputImage;
import sc.fiji.labkit.ui.labeling.Label;
import sc.fiji.labkit.ui.labeling.LabelingSerializer;
import sc.fiji.labkit.ui.models.LabelingModel;
import sc.fiji.labkit.ui.plugin.CziOpener;
import sc.fiji.labkit.ui.utils.progress.StatusServiceProgressWriter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicArrowButton;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static sc.fiji.labkit.ui.Main.start;

public class ImagePanel {
    private final LabelingModel model;
    private final LabelingSerializer serializer;
    private final ComponentList<Path, JPanel> list = new ComponentList<>();
    private final JPanel panel;

    private final List<Path> image_paths;
    private final JFrame dialogParent;
    private final Function<Supplier<Label>, JPopupMenu> menuFactory;
    private final BasicLabelingComponent labelingComponent;

    @Parameter
    DatasetService datasetService;

    @Parameter
    DatasetIOService datasetIOService;

    public ImagePanel(JFrame dialogParent, LabelingModel model, Extensible extensible,
                     boolean fixedLabels, Function<Supplier<Label>, JPopupMenu> menuFactory, List<Path> image_paths,
                      BasicLabelingComponent labelingComponent)
    {
        this.model = model;
        this.serializer = new LabelingSerializer(extensible.context());
        this.dialogParent = dialogParent;
        this.panel = initPanel(fixedLabels);
        this.menuFactory = menuFactory;
        this.image_paths = image_paths;
        this.labelingComponent = labelingComponent;
        
//        model.listeners().addListener(this::update);
//        model.selected().notifier().addListener(() -> list.setSelected(model.selected().get()));
//        list.listeners().addListener(() -> model.selected().set(list.getSelected()));
        update();
    }

    public static JPanel newFramedImagePanel(
            LabelingModel imageLabelingModel, DefaultExtensible extensible,
            boolean fixedLabels, List<Path> image_paths, BasicLabelingComponent labelingComponent)
    {
        return GuiUtils.createGroupedPanel("Images", new ImagePanel(extensible
                .dialogParent(), imageLabelingModel, extensible,
                fixedLabels, item1 -> extensible.createPopupMenu(Label.LABEL_MENU,
                item1), image_paths, labelingComponent).getComponent());
    }

    public JComponent getComponent() {
        return panel;
    }

    // -- Helper methods --

    private void update() {
        list.clear();

        image_paths.forEach((image) -> list.add(image, new EntryPanel(image)));
    }

    private JPanel initPanel(boolean fixedLabels) {
        JPanel panel = new JPanel();
        panel.setLayout(new MigLayout("insets 0, gap 0", "[grow]", "[grow][]"));
        list.getComponent().setBorder(BorderFactory.createEmptyBorder());
        panel.add(list.getComponent(), "grow, span, push, wrap");
        if (!fixedLabels) {
            JPanel buttonsPanel = new JPanel();
            buttonsPanel.setBackground(UIManager.getColor("List.background"));
            buttonsPanel.setLayout(new MigLayout("insets 4pt, gap 4pt", "[grow]",
                    ""));
            panel.add(buttonsPanel, "grow, span");
        }
        return panel;
    }

    private class EntryPanel extends JPanel {

        private final Path image;

        EntryPanel(Path image) {
            this.image = image;
            setOpaque(true);
            setLayout(new MigLayout("insets 4pt, gap 4pt, fillx"));
            //add(initColorButton());
            add(new JLabel(image.getFileName().toString()), "grow, push, width 0:0:pref");
//            JPopupMenu menu = menuFactory.apply(() -> this.image);
//            add(initPopupMenuButton(menu));
//            setComponentPopupMenu(menu);
//            add(initFinderButton(), "gapx 4pt");
//            add(initVisibilityCheckbox());
           initOpenOnDoubleClick();
        }

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
        private void openNewInstance(Path image_path) {
            //TODO: make this nicer, this is stupid
            switch (JOptionPane.showConfirmDialog(dialogParent,
                    "Do you want to save your changes?", "Close Window?",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE)){
                case JOptionPane.YES_OPTION:
                    //  SaveLabeling(segmentationModel.imageLabelingModel());
                    String bitmapFolderName = "instance_bitmaps";
                    Path pathToFolder = Paths.get(model.defaultFileName()).resolveSibling(bitmapFolderName);
                    String label_name = "l_" + model;
                    Path pathToBitmap = pathToFolder.resolve(label_name);
                    RandomAccessibleInterval<? extends IntegerType<?>> img = model.labeling().get().getIndexImg();
                    ImagePlus imp = ImageJFunctions.wrap(Cast.unchecked(img), "labeling", null);

                    String pathToLabelingFolder = model.defaultFileName();
                    try {
                        Files.createDirectories(pathToFolder);
                        IJ.save(imp, pathToBitmap.toFile().getPath());
                        serializer.save(model.labeling().get(), pathToLabelingFolder);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    openNewWindow(image_path);
                case JOptionPane.NO_OPTION:
                    openNewWindow(image_path);
                case JOptionPane.CANCEL_OPTION:
                    break;
            }



            //TODO: possible memory leak
            //dialogParent.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            //dialogParent.dispatchEvent(new WindowEvent(dialogParent, WindowEvent.WINDOW_CLOSING))

            //start(img_path.toString());
        }

        private void openNewWindow(Path image_path){
            final Path absolutePath = image_path.toAbsolutePath();
            //Save the previous image

            //Context context = null;
            Context context = serializer.getContext();
            ProgressWriter progressWriter = new StatusServiceProgressWriter(context
                    .service(StatusService.class));
            InputImage image = openImage(context, progressWriter, absolutePath.toFile());
            labelingComponent.close();
            dialogParent.dispose();

            // remove the semantic bitmap so it's easier for user to understand the need to create it again

            String bitmapFolderName = "semantic_bitmaps";
            String bitmapFileName = "saved_segmentation.tiff";
            Path pathToFile = Paths.get(model.defaultFileName()).resolveSibling(bitmapFolderName).resolve(bitmapFileName);

            try {
                Files.deleteIfExists(pathToFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            LabkitFrame.showForImage(context, image);
        }

        private void initOpenOnDoubleClick() {
            addMouseListener(new MouseAdapter() {

                public void mouseClicked(MouseEvent event) {
                    if (event.getClickCount() == 2) openNewInstance(image);
                }
            });
        }


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

    }
    private static InputImage openImage(Context context, ProgressWriter progressWriter,
                                        File file)
    {
        String filename = file.getAbsolutePath();
        if (filename.endsWith(".h5"))
            filename = filename.replaceAll("\\.h5$", ".xml");
        if (filename.endsWith(".czi"))
            return new CziOpener(progressWriter).openWithDialog(file.getAbsolutePath());
        if (filename.endsWith(".xml") || filename.endsWith(".ims"))
            return SpimDataInputImage.openWithGuiForLevelSelection(filename);
        try {
            Dataset dataset = context.service(DatasetIOService.class).open(new FileLocation(file));
            DatasetInputImage datasetInputImage = new DatasetInputImage(dataset);
            datasetInputImage.setDefaultLabelingFilename(filename + ".labeling");
            return datasetInputImage;
        }
        catch (IOException e) {
            throw new UnsupportedOperationException(
                    "Could not open the image file: " + file);
        }
    }
}

