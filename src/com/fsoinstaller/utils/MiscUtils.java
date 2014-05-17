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

package com.fsoinstaller.utils;

import java.awt.Color;
import java.awt.FontMetrics;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.BreakIterator;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.SwingUtilities;

import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;


/**
 * Miscellaneous useful methods.
 * 
 * @author Goober5000
 */
public class MiscUtils
{
	private static final Logger logger = Logger.getLogger(MiscUtils.class);
	
	private static final Pattern SLASH_PATTERN = Pattern.compile("[/\\\\]");
	private static final Pattern LEADING_WHITESPACE_PATTERN = Pattern.compile("^\\s+");
	private static final Pattern INVALID_FILENAME_CHARACTER_PATTERN = Pattern.compile("[^a-zA-Z0-9._]+");
	
	/**
	 * Prevent instantiation.
	 */
	private MiscUtils()
	{
	}
	
	/**
	 * Prints a large number of bytes in human-readable notation (either SI or
	 * decimal).
	 * 
	 * @author m!m
	 */
	public static String humanReadableByteCount(long bytes, boolean si)
	{
		int unit = si ? 1000 : 1024;
		if (bytes < unit)
			return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}
	
	/**
	 * Compares two objects for equality, without danger of a
	 * NullPointerException. Two null objects are assumed to be equal.
	 */
	public static boolean nullSafeEquals(Object o1, Object o2)
	{
		if (o1 == null && o2 == null)
			return true;
		else if (o1 == null || o2 == null)
			return false;
		else
			return o1.equals(o2);
	}
	
	public static File validateApplicationDir(String dirName)
	{
		if (dirName == null || dirName.length() == 0)
			return null;
		
		File file = new File(dirName);
		if (validateApplicationDir(file))
			return file;
		else
			return null;
	}
	
	public static boolean validateApplicationDir(File dir)
	{
		// should be valid
		return true;
	}
	
	/**
	 * Determines the host operating system by examining the Java "os.name"
	 * property.
	 */
	public static OperatingSystem determineOS()
	{
		String os_name_lower = System.getProperty("os.name").toLowerCase();
		for (OperatingSystem os: OperatingSystem.values())
		{
			for (String os_name: os.os_names())
			{
				if (os_name_lower.startsWith(os_name))
					return os;
			}
		}
		return OperatingSystem.OTHER;
	}
	
	/**
	 * Checks whether a mod can be installed on the host operating system. It is
	 * assumed that all mods can be installed on all systems unless the mod
	 * contains an operating system in its name. The host operating system is
	 * obtained by calling <tt>determineOS()</tt>.
	 */
	public static boolean validForOS(String modName)
	{
		OperatingSystem hostOS = determineOS();
		
		// if we have a specific OS, make sure the name doesn't exclude itself
		if (hostOS != OperatingSystem.OTHER)
		{
			for (OperatingSystem os: OperatingSystem.values())
			{
				// exclude all mods which have an match for some other OS
				if (os != hostOS)
				{
					String mod_lower = modName.toLowerCase();
					for (String mod_substring: os.mod_substrings())
					{
						if (mod_lower.contains(mod_substring))
							return false;
					}
				}
			}
		}
		
		return true;
	}
	
	public static ProcessBuilder buildExecCommand(File runDirectory, List<String> commands)
	{
		StringBuilder str = new StringBuilder();
		boolean first = true;
		for (String command: commands)
		{
			if (first)
				first = false;
			else
				str.append(" ");
			str.append(command);
		}
		
		return buildExecCommand(runDirectory, str.toString());
	}
	
