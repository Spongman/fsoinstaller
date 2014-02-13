/*
 * This file is part of the FreeSpace Open Installer
 * Copyright (C) 2010 The FreeSpace 2 Source Code Project
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */

package com.fsoinstaller.wizard;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.fsoinstaller.common.InstallerNode;
import com.fsoinstaller.common.InstallerNodeParseException;
import com.fsoinstaller.internet.Connector;
import com.fsoinstaller.internet.Downloader;
import com.fsoinstaller.internet.InvalidProxyException;
import com.fsoinstaller.main.Configuration;
import com.fsoinstaller.main.FreeSpaceOpenInstaller;
import com.fsoinstaller.utils.IOUtils;
import com.fsoinstaller.utils.Logger;
import com.fsoinstaller.utils.MiscUtils;
import com.fsoinstaller.utils.ProgressBarDialog;
import com.fsoinstaller.utils.ThreadSafeJOptionPane;
import com.l2fprod.common.swing.JDirectoryChooser;


public class ConfigPage extends WizardPage
{
	private static final Logger logger = Logger.getLogger(ConfigPage.class);
	
	private final JTextField directoryField;
	private final JTextField hostField;
	private final JTextField portField;
	private boolean usingProxy;
	
	public ConfigPage()
	{
		super("config");
		
		// load initial directory
		String dirText = configuration.getDefaultDir();
		File dir = configuration.getApplicationDir();
		if (dir != null)
		{
			try
			{
				dirText = dir.getCanonicalPath();
			}
			catch (IOException ioe)
			{
				logger.warn("Could not get canonical path of destination directory", ioe);
				dirText = dir.getAbsolutePath();
			}
		}
		
		// load initial proxy settings
		String host = configuration.getProxyHost();
		int port = configuration.getProxyPort();
		usingProxy = (host != null && port >= 0);
		
		// create widgets
		directoryField = new JTextField(dirText);
		hostField = new JTextField(usingProxy ? host : "none");
		portField = new JTextField(usingProxy ? Integer.toString(port) : "none");
		hostField.setEnabled(usingProxy);
		portField.setEnabled(usingProxy);
		
		// they shouldn't change vertical size
		directoryField.setMaximumSize(new Dimension((int) directoryField.getMaximumSize().getWidth(), (int) directoryField.getPreferredSize().getHeight()));
		hostField.setMaximumSize(new Dimension((int) hostField.getMaximumSize().getWidth(), (int) hostField.getPreferredSize().getHeight()));
		portField.setMaximumSize(new Dimension((int) portField.getMaximumSize().getWidth(), (int) portField.getPreferredSize().getHeight()));
	}
	
