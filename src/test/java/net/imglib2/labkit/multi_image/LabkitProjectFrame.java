
package net.imglib2.labkit.multi_image;

import net.imagej.ImageJ;
import net.imglib2.labkit.SegmentationComponent;
import net.imglib2.labkit.project.LabeledImage;
import net.imglib2.labkit.project.LabeledImagesListPanel;
import net.imglib2.labkit.project.LabkitProjectFileFilter;
import net.imglib2.labkit.project.LabkitProjectFileSerializer;
import net.imglib2.labkit.project.LabkitProjectModel;
import net.imglib2.labkit.models.SegmentationItem;
import net.imglib2.labkit.models.SegmenterListModel;
import net.imglib2.labkit.project.NewProjectDialog;
import org.scijava.Context;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Labkit main window that allows to work with multiple images.
 * <p>
 * The GUI consists of a Big Data Viewer panel with brush tools and two
 * sidebars. The left side bar for show labels and segmentation algorithms and
 * the right side bar show a list of open images.
 * <p>
 * The left side bar and Big Data Viewer panel are part of the
 * {@link SegmentationComponent}. This frame only adds the right side bar. The
 * {@link SegmentationComponent} is replaced, whenever a new image is selected.
 */
public class LabkitProjectFrame extends JFrame {

	private final JPanel workspace;

	private SegmentationComponent segmentationComponent;

	private final JMenu projectMenu;

	private final ProjectSegmentationModel projectSegmentationModel;

	public LabkitProjectFrame(LabkitProjectModel labkitProjectModel) {
		super("Labkit Project");
		JPanel imagesList = new LabeledImagesListPanel(labkitProjectModel);
		workspace = new JPanel();
		workspace.setPreferredSize(new Dimension(1000, 800));
		workspace.setLayout(new BorderLayout());
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, workspace, imagesList);
		projectSegmentationModel = new ProjectSegmentationModel(labkitProjectModel);
		projectMenu = initProjectMenu(projectSegmentationModel);
		labkitProjectModel.selectedImage().notifier().addListener(() -> updateBdv());
		if (labkitProjectModel.selectedImage().get() != null)
			updateBdv();
		splitPane.setResizeWeight(1);
		splitPane.setOneTouchExpandable(true);
		add(splitPane);
		pack();
	}

	public static void main(String... args) {
		new ImageJ().ui().showUI();
	}

	private static void openProject(LabkitProjectModel labkitProjectModel) {
		JFrame frame = new LabkitProjectFrame(labkitProjectModel);
		frame.setVisible(true);
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	}

	private void updateBdv() {
		if (segmentationComponent != null) {
			workspace.remove(segmentationComponent);
			segmentationComponent.close();
		}
		LabeledImage labeledImage = projectSegmentationModel.projectModel().selectedImage().get();
		if (labeledImage != null) {
			projectSegmentationModel.setSelectedImage(labeledImage);
			SegmentationComponent component = new SegmentationComponent(this, projectSegmentationModel,
				false);
			component.autoContrast();
			workspace.add(component);
			segmentationComponent = component;
			JMenuBar menuBar = component.getMenuBar();
			menuBar.add(projectMenu);
			setJMenuBar(menuBar);
			menuBar.updateUI();
		}
		else {
			JMenuBar menuBar = new JMenuBar();
			menuBar.add(projectMenu);
			setJMenuBar(menuBar);
			segmentationComponent = null;
		}
		workspace.revalidate();
		workspace.repaint();
	}

	private static JMenu initProjectMenu(ProjectSegmentationModel psm) {
		JMenu menu = new JMenu("Project");
		JMenuItem newProjectItem = new JMenuItem("New Project...");
		newProjectItem.addActionListener(ignore -> onNewProjectClicked(psm.context()));
		menu.add(newProjectItem);
		JMenuItem openProjectItem = new JMenuItem("Open Project...");
		openProjectItem.addActionListener(ignore -> onOpenProjectClicked(psm.context()));
		menu.add(openProjectItem);
		JMenuItem saveProjectItem = new JMenuItem("Save Project");
		saveProjectItem.addActionListener(ignore -> {
			onSaveProjectClicked(psm.projectModel(), psm.segmenterList());
		});
		menu.add(saveProjectItem);
		return menu;
	}

	static void onNewProjectClicked(Context context) {
		LabkitProjectModel newProject = NewProjectDialog.show(context);
		if (newProject == null)
			return;
		openProject(newProject);
	}

	static void onOpenProjectClicked(Context context) {
		JFileChooser dialog = new JFileChooser();
		dialog.setFileFilter(new LabkitProjectFileFilter());
		int returnValue = dialog.showOpenDialog(null);
		if (returnValue == JFileChooser.APPROVE_OPTION) {
			File file = dialog.getSelectedFile();
			try {
				openProject(LabkitProjectFileSerializer.open(context, file));
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static void onSaveProjectClicked(LabkitProjectModel projectModel,
		SegmenterListModel segmenterListModel)
	{
		try {
			updateSegmenterFiles(projectModel, segmenterListModel);
			LabkitProjectFileSerializer.save(projectModel, new File(projectModel.getProjectDirectory(),
				"labkit-project.yaml"));
			projectModel.labeledImages().forEach(labeledImage -> labeledImage.save());
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void updateSegmenterFiles(LabkitProjectModel projectModel,
		SegmenterListModel segmenterListModel) throws IOException
	{
		File segmentersDirectory = new File(projectModel.getProjectDirectory(), "segmenters");
		if (!segmentersDirectory.isDirectory())
			Files.createDirectory(segmentersDirectory.toPath());
		List<String> files = new ArrayList<>();
		for (SegmentationItem segmentationItem : segmenterListModel.segmenters().get()) {
			if (!segmentationItem.isTrained())
				continue;
			String result = saveSegmenter(segmentationItem, segmentersDirectory);
			files.add(result);
		}
		List<String> oldFiles = projectModel.segmenterFiles();
		removeObsoleteFiles(files, oldFiles);
		projectModel.segmenterFiles().clear();
		projectModel.segmenterFiles().addAll(files);
	}

	private static String saveSegmenter(SegmentationItem segmentationItem, File directory) {
		String fileName = segmentationItem.getFileName();
		String result;
		if (!segmentationItem.isModified() && fileName != null && fileExists(fileName) &&
			fileIsInDirectory(fileName, directory))
		{
			result = segmentationItem.getFileName();
		}
		else {
			File file = newFileName(directory);
			segmentationItem.saveModel(file.getPath());
			result = file.getPath();
		}
		return result;
	}

	private static void removeObsoleteFiles(Collection<String> updatedFiles,
		Collection<String> oldFiles)
	{
		Set<String> obsoleteFiles = new HashSet<>(oldFiles);
		obsoleteFiles.removeAll(updatedFiles);
		obsoleteFiles.forEach(file -> {
			try {
				Files.deleteIfExists(Paths.get(file));
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	private static boolean fileIsInDirectory(String fileName, File segmentersDirectory) {
		return new File(fileName).getParentFile().equals(segmentersDirectory);
	}

	private static boolean fileExists(String fileName) {
		return new File(fileName).exists();
	}

	private static File newFileName(File directory) {
		for (int i = 1;; i++) {
			File segmenterFile = new File(directory, "segmenter-" + i + ".classifier");
			if (!segmenterFile.exists())
				return segmenterFile;
		}
	}
}
