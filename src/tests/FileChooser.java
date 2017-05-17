package tests;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;

import transformerProcessor.TransformerProcessor;
import transformerProcessor.exceptions.InvalidLayerRequirement;
import transformerProcessor.exceptions.TransformationLayerException;
import transformerProcessor.exceptions.TransformationSourceException;

import java.io.File;
import java.io.IOException;

public class FileChooser extends JPanel implements ActionListener {
	final File CURRENT_DIR = new File("/home/kawin/workspace/DSLTrans_lifting");
    JButton openDirButton, openFileButton, submitButton;
    JTextField projectDir, transFile;
    JFileChooser fc, fd;
    static JFrame frame;

    public FileChooser() {
        super(new BorderLayout());

        fd = new JFileChooser();
        fd.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        if(CURRENT_DIR.exists()) {
        	fd.setCurrentDirectory(CURRENT_DIR);
        	fc.setCurrentDirectory(CURRENT_DIR);
        }

        openDirButton = new JButton("Choose project directory...");
        Dimension buttonSize = new Dimension(250, openDirButton.getPreferredSize().height);
        openDirButton.setPreferredSize(buttonSize);
        openDirButton.addActionListener(this);

        projectDir = new JTextField();
        projectDir.setColumns(30);
        projectDir.setPreferredSize(buttonSize);
        
        openFileButton = new JButton("Choose transformation file...");
        openFileButton.setPreferredSize(buttonSize);
        openFileButton.addActionListener(this);
        
        transFile = new JTextField();
        transFile.setColumns(30); 
        transFile.setPreferredSize(buttonSize);
        
        submitButton = new JButton("Transform");
        submitButton.setPreferredSize(buttonSize);
        submitButton.addActionListener(this);
        
        //For layout purposes buttons are in separate panels.
        JPanel dirPanel = new JPanel(); 
        dirPanel.add(openDirButton);
        dirPanel.add(projectDir); 
        
        JPanel filePanel = new JPanel();
        filePanel.add(openFileButton);
        filePanel.add(transFile); 
        
        JPanel submitPanel = new JPanel((LayoutManager) new FlowLayout(FlowLayout.RIGHT));
        submitPanel.add(submitButton); 

        add(dirPanel, BorderLayout.PAGE_START);
        add(filePanel, BorderLayout.CENTER);
        add(submitPanel, BorderLayout.PAGE_END);
   
    }

    /**
     * When some button is clicked (open directory/file or transform).
     */
    public void actionPerformed(ActionEvent e) {
    	// Used to open only directories: specify project directory
        if (e.getSource() == openDirButton) {
        	
            int returnVal = fd.showOpenDialog(FileChooser.this);

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fd.getSelectedFile();
                // Fill adjacent field to show selected project 
                // directory.
                projectDir.setText(file.getAbsolutePath());
            } 
        // Used to specify transformation file: 
        // many can exist within the same project directory
        } else if (e.getSource() == openFileButton) {
        	int returnVal = fc.showOpenDialog(FileChooser.this);
        	
        	if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                // Fill adjacent field to show selected file path.
                transFile.setText(file.getAbsolutePath());
            }
        // Used to specify submit button
        } else if (e.getSource() == submitButton) {
        	// If project directory or transformation file has not
        	// been chosen, flash the field red.
        	if(projectDir.getText().isEmpty()) {
        		flashMyField(projectDir);
        	} else if(transFile.getText().isEmpty()) {
        		flashMyField(transFile);
        	} else {	
        		// Hide the frame.
        		frame.setVisible(false);
        		
        		// Run the transformation.  
        		TransformerProcessor tP = new TransformerProcessor(projectDir.getText());
        		tP.LoadModel(transFile.getText());
        		
        		try {
        			tP.Execute();
        		} catch (InvalidLayerRequirement err) {
        			System.err.println("Error running transformation: " + transFile);
        			err.printStackTrace();
        		} catch (TransformationSourceException err) {
        			System.err.println("Error running transformation: " + transFile);
        			err.printStackTrace();
        		} catch (TransformationLayerException err) {
        			System.err.println("Error running transformation: " + transFile);
        			err.printStackTrace();
        		}
        	}
        }
    }

    /**
     * Create and show GUI.
     */
    private static void createAndShowGUI() {
        //Create and set up the window.
        frame = new JFrame("FileChooserDemo");
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Add content to the window.
        frame.add(new FileChooser());

        //Display the window.
        frame.pack();
        frame.setVisible(true);
    }
    
    /**
     * Flash the specified field red for 1/5 of a second.
     * @param field
     */
    public void flashMyField(final JTextField field) {
      final int delay = 200; // delay between success actions
      // Timer to flash the field red.
	  Timer timer = new javax.swing.Timer(delay, new ActionListener(){
	    boolean flashed = false; // indicates colour has changed

	    public void actionPerformed(ActionEvent evt) {
	      field.setBackground(Color.RED);
	      
	      if(flashed) {
	    	  field.setBackground(Color.WHITE);
	    	  // stop event to prevent repeated flashing
	    	  ((Timer)evt.getSource()).stop(); 
	      }
	      
	      flashed = true;
	    }
	  });
	  
	  timer.setInitialDelay(0);
	  timer.start();
    }

    public static void main(String[] args) {
        //Schedule a job for the event dispatch thread:
        //creating and showing this application's GUI.
    	
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                //Turn off metal's use of bold fonts
                UIManager.put("swing.boldMetal", Boolean.FALSE); 
                createAndShowGUI();
            }
        });
    }
}