	@Override
	public JPanel createCenterPanel()
	{
		JLabel dummy = new JLabel();
		
		// bleh, for multiline we need a JTextArea, but we want it to look like a JLabel
		JTextArea text = new JTextArea("Choose the directory where you would like to install FreeSpace Open and associated mods.  If your network requires the use of a proxy, you can also specify that here.");
		text.setEditable(false);
		text.setRows(4);
		text.setOpaque(false);
		text.setHighlighter(null);
		text.setFont(dummy.getFont());
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		
		JPanel dirPanel = new JPanel();
		dirPanel.setBorder(BorderFactory.createEmptyBorder(GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN));
		dirPanel.setLayout(new BoxLayout(dirPanel, BoxLayout.X_AXIS));
		dirPanel.add(directoryField);
		dirPanel.add(Box.createHorizontalStrut(GUIConstants.DEFAULT_MARGIN));
		dirPanel.add(new JButton(new BrowseAction()));
		
		JPanel outerDirPanel = new JPanel();
		outerDirPanel.setBorder(BorderFactory.createTitledBorder("Installation Directory"));
		outerDirPanel.setLayout(new BoxLayout(outerDirPanel, BoxLayout.Y_AXIS));
		outerDirPanel.add(dirPanel);
		
		JCheckBox check = new JCheckBox(new ProxyCheckAction());
		check.setSelected(usingProxy);
		JLabel hostLabel = new JLabel("Proxy host:");
		JLabel portLabel = new JLabel("Proxy port:");
		int m_width = (int) Math.max(hostLabel.getMinimumSize().getWidth(), portLabel.getMinimumSize().getWidth());
		int p_width = (int) Math.max(hostLabel.getPreferredSize().getWidth(), portLabel.getPreferredSize().getWidth());
		hostLabel.setMinimumSize(new Dimension(m_width, (int) hostLabel.getMinimumSize().getHeight()));
		portLabel.setMinimumSize(new Dimension(m_width, (int) portLabel.getMinimumSize().getHeight()));
		hostLabel.setPreferredSize(new Dimension(p_width, (int) hostLabel.getPreferredSize().getHeight()));
		portLabel.setPreferredSize(new Dimension(p_width, (int) portLabel.getPreferredSize().getHeight()));
		
		JPanel checkPanel = new JPanel();
		checkPanel.setLayout(new BoxLayout(checkPanel, BoxLayout.X_AXIS));
		checkPanel.add(check);
		checkPanel.add(Box.createHorizontalGlue());
		
		JPanel hostPanel = new JPanel();
		hostPanel.setLayout(new BoxLayout(hostPanel, BoxLayout.X_AXIS));
		hostPanel.add(Box.createHorizontalStrut(GUIConstants.DEFAULT_MARGIN * 3));
		hostPanel.add(hostLabel);
		hostPanel.add(Box.createHorizontalStrut(GUIConstants.DEFAULT_MARGIN));
		hostPanel.add(hostField);
		
		JPanel portPanel = new JPanel();
		portPanel.setLayout(new BoxLayout(portPanel, BoxLayout.X_AXIS));
		portPanel.add(Box.createHorizontalStrut(GUIConstants.DEFAULT_MARGIN * 3));
		portPanel.add(portLabel);
		portPanel.add(Box.createHorizontalStrut(GUIConstants.DEFAULT_MARGIN));
		portPanel.add(portField);
		
		JPanel proxyPanel = new JPanel();
		proxyPanel.setBorder(BorderFactory.createEmptyBorder(0, GUIConstants.SMALL_MARGIN, GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN));
		proxyPanel.setLayout(new BoxLayout(proxyPanel, BoxLayout.Y_AXIS));
		proxyPanel.add(checkPanel);
		proxyPanel.add(Box.createVerticalStrut(GUIConstants.SMALL_MARGIN));
		proxyPanel.add(hostPanel);
		proxyPanel.add(Box.createVerticalStrut(GUIConstants.SMALL_MARGIN));
		proxyPanel.add(portPanel);
		
		JPanel outerProxyPanel = new JPanel();
		outerProxyPanel.setBorder(BorderFactory.createTitledBorder("Proxy Settings"));
		outerProxyPanel.setLayout(new BoxLayout(outerProxyPanel, BoxLayout.Y_AXIS));
		outerProxyPanel.add(proxyPanel);
		
		JPanel panel = new JPanel();
		panel.setBorder(BorderFactory.createEmptyBorder(GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN, GUIConstants.DEFAULT_MARGIN));
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(text);
		panel.add(Box.createVerticalStrut(GUIConstants.DEFAULT_MARGIN * 2));
		panel.add(outerDirPanel);
		panel.add(Box.createVerticalStrut(GUIConstants.DEFAULT_MARGIN * 2));
		panel.add(outerProxyPanel);
		panel.add(Box.createVerticalStrut(GUIConstants.DEFAULT_MARGIN * 2));
		panel.add(Box.createVerticalGlue());
		
		return panel;
	}
	
	@Override
	public void prepareForDisplay()
	{
		backButton.setVisible(false);
	}
	
