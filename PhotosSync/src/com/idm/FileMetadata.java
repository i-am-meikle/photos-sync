package com.idm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.file.FileMetadataDirectory;

public class FileMetadata {

	final static SimpleDateFormat formatter = new SimpleDateFormat(
			"yyyy" + File.separatorChar + "MM-MMMM" + File.separatorChar + "dd-EEEE");

	private Path path;
	private BasicFileAttributes attributes;
	private Date datetime_original;
	private boolean hidden;
	private Date file_modified_date;

	public FileMetadata(Path aPath, BasicFileAttributes attrs) {

		this.path = aPath;
		this.attributes = attrs;
		this.initialize();

	}

	private void initialize() {

		// Get the Exif attributes
		try {
			File file = new File(path.toString());
			// Skip hidden files
			if (!(this.hidden = file.isHidden())) {
				Metadata metadata = ImageMetadataReader.readMetadata(file);
				Directory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
				if (directory == null) {
					this.logWarning("ExifSubIFDDirectory missing: %s%n", path.toString());
				} else {
					datetime_original = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
					if (datetime_original == null) {
						this.logWarning("ExifSubIFDDirectory-TAG_DATETIME_ORIGINAL missing: %s%n", path.toString());
					}
				}
				// if we have datetime_original, always use it. 
				if (datetime_original==null){
					directory = metadata.getFirstDirectoryOfType(FileMetadataDirectory.class);
					if (directory == null) {
						this.logWarning("FileMetadataDirectory missing: %s%n", path.toString());
					} else {
						file_modified_date = directory.getDate(FileMetadataDirectory.TAG_FILE_MODIFIED_DATE);
						if (file_modified_date == null) {
							this.logWarning("ExifSubIFDDirectory-TAG_FILE_MODIFIED_DATE missing: %s%n", path.toString());
						}
					}
				}
				
			}
		} catch (ImageProcessingException | IOException e) {
			this.logError("Error parsing file: %s - %s%n", path.toString(), e);
		}

	}

	private void logError(String message, Object... args) {

		System.err.format(message, args);

	}

	private void logWarning(String message, Object... args) {

//		System.out.format(message, args);

	}
	
	public String targetFilePath() {

		// For the target file path, the following rule is applied:
		// 1) datetime_original
		// 2) file_modified_date
		// 3) file creation file
		
		Date date = this.datetime_original();

		if (date == null) {
			date = this.file_modified_date();
		}

		if (date == null) {
			date = this.fileCreationTime();
		}
		
		// Is the creation time sensible ??
		// If the creation time is prior to 2000, then assume it is not sensible. 
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy");
		try {
			if (date.before((simpleDateFormat.parse("01-01-2000")))) {
				// Non sensible date, therefore use the inner most folder name from the source directory.
				return path.getName(path.getNameCount()-2).toString();
				
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		return formatter.format(date);
	}

	private Date file_modified_date() {

		return file_modified_date;
	}

	public Date datetime_original() {

			return datetime_original;
	}

	public Date fileCreationTime() {
		
		FileTime creationTime = attributes.creationTime();
		Date creationDate = new Date(creationTime.toMillis());
		return creationDate;
	}
	
	public boolean hasDatetime_original() {
		return datetime_original != null;
	}

	public long fileSize() {
		return attributes.size();
	}

	public boolean isHidden() {
		return this.hidden;
	}

}