	public static ProcessBuilder buildExecCommand(File runDirectory, String command)
	{
		if (!runDirectory.isDirectory())
			throw new IllegalArgumentException("Run directory must exist and be a directory!");
		if (command == null || command.equals(""))
			throw new IllegalArgumentException("Command must not be null or blank!");
		
		String shell = null;
		String param = null;
		
		// determine the shell command
		OperatingSystem os = determineOS();
		switch (os)
		{
			case WINDOWS:
			{
				String os_name_lower = System.getProperty("os.name").toLowerCase();
				if (os_name_lower.contains("windows 95") || os_name_lower.contains("windows 98") || os_name_lower.contains("windows me"))
				{
					logger.debug("Detected Windows 9X/ME; using COMMAND.COM...");
					shell = "command";
					param = "/C";
				}
				else
				{
					logger.debug("Detected Windows; using CMD.EXE...");
					shell = "cmd";
					param = "/C";
				}
				break;
			}
			
			default:
			{
				shell = "/bin/sh";
				param = "-c";
			}
		}
		
		// build the process, but don't start it yet
		logger.info("Command to run: " + command);
		ProcessBuilder builder = new ProcessBuilder(shell, param, command);
		builder.directory(runDirectory);
		
		return builder;
	}
	
	public static int runExecCommand(File runDirectory, String command) throws IOException, InterruptedException
	{
		String loggingPreamble = runDirectory.getAbsolutePath() + File.separator + command;
		
		ProcessBuilder builder = buildExecCommand(runDirectory, command);
		return runProcess(builder, loggingPreamble);
	}
	
	private static final Object execMutex = new Object();
	
	public static int runProcess(ProcessBuilder builder, String loggingPreamble) throws IOException, InterruptedException
	{
		synchronized (execMutex)
		{
			// run the process
			Process process = builder.start();
			
			// pipe the process's output to the appropriate logs
			ReaderLogger stdout = new ReaderLogger(new InputStreamReader(process.getInputStream(), Charset.forName("UTF-8")), Logger.getLogger(MiscUtils.class, "ExternalProcess"), Level.INFO, loggingPreamble);
			ReaderLogger stderr = new ReaderLogger(new InputStreamReader(process.getErrorStream(), Charset.forName("UTF-8")), Logger.getLogger(MiscUtils.class, "ExternalProcess"), Level.SEVERE, loggingPreamble);
			
			// start the loggers
			Thread t1 = new Thread(stdout, "ExternalProcess-stdout");
			Thread t2 = new Thread(stderr, "ExternalProcess-stderr");
			t1.setPriority(Thread.NORM_PRIORITY);
			t2.setPriority(Thread.NORM_PRIORITY);
			t1.start();
			t2.start();
			
			// block until the process completes
			return process.waitFor();
		}
	}
	
	/**
	 * Handy-dandy text wrapping function, adapted from
	 * http://www.geekyramblings.net/2005/06/30/wrap-jlabel-text/.
	 */
	public static String wrapText(String text, FontMetrics metrics, int maxWidth)
	{
		BreakIterator boundary = BreakIterator.getWordInstance();
		boundary.setText(text);
		
		StringBuilder line = new StringBuilder();
		StringBuilder paragraph = new StringBuilder();
		
		// concatenate all words into a new paragraph, adding newlines where appropriate
		for (int start = boundary.first(), end = boundary.next(); end != BreakIterator.DONE; start = end, end = boundary.next())
		{
			String word = text.substring(start, end);
			
			// if we have an actual newline, start a new line, otherwise append the word to the running total
			if (word.equals("\n"))
				line = new StringBuilder();
			else
				line.append(word);
			
			// see if all the words have overrun the line width limit yet
			int lineWidth = SwingUtilities.computeStringWidth(metrics, line.toString());
			if (lineWidth > maxWidth)
			{
				// get rid of any leading whitespace
				word = LEADING_WHITESPACE_PATTERN.matcher(word).replaceFirst("");
				
				// start a new line in both running strings
				line = new StringBuilder(word);
				paragraph.append("\n");
			}
			
			paragraph.append(word);
		}
		
		return paragraph.toString();
	}
	
	/**
	 * Removes invalid file name characters from the candidate file name,
	 * replacing them with underscores.
	 */
	public static String createValidFileName(String fileName)
	{
		if (fileName == null)
			throw new IllegalArgumentException("File name cannot be null!");
		
		// remove any invalid character from the filename.
		return INVALID_FILENAME_CHARACTER_PATTERN.matcher(fileName.trim()).replaceAll("_");
	}
	