	@Override
	public void prepareToLeavePage(final Runnable runWhenReady)
	{
		// what happens when we're finished validating
		Runnable toRunNext = runWhenReady;
		
		// what happens if we cancel
		final Runnable exitRunnable = new Runnable()
		{
			public void run()
			{
				JFrame frame = gui;
				logger.debug("Disposing active JFrame '" + frame.getName() + "'...");
				frame.dispose();
			}
		};
		
		// exception callback
		final ProgressBarDialog.AbnormalTerminationCallback callback = new ProgressBarDialog.AbnormalTerminationCallback()
		{
			public void handleCancellation()
			{
				ThreadSafeJOptionPane.showMessageDialog(gui, "Validation was cancelled!", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.WARNING_MESSAGE);
			}
			
			public void handleException(Exception exception)
			{
				if (exception instanceof SecurityException)
				{
					ThreadSafeJOptionPane.showMessageDialog(gui, "The Java security manager is prohibiting the installer from making any changes to the file system.  You will need to change the permissions in the Java control panel before the installer will be able to run successfully.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
					exitRunnable.run();
				}
				else if (exception instanceof InterruptedException)
				{
					ThreadSafeJOptionPane.showMessageDialog(gui, "Validation was interrupted.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.WARNING_MESSAGE);
					return;
				}
				else
				{
					ThreadSafeJOptionPane.showMessageDialog(gui, "An unexpected runtime exception occurred.  Please visit Hard Light Productions for technical support.  Make sure you provide the log file.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
					exitRunnable.run();
				}
			}
		};
		
		// this is okay because the checked directories are only ever accessed from the event dispatching thread
		Map<String, Object> settings = configuration.getSettings();
		@SuppressWarnings("unchecked")
		Set<String> checked = (Set<String>) settings.get(Configuration.CHECKED_DIRECTORIES_KEY);
		if (checked == null)
		{
			checked = new HashSet<String>();
			settings.put(Configuration.CHECKED_DIRECTORIES_KEY, checked);
		}
		
		// don't do directory manipulation if we don't need to
		if (!checked.contains(directoryField.getText()))
		{
			// we need to insert this task between validation and proceeding to the next page
			toRunNext = new Runnable()
			{
				public void run()
				{
					Callable<Void> gog = new DirectoryTask(gui, directoryField.getText(), runWhenReady, exitRunnable);
					ProgressBarDialog dialog = new ProgressBarDialog(gui, "Checking the installation directory...");
					dialog.runTask(gog, callback);
				}
			};
		}
		
		Callable<Void> validation = new SuperValidationTask(gui, directoryField.getText(), usingProxy, hostField.getText(), portField.getText(), toRunNext, exitRunnable);
		ProgressBarDialog dialog = new ProgressBarDialog(gui, "Setting up the installer...");
		dialog.runTask(validation, callback);
	}
	
	private final class BrowseAction extends AbstractAction
	{
		public BrowseAction()
		{
			putValue(Action.NAME, "Browse...");
			putValue(Action.SHORT_DESCRIPTION, "Click to choose an installation directory");
		}
		
		public void actionPerformed(ActionEvent e)
		{
			File dir = MiscUtils.validateApplicationDir(directoryField.getText());
			
			// create a file chooser
			JDirectoryChooser chooser = new JDirectoryChooser();
			chooser.setCurrentDirectory(dir);
			chooser.setDialogTitle("Choose a directory");
			chooser.setShowingCreateDirectory(false);
			
			// display it
			int result = chooser.showDialog(gui, "OK");
			if (result == JDirectoryChooser.APPROVE_OPTION)
				directoryField.setText(chooser.getSelectedFile().getAbsolutePath());
		}
	}
	
	private final class ProxyCheckAction extends AbstractAction
	{
		public ProxyCheckAction()
		{
			putValue(Action.NAME, "Use proxy");
		}
		
		public void actionPerformed(ActionEvent e)
		{
			usingProxy = !usingProxy;
			
			hostField.setEnabled(usingProxy);
			portField.setEnabled(usingProxy);
		}
	}
	
	private static final class SuperValidationTask implements Callable<Void>
	{
		private final JFrame activeFrame;
		private final String directoryText;
		private final boolean usingProxy;
		private final String hostText;
		private final String portText;
		private final Runnable runWhenReady;
		private final Runnable exitRunnable;
		
		private final Configuration configuration;
		private final Map<String, Object> settings;
		
		public SuperValidationTask(JFrame activeFrame, String directoryText, boolean usingProxy, String hostText, String portText, Runnable runWhenReady, Runnable exitRunnable)
		{
			this.activeFrame = activeFrame;
			this.directoryText = directoryText;
			this.usingProxy = usingProxy;
			this.hostText = hostText;
			this.portText = portText;
			this.runWhenReady = runWhenReady;
			this.exitRunnable = exitRunnable;
			
			// Configuration and its maps are thread-safe
			this.configuration = Configuration.getInstance();
			this.settings = configuration.getSettings();
		}
		
		/**
		 * Cleans up the validation task if there is an interrupt in the first
		 * phase.
		 */
		private Void cleanupPhaseA()
		{
			logger.info("Rolling back Phase A validation");
			
			settings.remove(Configuration.MOD_URLS_KEY);
			settings.remove(Configuration.REMOTE_VERSION_KEY);
			
			return null;
		}
		
		/**
		 * Cleans up the validation task if there is an interrupt in the second
		 * phase.
		 */
		private Void cleanupPhaseB()
		{
			logger.info("Rolling back Phase B validation");
			
			settings.remove(Configuration.MOD_NODES_KEY);
			
			return null;
		}
		
		public Void call()
		{
			logger.info("Validating user input...");
			
			// check directory
			File destinationDir = MiscUtils.validateApplicationDir(directoryText);
			if (destinationDir == null)
			{
				ThreadSafeJOptionPane.showMessageDialog(activeFrame, "The destination directory is not valid.  Please select another directory.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.WARNING_MESSAGE);
				return null;
			}
			
			// create directory that doesn't exist
			if (!destinationDir.exists())
			{
				// prompt to create it
				int result = ThreadSafeJOptionPane.showConfirmDialog(activeFrame, "The destination directory does not exist.  Do you want to create it?", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.YES_NO_OPTION);
				if (result != JOptionPane.YES_OPTION)
					return null;
				
				logger.info("Attempting to create directory/ies...");
				
				// attempt to create it
				if (!destinationDir.mkdirs())
				{
					ThreadSafeJOptionPane.showMessageDialog(activeFrame, "Could not create the destination directory.  Please select another directory.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
					return null;
				}
				
				logger.info("Directory creation successful.");
			}
			
			// check proxy
			Proxy proxy = null;
			String host = null;
			int port = -1;
			if (usingProxy)
			{
				logger.info("Checking proxy...");
				
				try
				{
					host = hostText;
					port = Integer.parseInt(portText);
					proxy = Connector.createProxy(host, port);
				}
				catch (NumberFormatException nfe)
				{
					ThreadSafeJOptionPane.showMessageDialog(activeFrame, "The proxy port could not be parsed as an integer.  Please enter a correct proxy port.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.WARNING_MESSAGE);
					return null;
				}
				catch (InvalidProxyException ipe)
				{
					logger.error("Proxy could not be created!", ipe);
					ThreadSafeJOptionPane.showMessageDialog(activeFrame, "This proxy appears to be invalid!  Check that you have entered the host and port correctly.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
					return null;
				}
				
				// good to go
				settings.put(Configuration.PROXY_KEY, proxy);
			}
			
			logger.info("Validation succeeded!");
			
			// save our settings
			configuration.setApplicationDir(destinationDir);
			configuration.setProxyInfo(host, port);
			configuration.saveUserProperties();
			
			Connector connector = new Connector(proxy);
			settings.put(Configuration.CONNECTOR_KEY, connector);
			
			// only check for the installer version if we haven't checked already
			if (!settings.containsKey(Configuration.REMOTE_VERSION_KEY))
			{
				logger.info("Checking installer version...");
				logger.info("This version is " + FreeSpaceOpenInstaller.INSTALLER_VERSION);
				
				File tempVersion;
				File tempFilenames;
				File tempBasicConfig;
				try
				{
					tempVersion = File.createTempFile("fsoinstaller_version", null);
					tempFilenames = File.createTempFile("fsoinstaller_filenames", null);
					tempBasicConfig = File.createTempFile("fsoinstaller_basicconfig", null);
				}
				catch (IOException ioe)
				{
					logger.error("Error creating temporary file!", ioe);
					ThreadSafeJOptionPane.showMessageDialog(activeFrame, "There was an error creating a temporary file!  This application may need elevated privileges to run.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
					return null;
				}
				tempVersion.deleteOnExit();
				tempFilenames.deleteOnExit();
				tempBasicConfig.deleteOnExit();
				
				String maxVersion = "0.0.0.0";
				String maxVersionURL = null;
				
				// check all URLs for version and filename info
				for (String url: FreeSpaceOpenInstaller.INSTALLER_HOME_URLs)
				{
					logger.debug("Accessing version info from " + url + "...");
					
					// assemble URLs
					URL versionURL;
					URL filenameURL;
					URL basicURL;
					try
					{
						versionURL = new URL(url + "version.txt");
						filenameURL = new URL(url + "filenames.txt");
						basicURL = new URL(url + "basic_config.txt");
					}
					catch (MalformedURLException murle)
					{
						logger.error("Something went wrong with the URL!", murle);
						continue;
					}
					
					// download version information
					Downloader tempVersionDownloader = new Downloader(connector, versionURL, tempVersion);
					if (tempVersionDownloader.download())
					{
						List<String> versionLines = IOUtils.readTextFileCleanly(tempVersion);
						if (!versionLines.isEmpty())
						{
							String thisVersion = versionLines.get(0);
							logger.info("Version at this URL is " + thisVersion);
							
							// get the information from the highest version available
							if (MiscUtils.compareVersions(thisVersion, maxVersion) > 0)
							{
								// get file names
								Downloader tempFilenamesDownloader = new Downloader(connector, filenameURL, tempFilenames);
								if (tempFilenamesDownloader.download())
								{
									List<String> filenameLines = IOUtils.readTextFileCleanly(tempFilenames);
									if (!filenameLines.isEmpty())
									{
										maxVersion = thisVersion;
										maxVersionURL = versionLines.get(1);
										
										// try to get basic configuration too, but this can be optional (sort of)
										Downloader tempBasicConfigDownloader = new Downloader(connector, basicURL, tempBasicConfig);
										if (tempBasicConfigDownloader.download())
										{
											List<String> basicLines = IOUtils.readTextFileCleanly(tempBasicConfig);
											
											// strip empty/blank lines
											Iterator<String> ii = basicLines.iterator();
											while (ii.hasNext())
												if (ii.next().trim().length() == 0)
													ii.remove();
											
											if (!basicLines.isEmpty())
												settings.put(Configuration.BASIC_CONFIG_MODS_KEY, basicLines);
										}
										else if (Thread.currentThread().isInterrupted())
											return cleanupPhaseA();
										
										// save our settings... save REMOTE_VERSION_KEY last because it is tested in the if() blocks
										settings.put(Configuration.MOD_URLS_KEY, filenameLines);
										settings.put(Configuration.REMOTE_VERSION_KEY, thisVersion);
									}
								}
								else if (Thread.currentThread().isInterrupted())
									return cleanupPhaseA();
							}
						}
					}
					else if (Thread.currentThread().isInterrupted())
						return cleanupPhaseA();
				}
				
				// make sure we could access version information
				if (!settings.containsKey(Configuration.REMOTE_VERSION_KEY))
				{
					ThreadSafeJOptionPane.showMessageDialog(activeFrame, "There was a problem accessing the remote sites.  Check your network connection and try again.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.WARNING_MESSAGE);
					return null;
				}
				
				// we have a version; check if it is more recent than what we're running
				// (this prompt should only ever come up once, because once the version is known, future visits to this page will take the early exit above)
				if (MiscUtils.compareVersions(maxVersion, FreeSpaceOpenInstaller.INSTALLER_VERSION) > 0)
				{
					logger.info("Installer is out-of-date; prompting user to download new version...");
					int result = ThreadSafeJOptionPane.showConfirmDialog(activeFrame, "This version of the installer is out-of-date.  Would you like to bring up the download page for the most recent version?\n\n(If you click Yes, the program will exit.)", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.YES_NO_OPTION);
					if (result == JOptionPane.YES_OPTION)
					{
						try
						{
							if (connector.browseToURL(new URL(maxVersionURL)))
							{
								// this should close the program
								EventQueue.invokeLater(exitRunnable);
								return null;
							}
						}
						catch (MalformedURLException murle)
						{
							logger.error("Something went wrong with the URL!", murle);
						}
						ThreadSafeJOptionPane.showMessageDialog(activeFrame, "There was a problem bringing up the download link.  Try re-downloading the installer using your favorite Internet browser.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
						return null;
					}
				}
			}
			
			// check again if thread is interrupted
			if (Thread.currentThread().isInterrupted())
				return cleanupPhaseA();
			
			// only check for mod information if we haven't checked already
			if (!settings.containsKey(Configuration.MOD_NODES_KEY))
			{
				logger.info("Downloading mod information...");
				
				@SuppressWarnings("unchecked")
				List<String> urls = (List<String>) settings.get(Configuration.MOD_URLS_KEY);
				if (urls == null || urls.isEmpty())
				{
					ThreadSafeJOptionPane.showMessageDialog(activeFrame, "For some reason, there are no mods available for download.  This is not an error with the network, but rather with the remote mod repositories.\n\nThis shouldn't ever happen, and we're rather perplexed that you're seeing this right now.  We can only suggest that you try again later.\n\nClick OK to exit.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
					EventQueue.invokeLater(exitRunnable);
					return null;
				}
				
				// parse mod urls into nodes
				List<InstallerNode> modNodes = new ArrayList<InstallerNode>();
				for (String url: urls)
				{
					// create a URL
					URL modURL;
					try
					{
						modURL = new URL(url);
					}
					catch (MalformedURLException murle)
					{
						logger.error("Something went wrong with the URL!", murle);
						continue;
					}
					
					// create a temporary file
					File tempModFile;
					try
					{
						tempModFile = File.createTempFile("fsoinstaller_mod", null);
					}
					catch (IOException ioe)
					{
						logger.error("Error creating temporary file!", ioe);
						ThreadSafeJOptionPane.showMessageDialog(activeFrame, "There was an error creating a temporary file!  This application may need elevated privileges to run.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
						return null;
					}
					tempModFile.deleteOnExit();
					
					// download it to the temp file
					Downloader tempModFileDownloader = new Downloader(connector, modURL, tempModFile);
					if (!tempModFileDownloader.download())
					{
						if (Thread.currentThread().isInterrupted())
							return cleanupPhaseB();
						
						logger.warn("Could not download mod information from '" + url + "'");
						continue;
					}
					
					// parse it into one or more nodes
					try
					{
						List<InstallerNode> nodes = IOUtils.readInstallFile(tempModFile);
						for (InstallerNode node: nodes)
						{
							modNodes.add(node);
							logger.info("Successfully added " + node.getName());
						}
					}
					catch (FileNotFoundException fnfe)
					{
						logger.error("This is very odd; we can't find the temp file we just created!", fnfe);
					}
					catch (IOException ioe)
					{
						logger.error("This is very odd; there was an error reading the temp file we just created!", ioe);
					}
					catch (InstallerNodeParseException inpe)
					{
						logger.warn("There was an error parsing the mod file at '" + url + "'", inpe);
					}
				}
				
				// check that we have mods
				if (modNodes.isEmpty())
				{
					ThreadSafeJOptionPane.showMessageDialog(activeFrame, "For some reason, there are no mods available for download.  This is not an error with the network, but rather with the remote mod repositories.\n\nThis shouldn't ever happen, and we're rather perplexed that you're seeing this right now.  We can only suggest that you try again later.\n\nClick OK to exit.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
					EventQueue.invokeLater(exitRunnable);
					return null;
				}
				
				// add to settings
				settings.put(Configuration.MOD_NODES_KEY, modNodes);
			}
			
			// check again if thread is interrupted
			if (Thread.currentThread().isInterrupted())
				return cleanupPhaseB();
			
			// now that we have the mod list, check to see if there is any old version information left over from Turey's installer
			logger.info("Checking for legacy version information...");
			
			// we check this every time because the user could have changed the destination directory
			// (however, the installed versions file should be deleted after the first try, or at least the properties file should contain the keys we need)
			File oldInstallerInfoDir = new File(destinationDir, "temp");
			if (oldInstallerInfoDir.exists() && oldInstallerInfoDir.isDirectory())
			{
				File installedversions = new File(oldInstallerInfoDir, "installedversions.txt");
				if (installedversions.exists())
				{
					// read lines from the installedversions file
					List<String> lines = IOUtils.readTextFileCleanly(installedversions);
					
					// load the version for each node
					@SuppressWarnings("unchecked")
					List<InstallerNode> modNodes = (List<InstallerNode>) settings.get(Configuration.MOD_NODES_KEY);
					for (InstallerNode node: modNodes)
						loadLegacyModVersions(node, lines, configuration.getUserProperties());
					
					// save our properties
					boolean success = configuration.saveUserProperties();
					
					// delete the file, since we don't need it any more
					if (success)
						installedversions.delete();
				}
				
				// delete other old files and the folder
				File latest = new File(oldInstallerInfoDir, "latest.txt");
				if (latest.exists())
					latest.delete();
				File version = new File(oldInstallerInfoDir, "version.txt");
				if (version.exists())
					version.delete();
				File[] filesLeft = oldInstallerInfoDir.listFiles();
				if (filesLeft != null && filesLeft.length == 0)
					oldInstallerInfoDir.delete();
			}
			
			// final interruption check for this task
			if (Thread.currentThread().isInterrupted())
				return null;
			
			// validation completed!
			logger.info("Done with SuperValidationTask!");
			EventQueue.invokeLater(runWhenReady);
			return null;
		}
		
		private void loadLegacyModVersions(InstallerNode node, List<String> installedversions_lines, Properties properties)
		{
			String propertyName = node.buildTreeName();
			
			// if we have a version already, we don't need to query the legacy version
			logger.debug(propertyName);
			if (properties.containsKey(propertyName))
				return;
			
			// find the version corresponding to this node in the installedversions file
			String version = null;
			Iterator<String> ii = installedversions_lines.iterator();
			while (ii.hasNext())
			{
				// get name matching this node
				if (!ii.next().equalsIgnoreCase("NAME"))
					continue;
				if (!ii.hasNext())
					break;
				if (!ii.next().equals(node.getName()))
					continue;
				// ensure name hasn't provided a version
				if (version != null)
				{
					logger.warn("The installedversions file contains more than one version for the name '" + node.getName() + "'!");
					return;
				}
				
				// get version
				if (!ii.hasNext())
					break;
				if (!ii.next().equalsIgnoreCase("VERSION"))
					continue;
				if (!ii.hasNext())
					break;
				version = ii.next();
			}
			
			// now that we have a version, save it
			if (version != null)
				properties.setProperty(propertyName, version);
			
			// we need to check all the child nodes as well
			for (InstallerNode child: node.getChildren())
				loadLegacyModVersions(child, installedversions_lines, properties);
		}
	}
	
	private static final class DirectoryTask implements Callable<Void>
	{
		private final JFrame activeFrame;
		private final String directoryText;
		private final Runnable runWhenReady;
		@SuppressWarnings("unused")
		private final Runnable exitRunnable;
		
		private final Configuration configuration;
		private final Map<String, Object> settings;
		
		public DirectoryTask(JFrame activeFrame, String directoryText, Runnable runWhenReady, Runnable exitRunnable)
		{
			this.activeFrame = activeFrame;
			this.directoryText = directoryText;
			this.runWhenReady = runWhenReady;
			this.exitRunnable = exitRunnable;
			
			// Configuration and its maps are thread-safe
			this.configuration = Configuration.getInstance();
			this.settings = configuration.getSettings();
		}
		
		public Void call()
		{
			File destinationDir = configuration.getApplicationDir();
			
			logger.info("Checking for read access...");
			
			// check that we can read from this directory: contents will be null if an I/O error occurred
			File[] contents = destinationDir.listFiles();
			if (contents == null)
			{
				ThreadSafeJOptionPane.showMessageDialog(activeFrame, "The installer could not read from the destination directory.  Please ensure that the directory is readable, or visit Hard Light Productions for technical support.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
				return null;
			}
			
			logger.info("Checking for write and delete access...");
			
			// check that we can write to this directory
			String unique = "installer_" + UUID.randomUUID().toString().replaceAll("-", "") + ".tmp";
			File writingTest = new File(destinationDir, unique);
			try
			{
				writingTest.createNewFile();
			}
			catch (IOException ioe)
			{
				logger.error("Creating a temporary file '" + unique + "' failed", ioe);
				ThreadSafeJOptionPane.showMessageDialog(activeFrame, "The installer could not create a temporary file in the destination directory.  Please ensure that the directory is writable, or visit Hard Light Productions for technical support.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
				return null;
			}
			if (!writingTest.delete())
			{
				logger.error("Deleting a temporary file '" + unique + "' failed");
				ThreadSafeJOptionPane.showMessageDialog(activeFrame, "The installer could not delete a temporary file in the destination directory.  Please ensure that the directory is not read-only, or visit Hard Light Productions for technical support.", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.ERROR_MESSAGE);
				return null;
			}
			
			// if we need FS2 installed, make sure that it is (or that user has been warned)
			if (configuration.requiresFS2())
			{
				logger.info("Checking for root_fs2.vp...");
				
				// the best way to do this is probably to check for the presence of root_fs2
				boolean exists = false;
				for (File file: contents)
				{
					if (file.isDirectory())
						continue;
					
					String name = file.getName();
					if (name.equalsIgnoreCase("root_fs2.vp"))
					{
						exists = true;
						break;
					}
				}
				
				if (!exists)
				{
					// prompt to continue
					int result = ThreadSafeJOptionPane.showConfirmDialog(activeFrame, "The destination directory does not appear to contain a retail installation of FreeSpace 2.  FreeSpace 2 is required to run FreeSpace Open as well as any mods you download.\n\nDo you want to continue anyway?", FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.YES_NO_OPTION);
					if (result != JOptionPane.YES_OPTION)
						return null;
				}
			}
			
			logger.info("Checking for extra VPs in the directory");
			
			// check for spurious VPs
			// (note: allowed VPs are in lowercase)
			List<String> allowedVPs = configuration.getAllowedVPs();
			List<String> extraVPs = new ArrayList<String>();
			for (File file: contents)
			{
				if (file.isDirectory())
					continue;
				
				String name = file.getName();
				if (!name.endsWith(".vp"))
					continue;
				
				if (!allowedVPs.contains(name.toLowerCase()))
					extraVPs.add(name);
			}
			
			if (!extraVPs.isEmpty())
			{
				StringBuilder message = new StringBuilder("The destination directory contains several extra VPs beyond the standard ones that should be there:\n\n");
				for (String name: extraVPs)
				{
					message.append(name);
					message.append("\n");
				}
				message.append("\nThese are likely to cause problems, and you are encouraged to move or delete them before running the game.  Do you want to continue with the installation?");
				
				// prompt to continue
				int result = ThreadSafeJOptionPane.showConfirmDialog(activeFrame, message, FreeSpaceOpenInstaller.INSTALLER_TITLE, JOptionPane.YES_NO_OPTION);
				if (result != JOptionPane.YES_OPTION)
					return null;
			}
			
			// TODO: if there are MVE files in data2 and data3 folders, offer to copy them to data/movies
			// (this is where the GOG installer puts them)
			
			// final interruption check for this task
			if (Thread.currentThread().isInterrupted())
				return null;
			
			// directory is good to go
			EventQueue.invokeLater(new Runnable()
			{
				public void run()
				{
					@SuppressWarnings("unchecked")
					Set<String> checked = (Set<String>) settings.get(Configuration.CHECKED_DIRECTORIES_KEY);
					checked.add(directoryText);
				}
			});
			
			// checking completed!
			logger.info("Done with DirectoryTask!");
			EventQueue.invokeLater(runWhenReady);
			return null;
		}
	}
}
