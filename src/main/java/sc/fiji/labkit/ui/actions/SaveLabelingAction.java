package sc.fiji.labkit.ui.actions;

import ij.IJ;
import ij.ImagePlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Cast;
import sc.fiji.labkit.ui.BasicLabelingComponent;
import sc.fiji.labkit.ui.Extensible;
import sc.fiji.labkit.ui.models.LabelingModel;
import sc.fiji.labkit.ui.models.SegmentationModel;

import javax.swing.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SaveLabelingAction {

    public SaveLabelingAction(Extensible extensible, SegmentationModel segmentationModel) {
        extensible.dialogParent().setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        extensible.dialogParent().addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                switch (JOptionPane.showConfirmDialog(extensible.dialogParent(),
                        "Do you want to save your changes?", "Close Window?",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE)){
                    case JOptionPane.YES_OPTION:
                        SaveLabeling(segmentationModel.imageLabelingModel());
                        System.exit(0);
                        break;
                    case JOptionPane.NO_OPTION:
                        System.exit(0);
                        break;
                    case JOptionPane.CANCEL_OPTION:
                        break;
                }
            }


        });
    }
    public void SaveLabeling(LabelingModel model){
        Path pathToFolder = Paths.get("/home/david/Work/catrin-bcd/labeling/");
        String label_name = "l_" + model.toString();
        Path pathToBitmap = pathToFolder.resolve(label_name);
        RandomAccessibleInterval<? extends IntegerType<?>> img = model.labeling().get().getIndexImg();
        ImagePlus imp = ImageJFunctions.wrap(Cast.unchecked(img), "labeling", null);
        IJ.save(imp, pathToBitmap.toFile().getPath());

    }

}