	/**
	 * If the specified file path has any spaces in it, surround the whole path
	 * with quotes.
	 */
	public static String maybeQuotePath(String filePath)
	{
		if (filePath.contains(" "))
		{
			StringBuilder str = new StringBuilder("\"");
			str.append(filePath);
			str.append("\"");
			return str.toString();
		}
		else
			return filePath;
	}
	
	/**
	 * Compare FSO Installer version strings to see if the first is greater
	 * than, less than, or equal to the second. Each number in the
	 * MAJOR.MINOR.BUILD sequence is checked, in order.
	 */
	public static int compareVersions(String version1, String version2)
	{
		String[] num1 = version1.trim().split("\\.");
		String[] num2 = version2.trim().split("\\.");
		
		int maxLen = num1.length > num2.length ? num1.length : num2.length;
		
		for (int i = 0; i < maxLen; i++)
		{
			int ver1 = 0;
			if (i < num1.length)
			{
				try
				{
					ver1 = Integer.parseInt(num1[i]);
				}
				catch (NumberFormatException nfe)
				{
					logger.warn("Could not parse version string '" + version1 + "'!", nfe);
				}
			}
			int ver2 = 0;
			if (i < num2.length)
			{
				try
				{
					ver2 = Integer.parseInt(num2[i]);
				}
				catch (NumberFormatException nfe)
				{
					logger.warn("Could not parse version string '" + version2 + "'!", nfe);
				}
			}
			
			if (ver1 > ver2)
				return 1;
			else if (ver1 < ver2)
				return -1;
		}
		
		return 0;
	}
	
	/**
	 * Replaces any forward or backward slashes in the specified path with the
	 * proper path separator character on the host operating system.
	 */
	public static String standardizeSlashes(String filePath)
	{
		return SLASH_PATTERN.matcher(filePath).replaceAll(Matcher.quoteReplacement(File.separator));
	}
	
	private static final ObjectHolder<Boolean> isSevenZipInitialized = new ObjectHolder<Boolean>(false);
	
	/**
	 * Needed because it looks like 7zip should be initialized in a thread-safe
	 * manner.
	 */
	public static void initSevenZip()
	{
		synchronized (isSevenZipInitialized)
		{
			boolean inited = isSevenZipInitialized.get();
			if (inited)
				return;
			isSevenZipInitialized.set(true);
			
			try
			{
				SevenZip.initSevenZipFromPlatformJAR();
				logger.info("7zip initialized successfully!");
			}
			catch (SevenZipNativeInitializationException sznie)
			{
				logger.error("Unable to initialize 7zip!", sznie);
			}
		}
	}
	
	/**
	 * Modifies an existing Java <tt>Color</tt> object, using a series of values
	 * in the HSB color space, to obtain a new Java <tt>Color</tt> object.
	 */
	public static Color deriveColorHSB(Color base, float hFactor, float sFactor, float bFactor)
	{
		float[] hsb = new float[3];
		Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), hsb);
		
		hsb[0] *= hFactor;
		hsb[1] *= sFactor;
		hsb[2] *= bFactor;
		
		return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
	}
	
	/**
	 * Checks whether OpenAL is installed on the host system. This is done by
	 * attempting to load OpenAL using the Java <tt>System.loadLibrary</tt>
	 * call. Both 'OpenAL32' (which is recognized on Windows) and 'openal'
	 * (which is recognized on Linux) are tried.
	 */
	public static boolean loadOpenAL()
	{
		logger.info("Attempting to load OpenAL...");
		
		// tested to work on Windows
		try
		{
			System.loadLibrary("OpenAL32");
			logger.info("Found 'OpenAL32'!");
			return true;
		}
		catch (UnsatisfiedLinkError ule)
		{
			// do nothing
		}
		
		// tested to work on Linux
		try
		{
			System.loadLibrary("openal");
			logger.info("Found 'openal'!");
			return true;
		}
		catch (UnsatisfiedLinkError ule2)
		{
			// do nothing
		}
		
		logger.warn("Neither 'OpenAL32' nor 'openal' could be loaded!");
		return false;
	}
}
