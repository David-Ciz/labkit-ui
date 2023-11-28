package sc.fiji.labkit.ui.actions;

import ij.IJ;
import ij.ImagePlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.IntegerType;
import net.imglib2.util.Cast;
import sc.fiji.labkit.ui.BasicLabelingComponent;
import sc.fiji.labkit.ui.Extensible;
import sc.fiji.labkit.ui.labeling.Labeling;
import sc.fiji.labkit.ui.labeling.LabelingSerializer;
import sc.fiji.labkit.ui.models.LabelingModel;
import sc.fiji.labkit.ui.models.SegmentationModel;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
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
                        SaveLabeling(segmentationModel.imageLabelingModel(), extensible);
                        try {
                            Socket soc = new Socket("localhost", 2004);
                            // Create a data output stream to write data to the socket
                            DataOutputStream dout = new DataOutputStream(soc.getOutputStream());
                            DataInputStream din = new DataInputStream(soc.getInputStream());
                            String bitmapFolderName = "instance_bitmaps";
                            Path pathToFolder = Paths.get(segmentationModel.imageLabelingModel().defaultFileName()).getParent();
                            String imagePath = pathToFolder.toString();
                            // Write the message in UTF
                            dout.writeUTF(String.format("metrics %s", imagePath));
                            // Flush and close the stream and the socket
                            dout.flush();
                            //dout.close();

                            String msg = din.readUTF();
                            if (msg.equals("success")) {
                                System.exit(0);
                                break;
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }


                    case JOptionPane.NO_OPTION:
//                        //System.exit(0);
//
                          System.exit(0);
                    case JOptionPane.CANCEL_OPTION:
                        break;
                }
            }


        });
    }
    public void SaveLabeling(LabelingModel model, Extensible extensible){
        String bitmapFolderName = "instance_bitmaps";
        Path pathToFolder = Paths.get(model.defaultFileName()).resolveSibling(bitmapFolderName);
        try {
            Files.createDirectories(Paths.get(model.defaultFileName()).resolveSibling(bitmapFolderName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String label_name = "l_" + model;
        Path pathToBitmap = pathToFolder.resolve(label_name);
        String pathToLabelingFolder = model.defaultFileName();
        RandomAccessibleInterval<? extends IntegerType<?>> img = model.labeling().get().getIndexImg();
        ImagePlus imp = ImageJFunctions.wrap(Cast.unchecked(img), "labeling", null);
        IJ.save(imp, pathToBitmap.toFile().getPath());
        LabelingSerializer serializer = new LabelingSerializer(extensible.context());
        try {
            serializer.save(model.labeling().get(), pathToLabelingFolder);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}
