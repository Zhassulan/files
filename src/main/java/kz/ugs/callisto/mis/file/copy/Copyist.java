package kz.ugs.callisto.mis.file.copy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import kz.ugs.callisto.system.propertyfilemanager.*;

/**
 * Класс для копирования файлов с сетевого ресурса на сервер Callisto
 * @author ZTokbayev
 *
 */
public class Copyist {
	
	private static Logger logger = LogManager.getLogger(Copyist.class);
	
	private static final String pathListFile = PropsManager.getInstance().getProperty("pathListFile");
	private static final String serverFolder = PropsManager.getInstance().getProperty("serverFolder");
	private String curDevice;
	private Date serverLastDate = StringToDate(PropsManager.getInstance().getProperty("serverLastDate"));
	private Date lastFileDate; 
	
	/**
	 * получить список путей к сетевым папкам
	 * @return
	 */
	private List <String> readFilePathList() {
		try (BufferedReader bfRader = new BufferedReader(new InputStreamReader(
                this.getClass().getResourceAsStream("/" + pathListFile)))) {
			return bfRader.lines().collect(Collectors.toList());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
    }  
	
	private Date StringToDate(String dateTime)	{
		DateFormat format = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", new Locale("ru", "RU"));
		Date date;
		try {
			date = format.parse(dateTime);
			return date;
		} catch (ParseException e) {
			logger.error(e.getMessage() + e.getStackTrace());
		}
		return null;
	}
	
	private void copyFiles(String path) {
		Date fileDate;
		File curDir = new File(path);
		File[] filesList = curDir.listFiles();
		for(File f : filesList)	{
			if(f.isDirectory())
				copyFiles(f.getPath());
			if(f.isFile()){
				fileDate = getFileCreatedDate(f.getAbsolutePath());
				logger.info(fileDate);
				//logger.info(f.getAbsolutePath() + " " + getFormattedDate(fileDate));
				logger.info("Comparing " + fileDate + " ? " + serverLastDate);
				if (fileDate.compareTo(serverLastDate) > 0) {
					createDir(serverFolder + curDevice);
					copyFile(f.getAbsolutePath(), serverFolder + curDevice + f.getName());
					lastFileDate = fileDate;
				}
			}
		}
	}
	
	private Date getFileCreatedDate(String path)	{
		Path filePath = Paths.get(path);
		try {
			BasicFileAttributes view = Files.getFileAttributeView(filePath, BasicFileAttributeView.class).readAttributes();
		    FileTime fileTime = view.creationTime();
			return new Date(fileTime.toMillis());
		} catch (IOException  e) {
			logger.error(e.getMessage() + e.getStackTrace());
		}
		return null;
	}
	
	private Date processRemotePath(String dirPath)	{
		Date fileDate;
		File curDir = new File(dirPath);
		File[] filesList = curDir.listFiles();
		for	(File f : filesList)	{
			if	(f.isDirectory())
				copyFiles(f.getPath());
			if	(f.isFile())	{
				fileDate = getFileCreatedDate(f.getAbsolutePath());
				if (fileDate != null)
					if (fileDate.compareTo(serverLastDate) > 0) {
						serverLastDate = fileDate; 
					} 
		    }
		}
		return null;
	}
	
	private void processAllPathList()	{
		List <String> pathList = readFilePathList();
		for (String pathItem : pathList) {
			curDevice = getDeviceCode(pathItem);
			processRemotePath(pathItem);
			//logger.info(getFormattedDate(serverLastDate));
		}
	}
	
	private String getFormattedDate(Date pDate)	{
		return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(pDate.getTime());
	}
	
	private String getDeviceCode(String path)	{
		if (path.equals("//192.168.1.242/Temp/"))
			return "Device1/";
		if (path.equals("//192.168.1.180/Data/"))
			return "Device2/";
		return null;
	}
	
	private static void copyFile(String sourceFilePath, String destFilePath)	{
		File source = new File(sourceFilePath);
		File dest = new File(destFilePath);
		FileChannel inputChannel = null;
		FileChannel outputChannel = null;
		try {
			inputChannel = new FileInputStream(source).getChannel();
			outputChannel = new FileOutputStream(dest).getChannel();
			outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
			logger.info("Copied file " + sourceFilePath + " to " + destFilePath);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
			}
		finally {
			try {
				inputChannel.close();
				outputChannel.close();
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
	}
	
	private void createDir(String dirPath)	{
		Path path = Paths.get(dirPath);
	    if (!Files.exists(path)) {
	        try {
	            Files.createDirectories(path);
	        } catch (IOException e) {
	            logger.error(e.getMessage(), e);
	    }
	    }
	}
	
	private void copyToServer()	{
		
	}
	
	public static void main(String args[])	{
		Copyist c = new Copyist();
		c.processAllPathList();
		PropsManager.getInstance().setProperty("serverLastDate", c.getFormattedDate(c.lastFileDate));
	}
}
